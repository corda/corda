package net.corda.serialization.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SecureHash.Companion.SHA2_256
import net.corda.core.crypto.SecureHash.Companion.SHA2_512
import net.corda.core.crypto.algorithm
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SecureHashSerializationTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test(timeout = 300_000)
    fun `serialize and deserialize SHA-256`() {
        val before = SecureHash.randomSHA256()
        val bytes = before.serialize(context = SerializationDefaults.P2P_CONTEXT.withoutReferences()).bytes
        val after = bytes.deserialize<SecureHash>()
        assertEquals(before, after)
        assertArrayEquals(before.bytes, after.bytes)
        assertEquals(before.algorithm, after.algorithm)
        assertEquals(after.algorithm, SHA2_256)
        assertTrue(after is SecureHash.SHA256)
        assertSame(before, after)
    }

    @Test(timeout = 300_000)
    fun `serialize and deserialize SHA-512`() {
        val before = SecureHash.random(SHA2_512)
        val bytes = before.serialize(context = SerializationDefaults.P2P_CONTEXT.withoutReferences()).bytes
        val after = bytes.deserialize<SecureHash>()
        assertEquals(before, after)
        assertArrayEquals(before.bytes, after.bytes)
        assertEquals(before.algorithm, after.algorithm)
        assertEquals(after.algorithm, SHA2_512)
        assertTrue(after is SecureHash.HASH)
        assertSame(before, after)
    }
}
