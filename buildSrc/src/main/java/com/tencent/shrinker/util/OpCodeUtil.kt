package com.tencent.shrinker.util

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


object OpCodeUtil {


    fun pushInt(mv: MethodVisitor, i: Int) {
        when {
            i in 0..5 -> mv.visitInsn(Opcodes.ICONST_0 + i) //  ICONST_0 ~ ICONST_5
            i <= java.lang.Byte.MAX_VALUE -> mv.visitIntInsn(Opcodes.BIPUSH, i)
            i <= java.lang.Short.MAX_VALUE -> mv.visitIntInsn(Opcodes.SIPUSH, i)
            else -> mv.visitLdcInsn(i)
        }
    }

}