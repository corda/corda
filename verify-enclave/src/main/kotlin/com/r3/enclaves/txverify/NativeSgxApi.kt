package com.r3.enclaves.txverify

object NativeSgxApi {

    init {
        System.loadLibrary("untrusted_corda_sgx")
    }

    external fun verify(enclavePath: String, transactionBytes: ByteArray): String?
}