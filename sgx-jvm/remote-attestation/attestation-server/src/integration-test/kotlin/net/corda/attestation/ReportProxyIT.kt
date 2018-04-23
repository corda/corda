package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.ias.*
import org.apache.http.HttpStatus.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets.*

class ReportProxyIT {
    private val httpPort = Integer.getInteger("test.isv.httpPort")
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var mapper: ObjectMapper

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

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
    fun testIASReportWithManifest() {
        val request = HttpPost("http://localhost:$httpPort/ias/report")
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val requestMessage = ReportRequest(
            isvEnclaveQuote = quote,
            pseManifest = byteArrayOf(0x73, 0x42),
            nonce = "0000000000000000"
        )
        request.entity = StringEntity(mapper.writeValueAsString(requestMessage), ContentType.APPLICATION_JSON)
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val responseMessage = mapper.readValue(response, ReportProxyResponse::class.java)
        assertTrue(responseMessage.signature.isNotEmpty())
        assertTrue(responseMessage.certificatePath.isNotEmpty())

        val iasReport = mapper.readValue(responseMessage.report.inputStream(), ReportResponse::class.java)
        assertEquals(QuoteStatus.OK, iasReport.isvEnclaveQuoteStatus)
        assertEquals("0000000000000000", iasReport.nonce)
        assertEquals(ManifestStatus.OK, iasReport.pseManifestStatus)
    }

    @Test
    fun testIASReportWithoutManifest() {
        val request = HttpPost("http://localhost:$httpPort/ias/report")
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val requestMessage = ReportRequest(
            isvEnclaveQuote = quote,
            nonce = "0000000000000000"
        )
        request.entity = StringEntity(mapper.writeValueAsString(requestMessage), ContentType.APPLICATION_JSON)
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val responseMessage = mapper.readValue(response, ReportProxyResponse::class.java)
        assertTrue(responseMessage.signature.isNotEmpty())
        assertTrue(responseMessage.certificatePath.isNotEmpty())

        val iasReport = mapper.readValue(responseMessage.report.inputStream(), ReportResponse::class.java)
        assertEquals(QuoteStatus.OK, iasReport.isvEnclaveQuoteStatus)
        assertEquals("0000000000000000", iasReport.nonce)
        assertNull(iasReport.pseManifestStatus)
    }
}