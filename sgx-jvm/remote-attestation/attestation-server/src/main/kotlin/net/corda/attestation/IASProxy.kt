package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.AttestationError
import net.corda.attestation.message.ias.ReportProxyResponse
import net.corda.attestation.message.ias.ReportRequest
import net.corda.attestation.message.ias.RevocationListProxyResponse
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.security.KeyStore
import java.security.SecureRandom
import java.util.*
import javax.net.ssl.SSLException
import javax.servlet.http.HttpServletResponse.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/ias")
class IASProxy {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(IASProxy::class.java)
        @JvmStatic
        private val SPID = "84D402C36BA9EF9B0A86EF1A9CC8CE4F"

        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
        private val random = SecureRandom()

        private val storePassword = (System.getProperty("javax.net.ssl.keyStorePassword") ?: "").toCharArray()
        private val keyStore: KeyStore
        private val iasHost: URI = URI.create("https://${System.getProperty("ias.host", "localhost:8443")}")
        private val isDummy = iasHost.host == "localhost"
        private val isvKeyAlias = if (isDummy) "jetty" else "isv"

        private val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        private val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()

        init {
            val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", "PKCS12")
            keyStore = loadKeyStore("javax.net.ssl.keyStore", storePassword, keyStoreType)
        }

        private fun loadKeyStore(propertyName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
            val fileName = System.getProperty(propertyName) ?: throw IllegalStateException("System property $propertyName not set")
            return KeyStore.getInstance(type).apply {
                FileInputStream(fileName).use { input -> this.load(input, password) }
            }
        }
    }

    @GET
    @Path("/sigrl/{gid}")
    fun proxyRevocationList(@PathParam("gid") platformGID: String): Response {
        val revocationList = try {
            createHttpClient().use { client ->
                val sigRlURI = UriBuilder.fromUri(iasHost)
                    .path("attestation/sgx/v2/sigrl/{gid}")
                    .build(platformGID)
                val getSigRL = HttpGet(sigRlURI)
                client.execute(getSigRL).use { response ->
                    if (response.statusLine.statusCode != SC_OK) {
                        return response.toResponse("Error from Intel Attestation Service (HTTP ${response.statusLine.statusCode})")
                    }
                    EntityUtils.toByteArray(response.entity).decodeBase64()
                }
            }
        } catch (e: SSLException) {
            log.error("HTTPS error: ${e.message}")
            return responseOf("HTTPS connection failed: ${e.message}", SC_FORBIDDEN)
        } catch (e: IOException) {
            log.error("HTTP client error", e)
            return responseOf("HTTP client error: ${e.message}")
        }

        return Response.ok()
            .entity(RevocationListProxyResponse(SPID, revocationList))
            .build()
    }

    @POST
    @Path("/report")
    fun proxyReport(prq: ReportRequest?): Response {
        val reportRequest = prq ?: return responseOf("Message is missing", SC_BAD_REQUEST)

        val reportResponse = try {
            createHttpClient().use { client ->
                val reportURI = UriBuilder.fromUri(iasHost)
                    .path("attestation/sgx/v2/report")
                    .build()
                val httpRequest = HttpPost(reportURI)
                httpRequest.entity = StringEntity(mapper.writeValueAsString(reportRequest), ContentType.APPLICATION_JSON)
                client.execute(httpRequest).use { httpResponse ->
                    if (httpResponse.statusLine.statusCode != SC_OK) {
                        return httpResponse.toResponse("Error from Intel Attestation Service")
                    }
                    ReportProxyResponse(
                        signature = httpResponse.requireHeader("X-IASReport-Signature"),
                        certificatePath = httpResponse.requireHeader("X-IASReport-Signing-Certificate").decodeURL(),
                        report = EntityUtils.toByteArray(httpResponse.entity)
                    )
                }
            }
        } catch (e: SSLException) {
            log.error("HTTPS error: ${e.message}")
            return responseOf("HTTPS connection failed: ${e.message}", SC_FORBIDDEN)
        } catch (e: IOException) {
            log.error("HTTP client error", e)
            return responseOf("HTTP client error: ${e.message}")
        }
        return Response.ok()
            .entity(reportResponse)
            .build()
    }

    private fun responseOf(message: String, statusCode: Int = SC_INTERNAL_SERVER_ERROR): Response = Response.status(statusCode)
        .entity(AttestationError(message))
        .build()

    private fun HttpResponse.toResponse(message: String, statusCode: Int = statusLine.statusCode): Response {
        return Response.status(statusCode)
            .entity(AttestationError(message))
            .apply {
                val requestIdHeader = getFirstHeader("Request-ID") ?: return@apply
                this.header(requestIdHeader.name, requestIdHeader.value)
            }
            .build()
    }

    private fun HttpResponse.requireHeader(name: String): String
        = (this.getFirstHeader(name) ?: throw ForbiddenException(toResponse("Response header '$name' missing", SC_FORBIDDEN))).value

    private fun createHttpClient(): CloseableHttpClient {
        val sslContext = SSLContextBuilder()
            .loadKeyMaterial(keyStore, storePassword, { _, _ -> isvKeyAlias })
            .setSecureRandom(random)
            .build()
        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("https", SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
            .build()
        return HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager(registry).apply {
                socketConfig = httpSocketConfig
            })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    private fun ByteArray.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
    private fun String.decodeURL(): String = URLDecoder.decode(this, "UTF-8")
}
