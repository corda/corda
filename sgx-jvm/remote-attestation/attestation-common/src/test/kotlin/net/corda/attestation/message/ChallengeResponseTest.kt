package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChallengeResponseTest {
    private companion object {
        private const val SPID = "0123456789ABCDEF"
        private val gaData = byteArrayOf(0x10, 0x00, 0x22, 0x00)
        private val gaBase64 = gaData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val challenge = ChallengeResponse(ga = gaData, spid = SPID, quoteType = 1)
        val str = mapper.writeValueAsString(challenge)
        assertEquals("""{"ga":"$gaBase64","spid":"$SPID","quoteType":1}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"ga":"$gaBase64","spid":"$SPID","quoteType":1}"""
        val challenge = mapper.readValue(str, ChallengeResponse::class.java)
        assertArrayEquals(gaData, challenge.ga)
        assertEquals(SPID, challenge.spid)
        assertEquals(1.toShort(), challenge.quoteType)
    }
}
