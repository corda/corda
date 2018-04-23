/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import java.io.InputStream

class RepeatingBytesInputStream(val bytesToRepeat: ByteArray, val numberOfBytes: Int) : InputStream() {
    private var bytesLeft = numberOfBytes
    override fun available() = bytesLeft
    override fun read(): Int {
        if (bytesLeft == 0) {
            return -1
        } else {
            bytesLeft--
            return bytesToRepeat[(numberOfBytes - bytesLeft) % bytesToRepeat.size].toInt()
        }
    }

    override fun read(byteArray: ByteArray, offset: Int, length: Int): Int {
        val lastIdx = Math.min(Math.min(offset + length, byteArray.size), offset + bytesLeft)
        for (i in offset until lastIdx) {
            byteArray[i] = bytesToRepeat[(numberOfBytes - bytesLeft + i - offset) % bytesToRepeat.size]
        }
        val bytesRead = lastIdx - offset
        bytesLeft -= bytesRead
        return if (bytesRead == 0 && bytesLeft == 0) -1 else bytesRead
    }
}