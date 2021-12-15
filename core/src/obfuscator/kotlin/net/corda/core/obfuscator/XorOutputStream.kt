package net.corda.core.obfuscator

import java.io.FilterOutputStream
import java.io.OutputStream

@Suppress("MagicNumber")
class XorOutputStream(private val destination : OutputStream) : FilterOutputStream(destination) {
    var prev : Int = 0

    override fun write(byte: Int) {
        val b = (byte + 0x80) xor prev
        destination.write(b)
        prev = b
    }

    override fun write(buffer: ByteArray) {
        write(buffer, 0, buffer.size)
    }

    override fun write(buffer: ByteArray, off: Int, len: Int) {
        var written = 0
        while(true) {
            val b = (buffer[written] + 0x80) xor prev
            destination.write(b)
            prev = b
            ++written
            if(written == len) break
        }
    }
}