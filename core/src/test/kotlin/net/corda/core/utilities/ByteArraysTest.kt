/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import net.corda.core.internal.declaredField
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import kotlin.test.assertEquals

class ByteArraysTest {
    @Test
    fun `slice works`() {
        byteArrayOf(9, 9, 0, 1, 2, 3, 4, 9, 9).let {
            sliceWorksImpl(it, OpaqueBytesSubSequence(it, 2, 5))
        }
        byteArrayOf(0, 1, 2, 3, 4).let {
            sliceWorksImpl(it, OpaqueBytes(it))
        }
    }

    private fun sliceWorksImpl(array: ByteArray, seq: ByteSequence) {
        // Python-style negative indices can be implemented later if needed:
        assertSame(IllegalArgumentException::class.java, catchThrowable { seq.slice(-1) }.javaClass)
        assertSame(IllegalArgumentException::class.java, catchThrowable { seq.slice(end = -1) }.javaClass)
        fun check(expected: ByteArray, actual: ByteBuffer) {
            assertEquals(ByteBuffer.wrap(expected), actual)
            assertSame(ReadOnlyBufferException::class.java, catchThrowable { actual.array() }.javaClass)
            assertSame(array, actual.declaredField<ByteArray>(ByteBuffer::class, "hb").value)
        }
        check(byteArrayOf(0, 1, 2, 3, 4), seq.slice())
        check(byteArrayOf(0, 1, 2, 3, 4), seq.slice(0, 5))
        check(byteArrayOf(0, 1, 2, 3, 4), seq.slice(0, 6))
        check(byteArrayOf(0, 1, 2, 3), seq.slice(0, 4))
        check(byteArrayOf(1, 2, 3), seq.slice(1, 4))
        check(byteArrayOf(1, 2, 3, 4), seq.slice(1, 5))
        check(byteArrayOf(1, 2, 3, 4), seq.slice(1, 6))
        check(byteArrayOf(4), seq.slice(4))
        check(byteArrayOf(), seq.slice(5))
        check(byteArrayOf(), seq.slice(6))
        check(byteArrayOf(2), seq.slice(2, 3))
        check(byteArrayOf(), seq.slice(2, 2))
        check(byteArrayOf(), seq.slice(2, 1))
    }
}
