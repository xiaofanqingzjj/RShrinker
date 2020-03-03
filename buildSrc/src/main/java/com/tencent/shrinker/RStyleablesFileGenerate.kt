package com.tencent.shrinker

import com.tencent.shrinker.util.OpCodeUtil
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files


/**
 *
 * 合并，并生成R.styleables.java类
 *
 */
object RStyleablesFileGenerate {


    /**
     * 根据收集的
     */
    fun generate(symbols: RSymbols, outPath: File) {

        if (symbols.isEmpty) return

        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_6,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_SUPER,
                RSymbols.R_STYLEABLES_CLASS_NAME, null,
                "java/lang/Object", null)

        for (name in symbols.getStyleables().keys) {
            writer.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, name, "[I", null, null)
        }

        writeClinit(symbols, writer)
        writer.visitEnd()



        // 把生成的Java文件保存在文件当中
        val bytes = writer.toByteArray()
        try {
            if (!outPath.isDirectory && !outPath.mkdirs()) {
                throw RuntimeException("Cannot mkdir $outPath")
            }
            Files.write(outPath.toPath().resolve(RSymbols.R_STYLEABLES_CLASS_NAME + ".class"), bytes)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }


    private fun writeClinit(symbols: RSymbols, writer: ClassWriter) {
        val styleables = symbols.getStyleables()

        val clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        clinit.visitCode()

        for (entry in styleables.entries) {
            val field = entry.key
            val value = entry.value
            val length = value.size

            OpCodeUtil.pushInt(clinit, length)

            clinit.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
            for (i in 0 until length) {
                clinit.visitInsn(Opcodes.DUP)                  // dup
                OpCodeUtil.pushInt(clinit, i)
                OpCodeUtil.pushInt(clinit, value[i])
                clinit.visitInsn(Opcodes.IASTORE)              // iastore
            }
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, RSymbols.R_STYLEABLES_CLASS_NAME, field, "[I")
        }
        clinit.visitInsn(Opcodes.RETURN)
        clinit.visitMaxs(0, 0) // auto compute
        clinit.visitEnd()
    }


}