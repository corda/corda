package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.Message0
import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.*
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

class PostMessage0IT {
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
    fun postMsg0() {
        val request = HttpPost("http://localhost:$httpPort/attest/msg0")
        val msg0 = Message0(extendedGID = 0)
        request.entity = StringEntity(mapper.writeValueAsString(msg0), APPLICATION_JSON)
        httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
        }
    }
}