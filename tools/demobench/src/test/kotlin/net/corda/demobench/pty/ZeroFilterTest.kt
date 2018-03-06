/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.pty

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.testing.internal.rigorousMock
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

    @Test
    fun `non-zero is OK`() {
        for (c in 'A'..'Z') {
            filter.write(c.toInt())
        }
        assertEquals(26, output.size())
    }

    @Test
    fun `zero is removed`() {
        filter.write(0)
        assertEquals(0, output.size())
    }

    @Test
    fun `zero is removed from array`() {
        val input = "He\u0000l\u0000lo".toByteArray(UTF_8)
        filter.write(input)

        assertEquals(5, output.size())
        assertEquals("Hello", output.toString("UTF-8"))
    }

    @Test
    fun `zero is removed starting from offset`() {
        val input = "H\u0000el\u0000lo W\u0000or\u0000ld!\u0000".toByteArray(UTF_8)
        val offset = input.indexOf('W'.toByte())
        filter.write(input, offset, input.size - offset)

        assertEquals(6, output.size())
        assertEquals("World!", output.toString("UTF-8"))
    }
}
