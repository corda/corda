package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.AttestationError
import net.corda.attestation.message.Message3
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.HttpStatus.*
import org.apache.http.client.CookieStore
import org.apache.http.client.config.CookieSpecs.STANDARD_STRICT
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyPair
import java.security.interfaces.ECPublicKey

class PostMessage3OnlyIT {
    private val httpPort = Integer.getInteger("test.isv.httpPort")
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
        mapper = ObjectMapper().registerModule(JavaTimeModule())
        crypto = Crypto()
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
    fun `test msg3 without payload`() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val request3 = HttpPost("http://localhost:$httpPort/attest/msg3")
        request3.addHeader(BasicHeader(CONTENT_TYPE, APPLICATION_JSON.mimeType))
        val response3 = httpClient.execute(request3, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_BAD_REQUEST, response.statusLine.statusCode)
            output
        }

        val error = mapper.readValue(response3, AttestationError::class.java)
        assertEquals("Message is missing", error.message)
    }

    @Test
    fun `test Msg3 requires Msg1 first`() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val request3 = HttpPost("http://localhost:$httpPort/attest/msg3")
        val msg3 = Message3(
            aesCMAC = byteArrayOf(),
            ga = (keyPair.public as ECPublicKey).toLittleEndian(),
            securityManifest = byteArrayOf(),
            quote = byteArrayOf()
        )
        request3.entity = StringEntity(mapper.writeValueAsString(msg3), APPLICATION_JSON)
        val response3 = httpClient.execute(request3, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_UNAUTHORIZED, response.statusLine.statusCode)
            output
        }

        val error = mapper.readValue(response3, AttestationError::class.java)
        assertEquals("Secret key has not been calculated yet", error.message)
    }
}