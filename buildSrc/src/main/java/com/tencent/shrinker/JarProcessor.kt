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

import com.google.common.collect.ImmutableList

import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


/**
 * @author yrom
 * @version 2017/11/29
 */
object JarProcessor {


    //    static void inlineDirInput(RSymbols symbols, Path src, Path dst) {
    //        List<Path> files = resolveSources(src, dst);
    //
    //        if (files.size() >= Runtime.getRuntime().availableProcessors()) {
    //            files.parallelStream().forEach((e)->{
    //                proceedFile(symbols, e, dst);
    //            });
    //        } else {
    //            files.forEach((e)-> {
    //                proceedFile(symbols, e, dst);
    //            });
    //        }
    //    }

    fun proceed(symbols: RSymbols, src: Path, dst: Path) {
        try {
            val entryList = readZipEntries(src)
                    .parallelStream()
                    .map { e -> transformClassBlob(symbols, e) }
                    .collect(Collectors.toList())


            if (entryList.isEmpty()) return

            Files.newOutputStream(dst).use { fileOut ->
                val buffer = zipEntries(entryList)
                buffer.writeTo(fileOut)
            }
        } catch (e: IOException) {
            throw RuntimeException("Reading jar entries failure", e)
        }

    }

    @Throws(IOException::class)
    private fun zipEntries(entryList: List<Pair<String, ByteArray>>): ByteArrayOutputStream {

        val buffer = ByteArrayOutputStream(8192)

        ZipOutputStream(buffer).use { jar ->

            jar.setMethod(ZipOutputStream.STORED)

            val crc = CRC32()

            for (entry in entryList) {

                val bytes = entry.second
                val newEntry = ZipEntry(entry.first)

                newEntry.method = ZipEntry.STORED // chose STORED method

                crc.reset()
                crc.update(entry.second)

                newEntry.crc = crc.value
                newEntry.size = bytes.size.toLong()

                writeEntryToJar(newEntry, bytes, jar)
            }
            jar.flush()
        }
        return buffer
    }

    private fun transformClassBlob(symbols: RSymbols, entry: Pair<String, ByteArray>): Pair<String, ByteArray> {
        val bytes = entry.second
        entry.second = InlineRCaller.inlineRCaller(symbols, bytes)
        return entry
    }


    /**
     * 读取zip包里的内容
     * @param src jar路径
     * @return 返回每个class item和其内容的byte[]
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun readZipEntries(src: Path): List<Pair<String, ByteArray>> {


        val list = ImmutableList.builder<Pair<String, ByteArray>>()

        ZipInputStream(ByteArrayInputStream(Files.readAllBytes(src))).use { zip ->

            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {

                val name = entry.name
                if (!name.endsWith(".class")) {
                    // skip
                    entry = zip.nextEntry
                    continue
                }
                val entrySize = entry.size
                if (entrySize >= Integer.MAX_VALUE) {
                    throw OutOfMemoryError("Too large class file $name, size is $entrySize")
                }
                val bytes = readByteArray(zip, entrySize.toInt())
                list.add(Pair.of(name, bytes))
                entry = zip.nextEntry
            }
        }
        return list.build()
    }

    @Throws(IOException::class)
    private fun readByteArray(zip: ZipInputStream, expected: Int): ByteArray {
        if (expected == -1) { // unknown size
            return IOUtils.toByteArray(zip)
        }
        val bytes = ByteArray(expected)
        var read = 0
        do {
            val n = zip.read(bytes, read, expected - read)
            if (n <= 0) {
                break
            }
            read += n
        } while (read < expected)

        if (expected != bytes.size) {
            throw EOFException("unexpected EOF")
        }
        return bytes
    }


    private fun writeEntryToJar(entry: ZipEntry, bytes: ByteArray, jar: ZipOutputStream) {
        try {
            jar.putNextEntry(entry)
            jar.write(bytes)
            jar.closeEntry()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

    }

    // mutable pair
    internal class Pair<F, S>(var first: F, var second: S) {
        companion object {

            fun <F, S> of(first: F, second: S): Pair<F, S> {
                return Pair(first, second)
            }
        }
    }
}
