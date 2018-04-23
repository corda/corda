package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.ChallengeResponse
import org.apache.http.HttpStatus.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GetProvisioningIT {
    private val httpPort = Integer.getInteger("test.isv.httpPort")
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var mapper: ObjectMapper

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Before
    fun setup() {
        mapper = ObjectMapper()
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
    fun getProvisioning() {
        val request = HttpGet("http://localhost:$httpPort/attest/provision")
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val challenge = mapper.readValue(response, ChallengeResponse::class.java)
        assertTrue(challenge.nonce.isNotEmpty())
    }
}