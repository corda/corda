package net.corda.attestation.ias

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.ias.message.ReportProxyResponse
import net.corda.attestation.ias.message.ReportRequest
import net.corda.attestation.readValue
import org.apache.http.HttpStatus.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.nio.charset.StandardCharsets.*

class ReportProxyIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
    }
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper().registerModule(JavaTimeModule())
        val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()
        httpClient = HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager().apply { socketConfig = httpSocketConfig })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    @After
    fun done() {
        httpClient.close()
    }

    @Test
    fun testIASReportWithoutManifest() {
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val requestMessage = ReportRequest(
            isvEnclaveQuote = quote,
            nonce = "0000000000000000"
        )
        val request = HttpPost("http://localhost:$httpPort/isv/report").apply {
            entity = StringEntity(mapper.writeValueAsString(requestMessage), APPLICATION_JSON)
        }
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val responseMessage: ReportProxyResponse = mapper.readValue(response)
        assertTrue(responseMessage.signature.isNotEmpty())
        assertTrue(responseMessage.certificatePath.isNotEmpty())

        val iasReport = toMap(responseMessage.report.inputStream())
        assertEquals("OK", iasReport["isvEnclaveQuoteStatus"])
        assertEquals("0000000000000000", iasReport["nonce"])
        assertNull(iasReport["pseManifestStatus"])
    }

    @Test
    fun testIASReportWithManifest() {
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val requestMessage = ReportRequest(
            isvEnclaveQuote = quote,
            pseManifest = byteArrayOf(0x63, 0x31, 0x0D, 0x5A),
            nonce = "0000000000000000"
        )
        val request = HttpPost("http://localhost:$httpPort/isv/report").apply {
            entity = StringEntity(mapper.writeValueAsString(requestMessage), APPLICATION_JSON)
        }
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val responseMessage: ReportProxyResponse = mapper.readValue(response)
        assertTrue(responseMessage.signature.isNotEmpty())
        assertTrue(responseMessage.certificatePath.isNotEmpty())

        val iasReport = toMap(responseMessage.report.inputStream())
        assertEquals("OK", iasReport["isvEnclaveQuoteStatus"])
        assertEquals("0000000000000000", iasReport["nonce"])
        assertEquals("OK", iasReport["pseManifestStatus"])
    }

    private fun toMap(input: InputStream): Map<String, Any>
        = mapper.readValue(input, object : TypeReference<Map<String, Any>>() {})
}
