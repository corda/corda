package com.r3.enclaves.txverify

import java.security.SecureRandomSpi

class SgxSecureRandomSpi : SecureRandomSpi() {
    override fun engineSetSeed(p0: ByteArray?) {
        println("SgxSecureRandomSpi.engineSetSeed called")
    }

    override fun engineNextBytes(p0: ByteArray?) {
        println("SgxSecureRandomSpi.engineNextBytes called")
    }

    override fun engineGenerateSeed(numberOfBytes: Int): ByteArray {
        println("SgxSecureRandomSpi.engineGenerateSeed called")
        return ByteArray(numberOfBytes)
    }
}