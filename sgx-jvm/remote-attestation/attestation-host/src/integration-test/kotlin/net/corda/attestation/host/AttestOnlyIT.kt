package net.corda.attestation.host

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.Crypto
import net.corda.attestation.CryptoProvider
import net.corda.attestation.message.AttestationError
import net.corda.attestation.message.AttestationRequest
import net.corda.attestation.readValue
import net.corda.attestation.toLittleEndian
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.HttpStatus.*
import org.apache.http.client.CookieStore
import org.apache.http.client.config.CookieSpecs.STANDARD_STRICT
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import org.junit.*
import org.junit.Assert.*
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyPair
import java.security.interfaces.ECPublicKey

class AttestOnlyIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
    }
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var cookies: CookieStore
    private lateinit var keyPair: KeyPair
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Before
    fun setup() {
        crypto = cryptoProvider.crypto
        mapper = ObjectMapper().registerModule(JavaTimeModule())
        cookies = BasicCookieStore()
        keyPair = crypto.generateKeyPair()

        val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setCookieSpec(STANDARD_STRICT)
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
    fun `attest without payload`() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val httpRequest = HttpPost("http://localhost:$httpPort/host/attest").apply {
            addHeader(BasicHeader(CONTENT_TYPE, APPLICATION_JSON.mimeType))
        }
        val responseBody = httpClient.execute(httpRequest, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_BAD_REQUEST, response.statusLine.statusCode)
            output
        }

        val error: AttestationError = mapper.readValue(responseBody)
        assertEquals("Message is missing", error.message)
    }

    @Test
    fun `test attest requires challenge first`() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val attestRequest = AttestationRequest(
            gb = (keyPair.public as ECPublicKey).toLittleEndian(),
            signatureGbGa = byteArrayOf(),
            aesCMAC = byteArrayOf()
        )
        val httpRequest = HttpPost("http://localhost:$httpPort/host/attest").apply {
            entity = StringEntity(mapper.writeValueAsString(attestRequest), APPLICATION_JSON)
        }
        val responseBody = httpClient.execute(httpRequest, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_UNAUTHORIZED, response.statusLine.statusCode)
            output
        }

        val error: AttestationError = mapper.readValue(responseBody)
        assertEquals("No response from our challenge", error.message)
    }
}