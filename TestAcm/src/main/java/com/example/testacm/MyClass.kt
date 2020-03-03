package com.example.testacm

import java.io.File

object MyClass {


    @JvmStatic
    fun main(args: Array<String>) {
        println("Hello World!!")

        val file = File("197")
        val symbol = RSymbols.collectAllRFiles(file)
        println("symbol:$symbol")

//        WriteStyleablesProcessor(RSymbols(), File("generate")).proceed()


    }
}
