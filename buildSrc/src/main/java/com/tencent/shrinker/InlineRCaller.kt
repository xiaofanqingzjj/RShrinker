/*
 * Copyright (c) 2017 Yrom Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.shrinker

import com.tencent.shrinker.util.OpCodeUtil
import com.tencent.shrinker.util.Util.isRClass
import com.tencent.shrinker.util.logger
import org.gradle.api.logging.LogLevel
import org.objectweb.asm.*
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES

/**
 *
 * class 转换
 *
 * 根据输入的class流转换成新的class流
 *
 *
 *
 *
 * @author yrom
 */
object InlineRCaller {


    fun inlineRCaller(rSymbols: RSymbols, origin: ByteArray): ByteArray {

        // 使用ClassReader解析类
        val reader = ClassReader(origin)

        val precondition = PredicateClassVisitor()

        // 传入一个Visitor来访问类的内容
        reader.accept(precondition, SKIP_DEBUG or SKIP_FRAMES)


        //  类是否访问类R文件，如果没有返回，则不做任何操作
        if (!precondition.isAttemptToVisitR) {
            return origin
        }


        // don't pass reader to the writer.
        // or it will copy 'CONSTANT POOL' that contains no used entries to lead proguard running failed!

        // 如果访问了，则把访问的内容替换成常量

        // 创建一个ClassWriter来修改类
        val writer = ClassWriter(0)

        // 把ClassWriter作为Visitor的代理，
        val visitor = ShrinkRClassVisitor(writer, rSymbols)

        // 访问类
        reader.accept(visitor, 0)


        return writer.toByteArray()
    }




    private class PredicateClassVisitor : ClassVisitor(Opcodes.ASM5) {
        var isAttemptToVisitR: Boolean = false
            private set


        override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
            if (!isAttemptToVisitR
                    && access == 0x19 /*ACC_PUBLIC | ACC_STATIC | ACC_FINAL*/
                    && isRClass(name)) {
                isAttemptToVisitR = true
            }
        }

        override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?, exceptions: Array<String?>?): MethodVisitor? {
            return if (isAttemptToVisitR) null else object : MethodVisitor(Opcodes.ASM5, null) {


                // 访问某个类的属性
                override fun visitFieldInsn(opcode: Int, owner: String?, fieldName: String?, fieldDesc: String?) {

                    if (isAttemptToVisitR
                            || opcode != Opcodes.GETSTATIC
                            || owner?.startsWith("java/lang/") == true) {
                        return
                    }

                    isAttemptToVisitR = isRClass(owner)
                }
            }


            // 返回一个方法访问器，用来检测是否调用了R相关的类
        }
    }


    /**
     * @author yrom
     * @version 2017/11/29
     */
    private class ShrinkRClassVisitor(cv: ClassWriter, private val rSymbols: RSymbols) : ClassVisitor(Opcodes.ASM5, cv) {

        private var classname: String? = null

        override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<String?>?) {
            classname = name
            logger.debug("processing class $name")
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? {
            return cv.visitField(access, name, desc, signature, value)
        }

        override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
            if (access == 0x19 /*ACC_PUBLIC | ACC_STATIC | ACC_FINAL*/ && isRClass(name)) {
                logger.debug("remove visit inner class {} in {}", name, classname)
                return
            }
            cv.visitInnerClass(name, outerName, innerName, access)
        }

        override fun visitMethod(access: Int, name: String, desc: String?,
                                 signature: String?, exceptions: Array<String?>?): MethodVisitor {


            return object : MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {

                override fun visitFieldInsn(opcode: Int, owner: String?, fieldName: String?, fieldDesc: String?) {

                    // 如果指令不是获取静态变量的指令直接跳过
                    if (opcode != Opcodes.GETSTATIC || owner?.startsWith("java/lang/") == true) {
                        // skip!
                        this.mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc)
                        return
                    }

                    // 获取R.xxx
                    val typeName = owner?.substring(owner.lastIndexOf('/') + 1)
                    val key = "$typeName.$fieldName"

                    if (rSymbols.containsKey(key)) {
                        // 在符号表中找对应的值
                        val value = rSymbols[key]
                                ?: throw UnsupportedOperationException("value of $key is null!")
                        if (logger.isEnabled(LogLevel.DEBUG)) {
                            logger.debug("replace {}.{} to 0x{}", owner, fieldName, Integer.toHexString(value))
                        }

                        // GETSTATIC指令的意思为，获取指定的静态域，将其压入栈顶，所以我们要替换这个操作
                        // 直接把常量值压入栈顶

                        OpCodeUtil.pushInt(this.mv, value)


                    } else if (owner?.endsWith("/R\$styleable") == true) { // replace all */R$styleable ref!

                        // 所有R.styleable的访问，替换成访问自己生成的R.styleables类
                        this.mv.visitFieldInsn(opcode, RSymbols.R_STYLEABLES_CLASS_NAME, fieldName, fieldDesc)
                    } else {
                        this.mv.visitFieldInsn(opcode, owner, fieldName, fieldDesc)
                    }
                }
            }
        }

    }
}


