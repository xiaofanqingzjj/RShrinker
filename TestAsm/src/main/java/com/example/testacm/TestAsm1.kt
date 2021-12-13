package com.example.testacm

import org.objectweb.asm.*
import java.io.File
import org.objectweb.asm.MethodVisitor as MethodVisitor


fun main(args: Array<String>) {

    val classFile = File("197/com/tencent/shrinker/util/Util.class")

    println("classFile:${classFile.exists()}")

    val currentFile = File(".");
    println("current:${currentFile.absolutePath}")




    val bytes = classFile.readBytes()

    val classReader = ClassReader(bytes)

    classReader.accept(object : ClassVisitor(Opcodes.ASM5) {

        // 访问类信息
        override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
            super.visit(version, access, name, signature, superName, interfaces)
            println("visit version:$version, access:$access, name:$name, signature:$signature, superName:$superName, interfaces:$interfaces")
        }

        // 访问源码
        override fun visitSource(source: String?, debug: String?) {
            super.visitSource(source, debug)
            println("visitSource:$source")
        }

        //
        override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor {
            println("visitField:$access, name:$name, desc:$desc, signature:$signature, value:$value")
            return super.visitField(access, name, desc, signature, value)
        }

        /**
         * 访问类的方法
         */
        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            println("visitMethod access:$access, name:$name")
            return object : MethodVisitor(Opcodes.ASM5) { // 返回MethodVisitor访问方法体

                override fun visitParameter(name: String?, access: Int) {
                    super.visitParameter(name, access)
                    println("visitParameter:$name, access:$access")
                }

                // 0操作数指令
                override fun visitInsn(opcode: Int) {
                    super.visitInsn(opcode)
                    println("visitInsn:$opcode")
                }


                // 一个整形操作数指令
                override fun visitIntInsn(opcode: Int, operand: Int) {
                    super.visitIntInsn(opcode, operand)
                }

                // 局部变量操作数指令
                override fun visitVarInsn(opcode: Int, `var`: Int) {
                    super.visitVarInsn(opcode, `var`)
                }




            }
        }

                                                           },
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
}