package com.tencent.shrinker

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.TransformInput
import com.google.common.collect.Maps

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.Collections
import java.util.LinkedList
import java.util.stream.Collectors
import java.util.stream.Stream

import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES

/**
 * @author yrom
 */
class RSymbols() {

    companion object {
        /**
         * default package!
         */
        const val R_STYLEABLES_CLASS_NAME = "R\$styleable"


        /**
         * 从inputs中解析出所有的R$xxx类
         */
        fun collectAllRFiles(inputs: Collection<TransformInput>): RSymbols {
            val symbols = RSymbols()

            val paths = inputs.stream()
                    // 所有依赖的包都会在主工程下生成一个对应的R文件
                    .map {
                        it.directoryInputs
                    }
                    //
                    .flatMap<DirectoryInput> {
                         it.stream()
                    }
                    .map<Stream<Path>> {
                        toStream(it)
                    }
                    .reduce(Stream.empty()) { stream, stream1 ->
                        Stream.concat(stream, stream1)
                    }
                    .collect(Collectors.toList())


            val stream: Stream<Path>
            if (paths.size >= Runtime.getRuntime().availableProcessors() * 3) {
                // use parallel here!

                stream = paths.parallelStream()
                symbols.symbols = Maps.newConcurrentMap()
            } else {
                stream = paths.stream()
                symbols.symbols = Maps.newHashMap()
            }

            // 找到所有R相关的类
            val rClassMatcher = FileSystems.getDefault().getPathMatcher("glob:R$*.class")
            stream.filter {
                rClassMatcher.matches(it.fileName)
            } .forEach {
                drainRSymbolsFromRFile(it, symbols)
            }

            return symbols
        }


        private fun drainRSymbolsFromRFile(file: Path, symbols: RSymbols) {

            val filename = file.fileName.toString()
            val typeName = filename.substring(0, filename.length - ".class".length)
            val bytes: ByteArray

            try {
                bytes = Files.readAllBytes(file)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }


            val visitor = object : ClassVisitor(Opcodes.ASM5) {

                // 收集非R$.styleables的R$xxx类
                override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? {
                    // read constant value
                    if (value !is Int) return null // 如果不是int值，比表示

                    val key = "$typeName.$name"
                    val old = symbols.symbols[key]

                    if (old != null && old != value) {
                        throw IllegalStateException("Value of " + key + " mismatched! "
                                + "Excepted 0x" + Integer.toHexString(old)
                                + " but was 0x" + Integer.toHexString(value))
                    } else {
                        symbols.symbols[key] = value
                    }
                    return null
                }

                override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<String>?): MethodVisitor? {

                    return if (access == Opcodes.ACC_STATIC && "<clinit>" == name) {

                        // public static final int[] a = new int[] {100, 200, 300}
//        NEWARRAY T_INT  创建int数组
//        DUP
//        ICONST_0  将100放入数组index 0
//        BIPUSH 100
//        IASTORE
//                DUP
//        ICONST_1 将200放入数组index 1
//        SIPUSH 200
//        IASTORE
//                DUP
//        ICONST_2 将300放入数组index 2
//        SIPUSH 300
//        IASTORE
//        PUTSTATIC com/example/testacm/TestA.a : [I //把数组赋值给类TestA的静态域a


                        object : MethodVisitor(Opcodes.ASM5) {

                            var current: IntArray? = null
                            var intStack = LinkedList<Int>()

                            override fun visitIntInsn(opcode: Int, operand: Int) {
                                if (opcode == Opcodes.NEWARRAY && operand == Opcodes.T_INT) {
                                    current = IntArray(intStack.pop())
                                } else if (opcode == Opcodes.BIPUSH) {
                                    intStack.push(operand)
                                }
                            }

                            override fun visitLdcInsn(cst: Any) {
                                if (cst is Int) {
                                    intStack.push(cst)
                                }
                            }

                            override fun visitInsn(opcode: Int) {
                                if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
                                    intStack.push(opcode - Opcodes.ICONST_0)
                                } else if (opcode == Opcodes.IASTORE) {
                                    val value = intStack.pop()
                                    val index = intStack.pop()
                                    current?.set(index, value)
                                }
                            }

                            override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
                                if (opcode == Opcodes.PUTSTATIC) {
                                    val old = symbols.styleables[name]
                                    if (old != null && old.size != current!!.size && !Arrays.equals(old, current)) {
                                        throw IllegalStateException("Value of styleable." + name + " mismatched! "
                                                + "Excepted " + Arrays.toString(old)
                                                + " but was " + Arrays.toString(current))
                                    } else {
                                        symbols.styleables[name] = current
                                    }
                                    current = null
                                    intStack.clear()
                                }
                            }
                        }
                    } else null
                }
            }

            ClassReader(bytes).accept(visitor, SKIP_DEBUG or SKIP_FRAMES)
        }


        private fun toStream(dir: DirectoryInput): Stream<Path> {
            try {
                return Files.walk(dir.file.toPath()).filter {path -> Files.isRegularFile(path) }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }
    }

    /**
     * 比如R.string.a=0xd7060001，保存在map里应为为["string.a"] = 0xd7060001
     */
    private var symbols: MutableMap<String, Int> = mutableMapOf()

    /**
     * 比如R.styleables.b = int[] {1, 2}，保存在map里应该为[styleables.b] = int[] {1, 2}
     */
    private val styleables = Maps.newHashMap<String, IntArray>()

    val isEmpty: Boolean
        get() = symbols.isEmpty() && styleables.isEmpty()

    operator fun get(key: String): Int? {
        return symbols[key]
    }

    fun containsKey(key: String): Boolean {
        return symbols.containsKey(key)
    }

    fun getStyleables(): Map<String, IntArray> {
        return Collections.unmodifiableMap(styleables)
    }
}
