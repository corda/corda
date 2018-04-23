package net.corda.attestation.message.ias

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReportProxyResponseTest {
    private companion object {
        private val reportData = byteArrayOf(0x51, 0x62, 0x43, 0x24, 0x75, 0x4D)
        private val reportBase64 = reportData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialiseBasic() {
        val response = ReportProxyResponse(
            signature = "<signature-data>",
            certificatePath = "<certificate-path>",
            report = reportData
        )
        val str = mapper.writeValueAsString(response)
        assertEquals("{"
                + "\"signature\":\"<signature-data>\","
                + "\"certificatePath\":\"<certificate-path>\","
                + "\"report\":\"$reportBase64\""
                + "}", str)
    }

    @Test
    fun testDeserialiseBasic() {
        val str = """{
            "signature":"<signature-data>",
            "certificatePath":"<certificate-path>",
            "report":"$reportBase64"
        }"""
        val response = mapper.readValue(str, ReportProxyResponse::class.java)
        assertEquals("<signature-data>", response.signature)
        assertEquals("<certificate-path>", response.certificatePath)
        assertArrayEquals(reportData, response.report)
    }
}