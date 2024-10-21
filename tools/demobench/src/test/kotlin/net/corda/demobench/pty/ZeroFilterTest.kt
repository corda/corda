package net.corda.demobench.pty

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import net.corda.coretesting.internal.rigorousMock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8

class ZeroFilterTest {
    private lateinit var output: ByteArrayOutputStream
    private lateinit var filter: OutputStream

    @Before
    fun setup() {
        output = ByteArrayOutputStream()
        val process = rigorousMock<Process>()
        doReturn(output).whenever(process).outputStream
        filter = process.zeroFiltered().outputStream
        verify(process).outputStream
    }

    @Test(timeout=300_000)
	fun `non-zero is OK`() {
        for (c in 'A'..'Z') {
            filter.write(c.toInt())
        }
        assertEquals(26, output.size())
    }

    @Test(timeout=300_000)
	fun `zero is removed`() {
        filter.write(0)
        assertEquals(0, output.size())
    }

    @Test(timeout=300_000)
	fun `zero is removed from array`() {
        val input = "He\u0000l\u0000lo".toByteArray(UTF_8)
        filter.write(input)

        assertEquals(5, output.size())
        assertEquals("Hello", output.toString("UTF-8"))
    }

    @Test(timeout=300_000)
	fun `zero is removed starting from offset`() {
        val input = "H\u0000el\u0000lo W\u0000or\u0000ld!\u0000".toByteArray(UTF_8)
        val offset = input.indexOf('W'.toByte())
        filter.write(input, offset, input.size - offset)

        assertEquals(6, output.size())
        assertEquals("World!", output.toString("UTF-8"))
    }
}
