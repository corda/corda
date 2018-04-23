package net.corda.attestation

import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.util.*
import javax.ws.rs.core.UriBuilder

@Ignore("This class exists only to probe IAS, and is not really a test at all.")
class IASIT {
    private val iasHost: URI = URI.create("https://test-as.sgx.trustedservices.intel.com")
    private val storePassword = (System.getProperty("javax.net.ssl.keyStorePassword") ?: "").toCharArray()
    private val random = SecureRandom()

    private lateinit var keyStore: KeyStore

    private val httpRequestConfig: RequestConfig = RequestConfig.custom()
        .setConnectTimeout(20_000)
        .setSocketTimeout(5_000)
        .build()
    private val httpSocketConfig: SocketConfig = SocketConfig.custom()
        .setSoReuseAddress(true)
        .setTcpNoDelay(true)
        .build()

    private fun createHttpClient(): CloseableHttpClient {
        val sslContext = SSLContextBuilder()
            .loadKeyMaterial(keyStore, storePassword, { _, _ -> "isv" })
            .setSecureRandom(random)
            .build()
        return HttpClients.custom()
            .setDefaultRequestConfig(httpRequestConfig)
            .setDefaultSocketConfig(httpSocketConfig)
            .setSSLContext(sslContext)
            .build()
    }

    private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
        return KeyStore.getInstance(type).apply {
            RemoteAttestation::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
                load(input, password)
            }
        }
    }

    @Before
    fun setup() {
        keyStore = loadKeyStoreResource("isv.pfx", storePassword)
    }

    @Test
    fun testGID() {
        createHttpClient().use { httpClient ->
            val requestURI = UriBuilder.fromUri(iasHost)
                .path("attestation/sgx/v2/sigrl/{gid}")
                .build(String.format("%08x", 0xacc))
            println("URI: $requestURI")
            val request = HttpGet(requestURI)
            httpClient.execute(request).use { response ->
                val statusCode = response.statusLine.statusCode
                if (statusCode == SC_OK) {
                    val revocationList = EntityUtils.toByteArray(response.entity).decodeBase64()
                    println(revocationList.toHexArrayString())
                } else {
                    println("NOPE: $statusCode")
                }
            }
        }
    }

    @Test
    fun huntGID() {
        createHttpClient().use { httpClient ->
            for (i in 1000..1999) {
                val requestURI = UriBuilder.fromUri(iasHost)
                    .path("attestation/sgx/v2/sigrl/{gid}")
                    .build(String.format("%16x", i))
                val request = HttpGet(requestURI)
                httpClient.execute(request).use { response ->
                    if (response.statusLine.statusCode == SC_OK) {
                        println("FOUND: $i")
                    } else {
                        println("NO: $i -> ${response.statusLine.statusCode}")
                    }
                }
            }
        }
    }

    private fun ByteArray.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
}