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
 *
 *
 */
class RSymbols() {

    companion object {
        /**
         * default package!
         */
        const val R_STYLEABLES_CLASS_NAME = "R\$styleable"


        /**
         * 从inputs中解析出所有的R$xxx类
         *
         * R文件包含的内容如下：
         * class R {
         *  public static final class styleable {
         *      public static final int[] FontFamily = new int[]{2130968969, 2130968970, 2130968971, 2130968972, 2130968973, 2130968974};
         *      public static final int FontFamily_fontProviderAuthority = 0;
         *      public static final int fontfamily_fontprovidercerts = 1;
         *      ...
         *  }
         *  public static final class style {}
         *  public static final class string {}
         * }
         *
         * 除了styleable类包含数组类型的值以外，其他的类只包含整数值。
         *
         * 这个方法收集所有的R符号，保存在字典中
         *
         * key值为类名+字典名，值为对应的常量值
         *
         *
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
                stream = paths.parallelStream()
                symbols.symbols = Maps.newConcurrentMap()
            } else {
                stream = paths.stream()
                symbols.symbols = Maps.newHashMap()
            }

            // 找到所有R$xxxx.class类
            val rClassMatcher = FileSystems.getDefault().getPathMatcher("glob:R$*.class")
            stream.filter {
                rClassMatcher.matches(it.fileName)
            }.forEach {


                drainRSymbolsFromRFile(it, symbols)
            }

            return symbols
        }

        /**
         * 解析class，获取R子类的符号名
         */
        private fun drainRSymbolsFromRFile(file: Path, symbols: RSymbols) {

            val filename = file.fileName.toString()

            // 类名
            val typeName = filename.substring(0, filename.length - ".class".length)

            // 读取字节码内容
            val bytes: ByteArray

            try {
                bytes = Files.readAllBytes(file)
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }


            val visitor = object : ClassVisitor(Opcodes.ASM5) {

                /**
                 * 收集非R$.styleables的R$xxx类
                 *
                 * 遍历类的字段
                 *
                 */
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

                    //
                    // 解析
                    // public static final class styleable {
                    //      public static final int[] FontFamily = new int[]{2130968969, 2130968970, 2130968971, 2130968972, 2130968973, 2130968974};
                    //      ...
                    // }
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


                        // 返回一个方法访问器
                        object : MethodVisitor(Opcodes.ASM5) {

                            var current: IntArray? = null // 要收集的数组
                            var intStack = LinkedList<Int>() // 模拟操作数栈

                            override fun visitIntInsn(opcode: Int, operand: Int) {
                                if (opcode == Opcodes.NEWARRAY && operand == Opcodes.T_INT) { // NEWARRAY指令，根据栈定创建指定大小的数组
                                    current = IntArray(intStack.pop())
                                } else if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) { // 模拟入栈操作
                                    intStack.push(operand)
                                }
                            }

                            override fun visitLdcInsn(cst: Any) {// 模拟入栈操作
                                if (cst is Int) {
                                    intStack.push(cst)
                                }
                            }

                            override fun visitInsn(opcode: Int) {
                                if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) { // 模拟入栈操作
                                    intStack.push(opcode - Opcodes.ICONST_0)
                                } else if (opcode == Opcodes.IASTORE) { // IASTORE从模拟栈中读取指定的值
                                    val value = intStack.pop()
                                    val index = intStack.pop()
                                    current?.set(index, value)
                                }
                            }

                            override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) { // 数组创建完毕
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
