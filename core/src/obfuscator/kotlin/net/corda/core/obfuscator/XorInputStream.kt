package net.corda.core.obfuscator

import java.io.FilterInputStream
import java.io.InputStream

@Suppress("MagicNumber")
class XorInputStream(private val source : InputStream) : FilterInputStream(source) {
    var prev : Int = 0

    override fun read(): Int {
        prev = source.read() xor prev
        return prev - 0x80
    }

    override fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        var read = 0
        while(true) {
            val b = source.read()
            if(b < 0) break
            buffer[off + read++] = ((b xor prev) - 0x80).toByte()
            prev = b
            if(read == len) break
        }
        return read
    }
}