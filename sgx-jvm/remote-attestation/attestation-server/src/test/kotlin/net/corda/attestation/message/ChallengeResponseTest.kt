package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChallengeResponseTest {
    private companion object {
        private val serviceKeyData = byteArrayOf(0x10, 0x00, 0x22, 0x00)
        private val serviceKeyBase64 = serviceKeyData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val challenge = ChallengeResponse("<nonsense>", serviceKeyData)
        val str = mapper.writeValueAsString(challenge)
        assertEquals("""{"nonce":"<nonsense>","serviceKey":"$serviceKeyBase64"}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"nonce":"<nonsense>","serviceKey":"$serviceKeyBase64"}"""
        val challenge = mapper.readValue(str, ChallengeResponse::class.java)
        assertEquals("<nonsense>", challenge.nonce)
        assertArrayEquals(serviceKeyData, challenge.serviceKey)
    }
}