package net.corda.attestation.message.ias

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RevocationListProxyResponseTest {
    private companion object {
        private const val SPID = "84D402C36BA9EF9B0A86EF1A9CC8CE4F"
        private val revocationListData = byteArrayOf(0x51, 0x62, 0x43, 0x24, 0x75, 0x4D)
        private val revocationListBase64 = revocationListData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialiseBasic() {
        val response = RevocationListProxyResponse(
            spid = SPID,
            revocationList = revocationListData
        )
        val str = mapper.writeValueAsString(response)
        assertEquals("{"
                + "\"spid\":\"$SPID\","
                + "\"revocationList\":\"$revocationListBase64\""
                + "}", str)
    }

    @Test
    fun testDeserialiseBasic() {
        val str = """{
             "spid":"$SPID",
             "revocationList":"$revocationListBase64"
        }"""
        val response = mapper.readValue(str, RevocationListProxyResponse::class.java)
        assertEquals(SPID, response.spid)
        assertArrayEquals(revocationListData, response.revocationList)
    }
}