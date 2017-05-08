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
        val until = Math.min(Math.min(offset + length, byteArray.size), offset + bytesLeft)
        for (i in offset .. until - 1) {
            byteArray[i] = bytesToRepeat[(numberOfBytes - bytesLeft + i - offset) % bytesToRepeat.size]
        }
        val bytesRead = until - offset
        bytesLeft -= bytesRead
        return if (bytesRead == 0 && bytesLeft == 0) -1 else bytesRead
    }
}