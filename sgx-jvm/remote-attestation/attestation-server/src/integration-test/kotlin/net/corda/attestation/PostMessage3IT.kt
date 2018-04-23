package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.*
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
import org.apache.http.util.EntityUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets.*
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec

class PostMessage3IT {
    private val httpPort = Integer.getInteger("test.isv.httpPort")
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var cookies: CookieStore
    private lateinit var keyPair: KeyPair
    private lateinit var ecParameters: ECParameterSpec
    private lateinit var msg2: Message2
    private lateinit var peerPublicKey: ECPublicKey
    private lateinit var smk: ByteArray
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Before
    fun setup() {
        crypto = Crypto()
        cookies = BasicCookieStore()
        keyPair = crypto.generateKeyPair()
        ecParameters = (keyPair.public as ECPublicKey).params
        mapper = ObjectMapper().registerModule(JavaTimeModule())

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

        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val request = HttpPost("http://localhost:$httpPort/attest/msg1")
        val msg1 = Message1(ga = (keyPair.public as ECPublicKey).toLittleEndian(), platformGID = "00000000")
        request.entity = StringEntity(mapper.writeValueAsString(msg1), APPLICATION_JSON)
        val response = httpClient.execute(request, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
        msg2 = mapper.readValue(response, Message2::class.java)

        val keyFactory = KeyFactory.getInstance("EC")
        peerPublicKey = keyFactory.generatePublic(msg2.gb.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        smk = crypto.generateSMK(keyPair.private, peerPublicKey)
    }

    @After
    fun done() {
        httpClient.close()
    }

    @Test
    fun postMsg3() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val request3 = HttpPost("http://localhost:$httpPort/attest/msg3")
        val ga = (keyPair.public as ECPublicKey).toLittleEndian()
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val msg3 = Message3(
            aesCMAC = crypto.aesCMAC(smk, { aes ->
                aes.update(ga)
                aes.update(quote)
            }),
            ga = ga,
            quote = quote
        )
        request3.entity = StringEntity(mapper.writeValueAsString(msg3), APPLICATION_JSON)
        val response3 = httpClient.execute(request3, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
        val msg4 = mapper.readValue(response3, Message4::class.java)
        assertEquals("OK", msg4.quoteStatus)
        assertArrayEquals(quote, msg4.quoteBody)
        assertArrayEquals(unsignedByteArrayOf(0x11, 0x22), msg4.platformInfo)
        assertNull(msg4.securityManifestStatus)

        val cmac = crypto.aesCMAC(crypto.generateMK(keyPair.private, peerPublicKey), { aes ->
            aes.update(msg4.platformInfo ?: byteArrayOf())
        })
        assertArrayEquals(cmac, msg4.aesCMAC)

        val secretKey = crypto.generateSecretKey(keyPair.private, peerPublicKey)
        val secret = String(crypto.decrypt(msg4.secret.plus(msg4.secretHash), secretKey, msg4.secretIV), UTF_8)
        assertEquals("And now for something completely different!", secret)
    }

    @Test
    fun testHugeNonce() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val request3 = HttpPost("http://localhost:$httpPort/attest/msg3")
        val ga = (keyPair.public as ECPublicKey).toLittleEndian()
        val quote = byteArrayOf(0x02, 0x04, 0x08, 0x10)
        val msg3 = Message3(
            aesCMAC = crypto.aesCMAC(smk, { aes ->
                aes.update(ga)
                aes.update(quote)
            }),
            ga = ga,
            quote = quote,
            nonce = "1234567890123456789012345678901234"
        )
        request3.entity = StringEntity(mapper.writeValueAsString(msg3), APPLICATION_JSON)
        val response3 = httpClient.execute(request3, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_BAD_REQUEST, response.statusLine.statusCode)
            output
        }

        val error = mapper.readValue(response3, AttestationError::class.java)
        assertEquals("Nonce is too large: maximum 32 digits", error.message)
    }

    private fun unsignedByteArrayOf(vararg values: Int) = ByteArray(values.size).apply {
        for (i in 0 until values.size) {
            this[i] = values[i].toByte()
        }
    }
}