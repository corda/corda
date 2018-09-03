package net.corda.core.crypto

import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals

class SignedDataTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Before
    fun initialise() {
        serialized = data.serialize()
    }

    val data = "Just a simple test string"
    lateinit var serialized: SerializedBytes<String>

    @Test
    fun `make sure correctly signed data is released`() {
        val keyPair = generateKeyPair()
        val sig = keyPair.private.sign(serialized.bytes, keyPair.public)
        val wrappedData = SignedData(serialized, sig)
        val unwrappedData = wrappedData.verified()

        assertEquals(data, unwrappedData)
    }

    @Test(expected = SignatureException::class)
    fun `make sure incorrectly signed data raises an exception`() {
        val keyPairA = generateKeyPair()
        val keyPairB = generateKeyPair()
        val sig = keyPairA.private.sign(serialized.bytes, keyPairB.public)
        val wrappedData = SignedData(serialized, sig)
        wrappedData.verified()
    }
}
