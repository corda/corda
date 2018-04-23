package net.corda.attestation.ias

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.ias.message.ReportProxyResponse
import net.corda.attestation.ias.message.ReportRequest
import net.corda.attestation.message.AttestationError
import net.corda.attestation.message.ServiceResponse
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
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLException
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletResponse.*
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/isv")
class IASProxy {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(IASProxy::class.java)
        private const val SPID = "84D402C36BA9EF9B0A86EF1A9CC8CE4F"
        private const val linkableQuote = 1.toShort()

        private const val isvKeyAlias = "isv"

        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
        private val random = SecureRandom()

        private val storePassword = (System.getProperty("net.corda.IsvKeyStorePassword") ?: "").toCharArray()
        private val keyStore: KeyStore
        private val iasHost: URI = URI.create("https://${System.getProperty("ias.host", "localhost:8443")}")

        private val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        private val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()

        init {
            keyStore = loadKeyStoreResource("isv.pfx", storePassword, "PKCS12")
        }

        private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
            return KeyStore.getInstance(type).apply {
                IASProxy::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
                    load(input, password)
                }
            }
        }
    }

    @field:Context
    private lateinit var servletContext: ServletContext

    private val executor: ExecutorService by lazy {
        servletContext.getAttribute(ThreadPoolListener.threadPoolAttr) as ExecutorService
    }

    @GET
    @Path("/service")
    fun serviceParameters(): Response {
        return Response.ok(ServiceResponse(SPID, linkableQuote)).build()
    }

    @GET
    @Path("/sigrl/{gid}")
    fun proxyRevocationList(@PathParam("gid") platformGID: String, @Suspended async: AsyncResponse) {
        log.info("Requesting revocation list for GID={}", platformGID)

        executor.submit {
            val revocationListBase64: ByteArray = try {
                createHttpClient().use { client ->
                    val sigRlURI = UriBuilder.fromUri(iasHost)
                        .path("attestation/sgx/v2/sigrl/{gid}")
                        .build(platformGID)
                    log.info("Invoking IAS: {}", sigRlURI)

                    val getSigRL = HttpGet(sigRlURI)
                    client.execute(getSigRL).use { response ->
                        val statusCode = response.statusLine.statusCode
                        if (statusCode != SC_OK) {
                            log.error("HTTP {} error from IAS", statusCode)
                            async.resume(response.toResponse("Error from Intel Attestation Service (HTTP $statusCode)"))
                            return@submit
                        }
                        EntityUtils.toByteArray(response.entity)
                    }
                }
            } catch (e: SSLException) {
                log.error("HTTPS error: ${e.message}")
                async.resume(responseOf("HTTPS connection failed: ${e.message}", SC_FORBIDDEN))
                return@submit
            } catch (e: IOException) {
                log.error("HTTP client error", e)
                async.resume(responseOf("HTTP client error: ${e.message}"))
                return@submit
            }

            // Successful response
            async.resume(Response.ok(revocationListBase64).build())
        }
    }

    @POST
    @Path("/report")
    fun proxyReport(reportRequest: ReportRequest?, @Suspended async: AsyncResponse) {
        if (reportRequest == null) {
            throw BadRequestException(responseOf("Message is missing", SC_BAD_REQUEST))
        }

        executor.submit {
            val reportResponse = try {
                createHttpClient().use { client ->
                    val reportURI = UriBuilder.fromUri(iasHost)
                        .path("attestation/sgx/v2/report")
                        .build()
                    log.info("Invoking IAS: {}", reportURI)

                    val httpRequest = HttpPost(reportURI)
                    httpRequest.entity = StringEntity(mapper.writeValueAsString(reportRequest), ContentType.APPLICATION_JSON)
                    client.execute(httpRequest).use { httpResponse ->
                        if (httpResponse.statusLine.statusCode != SC_OK) {
                            async.resume(httpResponse.toResponse("Error from Intel Attestation Service"))
                            return@submit
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
                async.resume(responseOf("HTTPS connection failed: ${e.message}", SC_FORBIDDEN))
                return@submit
            } catch (e: IOException) {
                log.error("HTTP client error", e)
                async.resume(responseOf("HTTP client error: ${e.message}"))
                return@submit
            }

            // Successful response
            async.resume(Response.ok(reportResponse).build())
        }
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

    private fun String.decodeURL(): String = URLDecoder.decode(this, "UTF-8")
}
