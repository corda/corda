package net.corda.attestation.host

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.*
import net.corda.attestation.message.AttestationError
import net.corda.attestation.message.ChallengeRequest
import net.corda.attestation.message.ChallengeResponse
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
import org.junit.Rule
import org.junit.Test
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec

class ChallengeIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
    }
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var ecParameters: ECParameterSpec
    private lateinit var keyFactory: KeyFactory
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Before
    fun setup() {
        mapper = ObjectMapper()
        crypto = cryptoProvider.crypto
        keyFactory = KeyFactory.getInstance("EC")
        ecParameters = (crypto.generateKeyPair().public as ECPublicKey).params
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
    fun challenge() {
        val keyPair = crypto.generateKeyPair()
        val challengeRequest = ChallengeRequest((keyPair.public as ECPublicKey).toLittleEndian(), "nonce-value")
        val request = HttpPost("http://localhost:$httpPort/host/challenge").apply {
            entity = StringEntity(mapper.writeValueAsString(challengeRequest), APPLICATION_JSON)
        }
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val challengeResponse: ChallengeResponse = mapper.readValue(response)
        keyFactory.generatePublic(challengeResponse.ga.toBigEndianKeySpec(ecParameters))
    }


    @Test
    fun testHugeNonce() {
        val keyPair = crypto.generateKeyPair()
        val challengeRequest = ChallengeRequest(
            gc = (keyPair.public as ECPublicKey).toLittleEndian(),
            nonce = "1234567890123456789012345678901234"
        )
        val httpRequest = HttpPost("http://localhost:$httpPort/host/challenge").apply {
            entity = StringEntity(mapper.writeValueAsString(challengeRequest), APPLICATION_JSON)
        }
        val responseBody = httpClient.execute(httpRequest).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_BAD_REQUEST, response.statusLine.statusCode)
            output
        }

        val error: AttestationError = mapper.readValue(responseBody)
        assertEquals("Nonce is too large: maximum 32 digits", error.message)
    }
}
