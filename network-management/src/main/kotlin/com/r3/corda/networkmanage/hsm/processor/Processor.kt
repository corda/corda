package com.r3.corda.networkmanage.hsm.processor

import com.r3.corda.networkmanage.hsm.utils.mapCryptoServerException

abstract class Processor {

    protected fun processError(exception: Exception) {
        val processed = mapCryptoServerException(exception)
        System.err.println("An error occurred:")
        processed.printStackTrace()
    }

    protected fun printlnColor(line: String?, color: String = "") {
        println(color + line)
    }
}