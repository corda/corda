package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChallengeRequestTest {
    private companion object {
        private val publicKeyData = byteArrayOf(0x1F, 0x0A, 0x22, 0x37)
        private val publicKeyBase64 = publicKeyData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val challenge = ChallengeRequest(publicKeyData, "<nonce-value>")
        val str = mapper.writeValueAsString(challenge)
        assertEquals("""{"gc":"$publicKeyBase64","nonce":"<nonce-value>"}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"gc":"$publicKeyBase64","nonce":"<nonce-value>"}"""
        val challenge = mapper.readValue(str, ChallengeRequest::class.java)
        assertArrayEquals(publicKeyData, challenge.gc)
        assertEquals("<nonce-value>", challenge.nonce)
    }
}
