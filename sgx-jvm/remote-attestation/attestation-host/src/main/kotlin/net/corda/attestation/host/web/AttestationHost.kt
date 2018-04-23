package net.corda.attestation.host.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.*
import net.corda.attestation.host.sgx.AttestationEnclave
import net.corda.attestation.host.sgx.ChallengerDetails
import net.corda.attestation.host.sgx.bridge.NativeAttestationEnclave
import net.corda.attestation.host.sgx.enclave.ECKey
import net.corda.attestation.host.sgx.enclave.SgxException
import net.corda.attestation.host.sgx.enclave.SgxStatus
import net.corda.attestation.host.sgx.entities.AttestationResult
import net.corda.attestation.host.sgx.entities.QuoteType
import net.corda.attestation.host.sgx.system.value
import net.corda.attestation.message.*
import org.apache.http.HttpResponse
import org.apache.http.client.CookieStore
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.nio.file.Paths
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.util.*
import java.util.concurrent.ExecutorService
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse.*
import javax.servlet.http.HttpSession
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/host")
class AttestationHost {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(AttestationHost::class.java)

        private const val AES_CMAC_FUNC = 1.toShort()
        private const val maxNonceLength = 32
        private const val enclaveAttr = "Enclave"
        private const val challengerKeyAttr = "Challenger-Key"
        private const val challengerNonceAttr = "Challenger-Nonce"
        private const val challengeResponseAttr = "Challenge-Response"
        private const val platformGIDAttr = "Platform-GroupID"
        private const val conversationAttr = "Conversation-Cookies"
        private val isvHost: URI = URI.create("http://${System.getProperty("isv.host", "localhost:8080")}")
        private val enclavePath = Paths.get(System.getProperty("corda.sgx.enclave.path", "."))
                                      .resolve("corda_sgx_ra_enclave.so")

        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
        private val keyFactory: KeyFactory = KeyFactory.getInstance("EC")
        private val ecParameters: ECParameterSpec
        private val crypto = Crypto()

        private val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        private val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()

        init {
            ecParameters = (crypto.generateKeyPair().public as ECPublicKey).params
            log.info("Elliptic Curve Parameters: {}", ecParameters)
        }
    }

    @field:Context
    private lateinit var httpRequest: HttpServletRequest

    @field:Context
    private lateinit var servletContext: ServletContext

    private val executor: ExecutorService by lazy {
        servletContext.getAttribute(ThreadPoolListener.threadPoolAttr) as ExecutorService
    }

    @POST
    @Path("/challenge")
    fun provision(challenge: ChallengeRequest?, @Suspended async: AsyncResponse) {
        if (challenge == null) {
            throw BadRequestException(responseOf("Message is missing", SC_BAD_REQUEST))
        }
        val session = httpRequest.session
        log.info("Challenge - HTTP Session: {}", session.id)

        validateNonce(challenge.nonce)

        val challengerPublicKey = try {
            keyFactory.generatePublic(challenge.gc.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        } catch (e: IllegalArgumentException) {
            throw BadRequestException(responseOf(e.message ?: "", SC_BAD_REQUEST))
        }
        session.setAttribute(challengerKeyAttr, challengerPublicKey)
        session.setAttribute(challengerNonceAttr, challenge.nonce)

        val enclave = NativeAttestationEnclave(enclavePath).apply {
            activate()
            initializeKeyExchange(ECKey.fromBytes(challenge.gc))
        }
        session.setAttribute(enclaveAttr, enclave)

        // Remember the HTTP session ID so that we can maintain a conversation
        // across multiple requests between three HTTP servers.
        val cookies = BasicCookieStore()
        session.setAttribute(conversationAttr, cookies)

        executor.submit {
            val context = HttpClientContext.create().apply {
                cookieStore = cookies
            }

            // Request basic service information from ISV. This data doesn't change.
            val serviceResponse: ServiceResponse = try {
                createHttpClient().use { client ->
                    val serviceURI = UriBuilder.fromUri(isvHost)
                        .path("/isv/service")
                        .build()
                    log.info("Invoking ISV: {}", serviceURI)

                    val httpRequest = HttpGet(serviceURI)
                    client.execute(httpRequest, context).use { httpResponse ->
                        val statusCode = httpResponse.statusLine.statusCode
                        if (statusCode != SC_OK) {
                            async.resume(httpResponse.toResponse("Error from ISV Host (HTTP $statusCode)"))
                            return@submit
                        }
                        mapper.readValue(httpResponse.entity.content)
                    }
                }
            } catch (e: IOException) {
                log.error("HTTP client error", e)
                async.resume(responseOf("HTTP connection failed: ${e.message}", SC_FORBIDDEN))
                return@submit
            }

            val (enclaveKey, platformGID) = enclave.getPublicKeyAndGroupIdentifier()
            session.setAttribute(platformGIDAttr, platformGID)

            val challengeResponse = ChallengeResponse(
                ga = enclaveKey.bytes,
                spid = serviceResponse.spid,
                quoteType = serviceResponse.quoteType
            )
            session.setAttribute(challengeResponseAttr, challengeResponse)
            async.resume(Response.ok(challengeResponse).build())
        }
    }

    @POST
    @Path("/attest")
    fun attestation(attestation: AttestationRequest?, @Suspended async: AsyncResponse) {
        if (attestation == null) {
            throw BadRequestException(responseOf("Message is missing", SC_BAD_REQUEST))
        }
        val session = httpRequest.session
        log.info("Attestation - HTTP Session: {}", session.id)

        val challengeResponse: ChallengeResponse = session.requireAttribute(challengeResponseAttr, "No response from our challenge")
        val challengeNonce: String = session.requireAttribute(challengerNonceAttr, "Challenger's nonce unavailable")
        val platformGID = session.requireAttribute<Int>(platformGIDAttr, "Platform GID unavailable").value()
        val cookies: CookieStore = session.requireAttribute(conversationAttr, "No existing HTTP session with ISV")
        val enclave: AttestationEnclave = session.requireAttribute(enclaveAttr, "Enclave unavailable")

        executor.submit {
            val context = HttpClientContext.create().apply {
                cookieStore = cookies
            }

            log.info("Platform GID: '{}'", platformGID)

            val iasReportBody: ByteArray = createHttpClient().use { client ->
                /*
                 * First fetch the signature revocation list from the IAS Proxy.
                 */
                val revocationList = try {
                    val sigRlURI = UriBuilder.fromUri(isvHost)
                        .path("/isv/sigrl/{gid}")
                        .build(platformGID)
                    log.info("Invoking ISV: {}", sigRlURI)

                    val getSigRL = HttpGet(sigRlURI)
                    client.execute(getSigRL, context).use { response ->
                        val statusCode = response.statusLine.statusCode
                        if (statusCode != SC_OK) {
                            async.resume(response.toResponse("Error from ISV Host (HTTP $statusCode)"))
                            return@submit
                        }
                        EntityUtils.toByteArray(response.entity).decodeBase64()
                    }
                } catch (e: IOException) {
                    log.error("HTTP client error", e)
                    async.resume(responseOf("HTTP client error: ${e.message}"))
                    return@submit
                }

                log.info("Fetched revocation list from IAS")

                /*
                 * Now tell the enclave to generate the quote.
                 */
                val quote = try {
                    val challengerDetails = ChallengerDetails(
                        publicKey = ECKey.fromBytes(attestation.gb),
                        serviceProviderIdentifier = challengeResponse.spid.hexToBytes(),
                        quoteType = QuoteType.forValue(challengeResponse.quoteType),
                        keyDerivationFunctionIdentifier = AES_CMAC_FUNC,
                        signature = attestation.signatureGbGa,
                        messageAuthenticationCode = attestation.aesCMAC,
                        signatureRevocationList = revocationList
                    )
                    enclave.processChallengerDetailsAndGenerateQuote(challengerDetails)
                } catch (e: Exception) {
                    log.error("Attestation error", e)
                    async.resume(responseOf("Attestation error: ${e.message}"))
                    return@submit
                }

                /*
                 * Now pass the quote to the IAS Proxy for validation.
                 */
                val reportBody = try {
                    val reportURI = UriBuilder.fromUri(isvHost)
                        .path("/isv/report")
                        .build()
                    log.info("Invoking ISV: {}", reportURI)

                    val reportRequest = ReportRequest(
                        isvEnclaveQuote = quote.payload,
                        pseManifest = quote.securityProperties,
                        nonce = challengeNonce
                    )
                    val httpRequest = HttpPost(reportURI).apply {
                        entity = StringEntity(mapper.writeValueAsString(reportRequest), APPLICATION_JSON)
                    }
                    client.execute(httpRequest, context).use { httpResponse ->
                        val statusCode = httpResponse.statusLine.statusCode
                        if (statusCode != SC_OK) {
                            async.resume(httpResponse.toResponse("Error from ISV Host (HTTP $statusCode)"))
                            return@submit
                        }
                        EntityUtils.toByteArray(httpResponse.entity)
                    }
                } catch (e: IOException) {
                    log.error("HTTP client error", e)
                    async.resume(responseOf("HTTP client error: ${e.message}"))
                    return@submit
                }

                // Return this to the challenger for validation.
                reportBody
            }

            log.info("Received report from IAS")

            // Successful response.
            async.resume(Response.ok(iasReportBody).build())
        }
    }

    @POST
    @Path("/secret")
    fun secret(secret: SecretRequest?): Response {
        if (secret == null) {
            throw BadRequestException(responseOf("Message is missing", SC_BAD_REQUEST))
        }

        val session = httpRequest.session
        log.info("Secret - HTTP Session: {}", session.id)
        log.info("platformInfo: {}", secret.platformInfo)
        log.info("aesCMAC: {}", secret.aesCMAC)
        log.info("secret: {}", secret.data)

        val enclave: AttestationEnclave = session.requireAttribute(enclaveAttr, "Enclave unavailable")
        val attestationResult = AttestationResult(
            attestationResultMessage = secret.platformInfo,
            aesCMAC = secret.aesCMAC,
            secret = secret.data,
            secretHash = secret.authTag,
            secretIV = secret.iv
        )
        val (cmacStatus, sealedSecret) = try {
            enclave.verifyAttestationResponse(attestationResult)
        } catch (e: SgxException) {
            log.error("SGX enclave error", e)
            return responseOf("Failed to validate request", SC_BAD_REQUEST)
        }

        if (cmacStatus != SgxStatus.SUCCESS) {
            log.error("CMAC validation failed: $cmacStatus")
            return responseOf("Invalid CMAC status '$cmacStatus'", SC_BAD_REQUEST)
        }

        // Successful response.
        log.info("Sealed secret size: ${sealedSecret.size}")
        return Response.ok().build()
    }

    private fun validateNonce(n: String?) {
        val nonce = n ?: return
        if (nonce.length > maxNonceLength) {
            throw BadRequestException(responseOf("Nonce is too large: maximum $maxNonceLength digits", SC_BAD_REQUEST))
        }
    }

    private fun responseOf(message: String, statusCode: Int = SC_INTERNAL_SERVER_ERROR): Response = Response.status(statusCode)
        .entity(AttestationError(message))
        .build()

    private fun HttpResponse.toResponse(message: String, statusCode: Int = statusLine.statusCode): Response {
        return Response.status(statusCode)
            .entity(AttestationError(message))
            .build()
    }

    private fun createHttpClient(): CloseableHttpClient {
        return HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager().apply {
                socketConfig = httpSocketConfig
            })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    private fun ByteArray.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
    private inline fun <reified T : Any> HttpSession.requireAttribute(attrName: String, errorMessage: String): T
        = getAttribute(attrName) as? T ?: throw NotAuthorizedException(responseOf(errorMessage, SC_UNAUTHORIZED))
}
