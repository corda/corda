package core.crypto

import core.serialization.serialize
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals

class SignedDataTest {
    val data = "Just a simple test string"
    val serialized = data.serialize()

    @Test
    fun `make sure correctly signed data is released`() {
        val keyPair = generateKeyPair()
        val sig = keyPair.private.signWithECDSA(serialized.bits, keyPair.public)
        val wrappedData = SignedData(serialized, sig)
        val unwrappedData = wrappedData.verified()

        assertEquals(data, unwrappedData)
    }

    @Test(expected = SignatureException::class)
    fun `make sure incorrectly signed data raises an exception`() {
        val keyPairA = generateKeyPair()
        val keyPairB = generateKeyPair()
        val sig = keyPairA.private.signWithECDSA(serialized.bits, keyPairB.public)
        val wrappedData = SignedData(serialized, sig)
        wrappedData.verified()
    }
}