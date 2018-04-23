/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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