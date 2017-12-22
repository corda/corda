package net.corda.attestation.ias

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
import org.junit.Test
import java.nio.charset.StandardCharsets.*
import java.util.*

class RevocationListProxyIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
    }
    private lateinit var httpClient: CloseableHttpClient

    @Before
    fun setup() {
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
    fun testIASRevocationList() {
        val request = HttpGet("http://localhost:$httpPort/isv/sigrl/00000000")
        val response = httpClient.execute(request).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }

        val revocationList = Base64.getDecoder().decode(response)
        assertTrue(revocationList.isNotEmpty())
    }
}
