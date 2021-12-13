/*
 * Copyright (c) 2018 Yrom Wang
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

package com.example.testacm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

/**
 *
 *
 * @author yrom
 * @version 2018/1/9
 */
final class WriteStyleablesProcessor implements Processor {
    private RSymbols symbols;
    private File dir;

    WriteStyleablesProcessor(RSymbols symbols, File dir) {
        this.symbols = symbols;
        this.dir = dir;
    }

    @Override
    public void proceed() {


        // 创建一个类生成器
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // 创建类名
        writer.visit(Opcodes.V1_6,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_SUPER,
                RSymbols.R_STYLEABLES_CLASS_NAME,
                null,
                "java/lang/Object",
                null);

        // 创建类属性
        for (String name : symbols.getStyleables().keySet()) {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name, "[I", null, null);
        }


        // 创建2个属性
        writer.visitField(Opcodes.ACC_PRIVATE, "test", "[Ljava.lang.String;", null, null);
        writer.visitField(Opcodes.ACC_PRIVATE, "test2", "Ljava.util.Map;", null, null);

        // 创建方法
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PRIVATE, "testMethod", "(IILjava.lang.String;)[Ljava.util.List;", null, null);


        mv.visitCode();

//        mv.visitInsn(Opcodes.SIPUSH);

//        pushInt(mv, 2);

        mv.visitEnd();

        writeClinit(writer);


        writer.visitEnd();


        // 把生成的Java文件保存在文件当中
        byte[] bytes = writer.toByteArray();
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new RuntimeException("Cannot mkdir " + dir);
            }
            Files.write(dir.toPath().resolve(RSymbols.R_STYLEABLES_CLASS_NAME + ".class"), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeClinit(ClassWriter writer) {
        Map<String, int[]> styleables = symbols.getStyleables();

        // 创建构造函数
        MethodVisitor mvConstructor = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);

        // 开始写入代码
        mvConstructor.visitCode();

        // 创建数组的字节码代码为：
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


//        styleables.

        for (Map.Entry<String, int[]> entry : styleables.entrySet()) {
            final String field = entry.getKey();
            final int[] value = entry.getValue();
            final int length = value.length;

            //
            pushInt(mvConstructor, length);

            // 创建数组
            mvConstructor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);


            for (int i = 0; i < length; i++) {
                mvConstructor.visitInsn(Opcodes.DUP);                  // dup

                pushInt(mvConstructor, i); // 数组的index 压入栈顶
                pushInt(mvConstructor, value[i]); // 对应index的值，压入栈顶

                mvConstructor.visitInsn(Opcodes.IASTORE);              // iastore，保存值到数组对应的index
            }

            // 将数组写入类的域
            mvConstructor.visitFieldInsn(Opcodes.PUTSTATIC, RSymbols.R_STYLEABLES_CLASS_NAME, field, "[I");
        }

        mvConstructor.visitInsn(Opcodes.RETURN);
        mvConstructor.visitMaxs(0, 0); // auto compute
        mvConstructor.visitEnd();
    }


    /**
     * 把某个值推送到栈顶
     * @param mv
     * @param i
     */
    private static void pushInt(MethodVisitor mv, int i) {
        if (0 <= i && i <= 5) {
            // 如果i<=5，则把对应的值推送到栈顶
            mv.visitInsn(Opcodes.ICONST_0 + i); //  ICONST_0 ~ ICONST_5
        } else if (i <= Byte.MAX_VALUE) {

            // 如果i<= 127，直接把对应的值推送到栈顶
            mv.visitIntInsn(Opcodes.BIPUSH, i);
        } else if (i <= Short.MAX_VALUE) {

            // 如果小于32767，
            mv.visitIntInsn(Opcodes.SIPUSH, i);
        } else {

            //
            mv.visitLdcInsn(i);
        }
    }
}
