package net.corda.serialization.internal.amqp

import org.apache.qpid.proton.codec.ReadableBuffer.ByteBufferReader
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class OutputStreamWritableBufferTests {

    @Test(timeout = 300_000)
    fun testByte() {
        val stream = ByteArrayOutputStream()
        val buffer = OutputStreamWritableBuffer(stream)
        var b = Byte.MIN_VALUE
        while (b <= Byte.MAX_VALUE) {
            buffer.put(b)
            if (b == Byte.MAX_VALUE) break
            b++
        }
        stream.close()

        b = Byte.MIN_VALUE
        val bytes = stream.toByteArray()
        for (byte in bytes) {
            assertEquals(b++, byte)
        }
    }

    @Test(timeout = 300_000)
    fun testInt() {
        val stream = ByteArrayOutputStream()
        val buffer = OutputStreamWritableBuffer(stream)
        buffer.putInt(Int.MIN_VALUE)
        buffer.putInt(Int.MAX_VALUE)
        stream.close()

        val reader = ByteBufferReader.wrap(stream.toByteArray())
        assertEquals(Int.MIN_VALUE, reader.int)
        assertEquals(Int.MAX_VALUE, reader.int)
    }

    @Test(timeout = 300_000)
    fun testLong() {
        val stream = ByteArrayOutputStream()
        val buffer = OutputStreamWritableBuffer(stream)
        buffer.putLong(Long.MIN_VALUE)
        buffer.putLong(Long.MAX_VALUE)
        stream.close()

        val reader = ByteBufferReader.wrap(stream.toByteArray())
        assertEquals(Long.MIN_VALUE, reader.long)
        assertEquals(Long.MAX_VALUE, reader.long)
    }
}