package net.corda.attestation.host

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.*
import net.corda.attestation.message.AttestationRequest
import net.corda.attestation.message.ChallengeRequest
import net.corda.attestation.message.ChallengeResponse
import net.corda.attestation.message.SecretRequest
import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.CookieStore
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
import org.apache.http.util.EntityUtils
import org.junit.*
import org.junit.Assert.*
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec

class SecretIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
        private const val AES_CMAC_FUNC = 1.toShort()
    }
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var ecParameters: ECParameterSpec
    private lateinit var keyFactory: KeyFactory
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto
    private lateinit var cookies: CookieStore
    private lateinit var transientKeyPair: KeyPair
    private lateinit var challengerKeyPair: KeyPair
    private lateinit var peerPublicKey: ECPublicKey
    private lateinit var smk: ByteArray

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Rule
    @JvmField
    val signatureProvider = SignatureProvider(cryptoProvider)

    @Before
    fun setup() {
        mapper = ObjectMapper()
        cookies = BasicCookieStore()
        crypto = cryptoProvider.crypto
        keyFactory = KeyFactory.getInstance("EC")
        transientKeyPair = crypto.generateKeyPair()
        ecParameters = (transientKeyPair.public as ECPublicKey).params
        challengerKeyPair = crypto.generateKeyPair()

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

        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val challengeRequest = ChallengeRequest((challengerKeyPair.public as ECPublicKey).toLittleEndian(), "nonce-value")
        var httpRequest = HttpPost("http://localhost:$httpPort/host/challenge").apply {
            entity = StringEntity(mapper.writeValueAsString(challengeRequest), APPLICATION_JSON)
        }
        val response = httpClient.execute(httpRequest, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
        val challengeResponse = mapper.readValue<ChallengeResponse>(response)

        val keyFactory = KeyFactory.getInstance("EC")
        peerPublicKey = keyFactory.generatePublic(challengeResponse.ga.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        smk = crypto.generateSMK(transientKeyPair.private, peerPublicKey)

        val gb = (transientKeyPair.public as ECPublicKey).toLittleEndian()
        val signatureGbGa = signatureProvider.signatureOf(challengerKeyPair.private, transientKeyPair.public as ECPublicKey, peerPublicKey)
        val attestRequest = AttestationRequest(
            gb = gb,
            signatureGbGa = signatureGbGa,
            aesCMAC = crypto.aesCMAC(smk, { aes ->
                aes.update(gb)
                aes.update(challengeResponse.spid.hexToBytes())
                aes.update(challengeResponse.quoteType.toLittleEndian())
                aes.update(AES_CMAC_FUNC.toLittleEndian())
                aes.update(signatureGbGa)
            })
        )
        httpRequest = HttpPost("http://localhost:$httpPort/host/attest").apply {
            entity = StringEntity(mapper.writeValueAsString(attestRequest), APPLICATION_JSON)
        }
        httpClient.execute(httpRequest, context).use { httpResponse ->
            val output = EntityUtils.toString(httpResponse.entity, UTF_8)
            assertEquals(output, SC_OK, httpResponse.statusLine.statusCode)
            output
        }
    }

    @After
    fun done() {
        httpClient.close()
    }

    @Test
    fun postSecret() {
        val secretKey = crypto.generateSecretKey(transientKeyPair.private, peerPublicKey)
        val secretIV = crypto.createIV()
        val secretData = crypto.encrypt("And now for something completely different!".toByteArray(), secretKey, secretIV)

        val platformInfo: ByteArray? = null
        val mk = crypto.generateMK(transientKeyPair.private, peerPublicKey)
        val secretRequest = SecretRequest(
            platformInfo = platformInfo,
            aesCMAC = crypto.aesCMAC(mk, { aes ->
                aes.update(platformInfo)
            }),
            data = secretData.encryptedData(),
            authTag = secretData.authenticationTag(),
            iv = secretIV
        )
        val request = HttpPost("http://localhost:$httpPort/host/secret").apply {
            entity = StringEntity(mapper.writeValueAsString(secretRequest), APPLICATION_JSON)
        }
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }
        httpClient.execute(request, context).use { response ->
            val output = EntityUtils.toString(response.entity)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
    }
}