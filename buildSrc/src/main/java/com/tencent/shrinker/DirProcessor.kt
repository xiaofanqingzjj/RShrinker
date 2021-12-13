package com.tencent.shrinker

import com.google.common.collect.ImmutableList
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.*

/**
 * @author yrom
 * @version 2017/11/29
 */
object DirProcessor {


    private val CASE_R_FILE = FileSystems.getDefault().getPathMatcher("regex:^R\\.class|R\\$[a-z]+\\.class$")

    private val CLASS_TRANSFORM_FILTER: DirectoryStream.Filter<Path> = DirectoryStream.Filter{
        path -> Files.isDirectory(path) || Files.isRegularFile(path) && !CASE_R_FILE.matches(path.getFileName())
    }

    /**
     *
     */
    fun proceed(symbols: RSymbols, src: Path, dst: Path) {
        val files = resolveSources(src)

        if (files.size >= Runtime.getRuntime().availableProcessors()) {
            files.parallelStream().forEach { e -> proceedFile(symbols, e, dst) }
        } else {
            files.forEach { e -> proceedFile(symbols, e, dst) }
        }
    }


    private fun proceedFile(symbols: RSymbols, source: Path, dst: Path) {

        val name = source.fileName.toString()

        val target = dst.resolve(name)

        if (Files.isDirectory(source)) {
            proceed(symbols, source, target)
        } else if (Files.isRegularFile(source)) {
            try {

                // 创建目标文件
                if (Files.notExists(dst)) {
                    Files.createDirectories(dst)
                }

                if (source.fileName.toString().endsWith(".class")) {
                    val bytes = InlineRCaller.inlineRCaller(symbols, Files.readAllBytes(source))
                    Files.write(target, bytes)
                } else {
                    // copy non-class file!
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun resolveSources(src: Path): List<Path> {
        val list = ImmutableList.builder<Path>()
        try {
            Files.newDirectoryStream(src, CLASS_TRANSFORM_FILTER).use { dir ->
                for (file in dir) {
                    list.add(file)
                }
            }
        } catch (e: DirectoryIteratorException) {
            throw UncheckedIOException(e.cause)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }

        return list.build()
    }
}
