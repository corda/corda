package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ServiceResponseTest {
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
        val service = ServiceResponse(spid = SPID, quoteType = 1)
        val str = mapper.writeValueAsString(service)
        assertEquals("""{"spid":"$SPID","quoteType":1}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"spid":"$SPID","quoteType":1}"""
        val service = mapper.readValue(str, ServiceResponse::class.java)
        assertEquals(SPID, service.spid)
        assertEquals(1.toShort(), service.quoteType)
    }
}