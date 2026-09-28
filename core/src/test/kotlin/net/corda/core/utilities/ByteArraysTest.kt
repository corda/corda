package net.corda.core.utilities

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import kotlin.test.assertEquals

class ByteArraysTest {
    @Test(timeout=300_000)
	fun `slice works`() {
        sliceWorksImpl(OpaqueBytesSubSequence(byteArrayOf(9, 9, 0, 1, 2, 3, 4, 9, 9), 2, 5))
        sliceWorksImpl(OpaqueBytes(byteArrayOf(0, 1, 2, 3, 4)))
    }

    private fun sliceWorksImpl(seq: ByteSequence) {
        // Python-style negative indices can be implemented later if needed:
        assertSame(IllegalArgumentException::class.java, catchThrowable { seq.slice(-1) }.javaClass)
        assertSame(IllegalArgumentException::class.java, catchThrowable { seq.slice(end = -1) }.javaClass)
        fun check(expected: ByteArray, actual: ByteBuffer) {
            assertEquals(ByteBuffer.wrap(expected), actual)
            assertSame(ReadOnlyBufferException::class.java, catchThrowable { actual.array() }.javaClass)
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

    @Test(timeout=300_000)
	fun `test hex parsing strictly uppercase`() {
        val hexRegex = "^[0-9A-F]+\$".toRegex()

        val privacySalt = net.corda.core.contracts.PrivacySalt()
        val privacySaltAsHexString = privacySalt.bytes.toHexString()
        assertTrue(privacySaltAsHexString.matches(hexRegex))

        val stateRef = StateRef(SecureHash.randomSHA256(), 0)
        val txhashAsHexString = stateRef.txhash.bytes.toHexString()
        assertTrue(txhashAsHexString.matches(hexRegex))
    }
}
