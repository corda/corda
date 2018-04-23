package net.corda.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.*
import net.corda.attestation.message.ias.ReportRequest
import net.corda.attestation.message.ias.ReportResponse
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory.getDefaultHostnameVerifier
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.cert.PKIXRevocationChecker.Option.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import java.util.*
import javax.crypto.SecretKey
import javax.net.ssl.SSLException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse.*
import javax.ws.rs.*
import javax.ws.rs.core.*

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/attest")
class RemoteAttestation {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(RemoteAttestation::class.java)
        @JvmStatic
        private val SPID = "84D402C36BA9EF9B0A86EF1A9CC8CE4F".hexToBytes()
        @JvmStatic
        private val LINKABLE_QUOTE = byteArrayOf(0x01, 0x00)

        private const val intelAES = 0
        private const val maxNonceLength = 32
        private const val tlvHeaderSize = 8
        private const val AES_CMAC_FUNC = 1
        private const val secretKeyAttrName = "Secret-Key"
        private const val transientKeyPairAttrName = "Transient-Key-Pair"
        private const val smkAttrName = "SMK"

        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
        private val crypto = Crypto()
        private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        private val keyFactory: KeyFactory = KeyFactory.getInstance("EC")
        private val storePassword = (System.getProperty("javax.net.ssl.keyStorePassword") ?: "").toCharArray()
        private val isvStore: KeyStore
        private val iasStore: KeyStore
        private val serviceKeyPair: KeyPair
        private val pkixParameters: PKIXParameters
        private val ecParameters: ECParameterSpec

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
            ecParameters = (crypto.generateKeyPair().public as ECPublicKey).params
            log.info("Elliptic Curve Parameters: {}", ecParameters)

            val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", "PKCS12")
            isvStore = loadKeyStore("javax.net.ssl.keyStore", storePassword, keyStoreType)
            serviceKeyPair = loadKeyStoreResource("isv-svc.pfx", storePassword).getKeyPair("isv-svc", storePassword)

            val iasResourceName = if (isDummy) "dummyIAStrust.pfx" else "ias.pfx"
            iasStore = loadKeyStoreResource(iasResourceName, storePassword)

            val revocationListOptions = if (isDummy) EnumSet.of(SOFT_FAIL, PREFER_CRLS, NO_FALLBACK) else EnumSet.of(SOFT_FAIL)
            pkixParameters = PKIXParameters(iasStore.trustAnchorsFor("ias")).apply {
                val rlChecker = CertPathValidator.getInstance("PKIX").revocationChecker as PKIXRevocationChecker
                addCertPathChecker(rlChecker.apply { options = revocationListOptions })
            }
        }

        private fun loadKeyStore(propertyName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
            val fileName = System.getProperty(propertyName) ?: throw IllegalStateException("System property $propertyName not set")
            return KeyStore.getInstance(type).apply {
                FileInputStream(fileName).use { input -> this.load(input, password) }
            }
        }

        private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
            return KeyStore.getInstance(type).apply {
                RemoteAttestation::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
                    load(input, password)
                }
            }
        }

        private fun KeyStore.getKeyPair(alias: String, password: CharArray): KeyPair {
            val privateKey = getKey(alias, password) as PrivateKey
            return KeyPair(getCertificate(alias).publicKey, privateKey)
        }

        private fun KeyStore.trustAnchorsFor(vararg aliases: String): Set<TrustAnchor>
            = aliases.map { alias -> TrustAnchor(getCertificate(alias) as X509Certificate, null) }.toSet()
    }

    @field:Context
    private lateinit var httpRequest: HttpServletRequest

    @GET
    @Path("/provision")
    fun provision(): Response {
        log.info("Provisioning requested")
        return Response.ok()
                .entity(ChallengeResponse(createNonce(), (serviceKeyPair.public as ECPublicKey).toLittleEndian()))
                .build()
    }

    @POST
    @Path("/msg0")
    fun receiveMsg0(m: Message0?): Response {
        val msg0 = m ?: return responseOf("Message is missing", SC_BAD_REQUEST)
        if (intelAES != msg0.extendedGID) {
            return responseOf("Unsupported extended GID value", SC_BAD_REQUEST)
        }
        log.info("Message0 processed")
        return Response.ok().build()
    }

    /**
     * Receives Msg1 and returns Msg2.
     */
    @POST
    @Path("/msg1")
    fun handleMsg1(m: Message1?): Response {
        val msg1 = m ?: return responseOf("Message is missing", SC_BAD_REQUEST)
        val session = httpRequest.session ?: return responseOf("No session in progress", SC_UNAUTHORIZED)

        log.info("HTTP Session: {}", session.id)

        val revocationList = try {
            createHttpClient().use { client ->
                val sigRlURI = UriBuilder.fromUri(iasHost)
                    .path("attestation/sgx/v2/sigrl/{gid}")
                    .build(msg1.platformGID)
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

        val transientKeyPair = crypto.generateKeyPair()
        session.setAttribute(transientKeyPairAttrName, transientKeyPair)

        val peerPublicKey = try {
            keyFactory.generatePublic(msg1.ga.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        } catch (e: IllegalArgumentException) {
            return responseOf(e.message ?: "", SC_BAD_REQUEST)
        }
        session.setAttribute(secretKeyAttrName, crypto.generateSecretKey(transientKeyPair.private, peerPublicKey))

        log.info("Message1 processed - returning Message2")

        val smk = crypto.generateSMK(transientKeyPair.private, peerPublicKey)
        session.setAttribute(smkAttrName, smk)

        val publicKey = transientKeyPair.public as ECPublicKey
        val signatureGbGa = signatureOf(publicKey, peerPublicKey)
        val gb = publicKey.toLittleEndian()
        val msg2 = Message2(
            gb = gb,
            spid = SPID.toHexString(),
            keyDerivationFuncId = AES_CMAC_FUNC,
            signatureGbGa = signatureGbGa,
            aesCMAC = crypto.aesCMAC(smk, { aes ->
                aes.update(gb)
                aes.update(SPID)
                aes.update(LINKABLE_QUOTE)
                aes.update(AES_CMAC_FUNC.toShort().toLittleEndian())
                aes.update(signatureGbGa)
            }),
            revocationList = revocationList
        )
        return Response.ok(msg2).build()
    }

    /**
     * Receives Msg3 and return Msg4.
     */
    @POST
    @Path("/msg3")
    fun handleMsg3(m: Message3?): Response {
        val msg3 = m ?: return responseOf("Message is missing", SC_BAD_REQUEST)
        val session = httpRequest.session
                ?: return responseOf("No session in progress", SC_UNAUTHORIZED)
        log.info("HTTP Session: {}", session.id)

        val secretKey = session.getAttribute(secretKeyAttrName) as? SecretKey
                ?: return responseOf("Secret key has not been calculated yet", SC_UNAUTHORIZED)
        val smk = session.getAttribute(smkAttrName) as? ByteArray
                ?: return responseOf("SMK value has not been calculated yet", SC_UNAUTHORIZED)
        validateCMAC(msg3, smk)

        val transientKeyPair = session.getAttribute(transientKeyPairAttrName) as? KeyPair
                ?: return responseOf("DH key unavailable", SC_UNAUTHORIZED)
        val peerPublicKey = try {
            keyFactory.generatePublic(msg3.ga.toBigEndianKeySpec(ecParameters))
        } catch (e: IllegalArgumentException) {
            return responseOf(e.message ?: "", SC_BAD_REQUEST)
        }
        if (crypto.generateSecretKey(transientKeyPair.private, peerPublicKey) != secretKey) {
            return responseOf("Keys do not match!", SC_FORBIDDEN)
        }
        validateNonce(msg3.nonce)

        log.debug("Quote: {}", msg3.quote.toHexArrayString())
        log.debug("Security manifest: {}", msg3.securityManifest?.toHexArrayString())
        log.debug("Nonce: {}", msg3.nonce)

        val report: ReportResponse = try {
            createHttpClient().use { client ->
                val reportURI = UriBuilder.fromUri(iasHost)
                    .path("attestation/sgx/v2/report")
                    .build()
                val httpRequest = HttpPost(reportURI)
                val reportRequest = ReportRequest(
                    isvEnclaveQuote = msg3.quote,
                    pseManifest = msg3.securityManifest?.ifNotZeros(),
                    nonce = msg3.nonce
                )
                httpRequest.entity = StringEntity(mapper.writeValueAsString(reportRequest), ContentType.APPLICATION_JSON)
                client.execute(httpRequest).use { httpResponse ->
                    if (httpResponse.statusLine.statusCode != SC_OK) {
                        return httpResponse.toResponse("Error from Intel Attestation Service (HTTP ${httpResponse.statusLine.statusCode})")
                    }
                    mapper.readValue(validate(httpResponse), ReportResponse::class.java)
                }
            }
        } catch (e: SSLException) {
            log.error("HTTPS error: ${e.message}")
            return responseOf("HTTPS connection failed: ${e.message}", SC_FORBIDDEN)
        } catch (e: IOException) {
            log.error("HTTP client error", e)
            return responseOf("HTTP client error: ${e.message}")
        }

        log.info("Report ID: {}", report.id)
        log.info("Quote Status: {}", report.isvEnclaveQuoteStatus)
        log.info("Message3 processed - returning Message4")

        val secretIV = crypto.createIV()
        val secretData = crypto.encrypt("And now for something completely different!".toByteArray(), secretKey, secretIV)
        val platformInfo = report.platformInfoBlob?.removeHeader(tlvHeaderSize) ?: byteArrayOf()
        val mk = crypto.generateMK(transientKeyPair.private, peerPublicKey)
        val msg4 = Message4(
            reportID = report.id,
            quoteStatus = report.isvEnclaveQuoteStatus.toString(),
            quoteBody = report.isvEnclaveQuoteBody,
            aesCMAC = crypto.aesCMAC(mk, { aes ->
                aes.update(platformInfo)
            }),
            securityManifestStatus = report.pseManifestStatus?.toString(),
            securityManifestHash = report.pseManifestHash,
            platformInfo = platformInfo,
            epidPseudonym = report.epidPseudonym,
            nonce = report.nonce,
            secret = secretData.encryptedData(),
            secretHash = secretData.authenticationTag(),
            secretIV = secretIV,
            timestamp = report.timestamp
        )
        return Response.ok(msg4)
            .build()
    }

    private fun createNonce(): String = UUID.randomUUID().let { uuid ->
        String.format("%016x%016x", uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    private fun validateNonce(n: String?) {
        val nonce = n ?: return
        if (nonce.length > maxNonceLength) {
            throw BadRequestException(responseOf("Nonce is too large: maximum $maxNonceLength digits", SC_BAD_REQUEST))
        }
    }

    private fun validateCMAC(msg3: Message3, smk: ByteArray) {
        val cmac = crypto.aesCMAC(smk, { aes ->
            aes.update(msg3.ga)
            aes.update(msg3.securityManifest ?: byteArrayOf())
            aes.update(msg3.quote)
        })
        if (!cmac.contentEquals(msg3.aesCMAC)) {
            throw BadRequestException(responseOf("Incorrect CMAC value", SC_BAD_REQUEST))
        }
    }

    private fun validate(response: HttpResponse): String {
        return EntityUtils.toByteArray(response.entity).let { payload ->
            val iasSignature = response.requireHeader("X-IASReport-Signature")
            val iasSigningCertificate = response.requireHeader("X-IASReport-Signing-Certificate").decodeURL()

            val certificatePath = try {
                parseCertificates(iasSigningCertificate)
            } catch (e: CertificateException) {
                log.error("Failed to parse certificate from HTTP header '{}': {}", iasSigningCertificate, e.message)
                throw ForbiddenException(response.toResponse("Invalid X-IASReport HTTP headers", SC_FORBIDDEN))
            }

            try {
                val certValidator = CertPathValidator.getInstance("PKIX")
                certValidator.validate(certificatePath, pkixParameters)
            } catch (e: GeneralSecurityException) {
                log.error("Certificate '{}' is invalid: {}", certificatePath, e.message)
                throw ForbiddenException(response.toResponse("Invalid IAS certificate", SC_FORBIDDEN))
            }

            val signature = try {
                Signature.getInstance("SHA256withRSA").apply {
                    initVerify(certificatePath.certificates[0])
                }
            } catch (e: GeneralSecurityException) {
                log.error("Failed to initialise signature: {}", e.message)
                throw ForbiddenException(response.toResponse("", SC_FORBIDDEN))
            }

            try {
                signature.update(payload)
                if (!signature.verify(iasSignature.toByteArray().decodeBase64())) {
                    throw ForbiddenException(response.toResponse("Report failed IAS signature check", SC_FORBIDDEN))
                }
            } catch (e: SignatureException) {
                log.error("Failed to parse signature from IAS: {}", e.message)
                throw ForbiddenException(response.toResponse("Corrupt IAS signature data", SC_FORBIDDEN))
            }

            String(payload, UTF_8)
        }
    }

    private fun parseCertificates(iasCertificateHeader: String): CertPath {
        val certificates = mutableListOf<Certificate>()
        iasCertificateHeader.byteInputStream().use { input ->
            while (input.available() > 0) {
                certificates.add(certificateFactory.generateCertificate(input))
            }
        }
        return certificateFactory.generateCertPath(certificates)
    }

    private fun responseOf(message: String, statusCode: Int = SC_INTERNAL_SERVER_ERROR): Response = Response.status(statusCode)
        .entity(AttestationError(message))
        .build()

    private fun HttpResponse.requireHeader(name: String): String {
        return (this.getFirstHeader(name) ?: throw ForbiddenException(toResponse("Response header '$name' missing", SC_FORBIDDEN))).value
    }

    private fun HttpResponse.toResponse(message: String, statusCode: Int = statusLine.statusCode): Response {
        return Response.status(statusCode)
            .entity(AttestationError(message))
            .apply {
                val requestIdHeader = getFirstHeader("Request-ID") ?: return@apply
                this.header(requestIdHeader.name, requestIdHeader.value)
            }
            .build()
    }

    private fun createHttpClient(): CloseableHttpClient {
        val sslContext = SSLContextBuilder()
            .loadKeyMaterial(isvStore, storePassword, { _, _ -> isvKeyAlias })
            .setSecureRandom(crypto.random)
            .build()
        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("https", SSLConnectionSocketFactory(sslContext, getDefaultHostnameVerifier()))
            .build()
        return HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager(registry).apply {
                socketConfig = httpSocketConfig
            })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    private fun signatureOf(publicKey: ECPublicKey, peerKey: ECPublicKey): ByteArray {
        val signature = Signature.getInstance("SHA256WithECDSA").let { signer ->
            signer.initSign(serviceKeyPair.private, crypto.random)
            signer.update(publicKey.toLittleEndian())
            signer.update(peerKey.toLittleEndian())
            signer.sign()
        }
        return ByteBuffer.allocate(KEY_SIZE).let { buf ->
            ASN1InputStream(signature).use { input ->
                for (number in input.readObject() as DLSequence) {
                    val pos = (number as ASN1Integer).positiveValue.toLittleEndian(KEY_SIZE / 2)
                    buf.put(pos)
                }
                buf.array()
            }
        }
    }

    private fun ByteArray.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
    private fun String.decodeURL(): String = URLDecoder.decode(this, "UTF-8")
    private fun ByteArray.removeHeader(headerSize: Int) = copyOfRange(headerSize, size)
    private fun ByteArray.ifNotZeros(): ByteArray? {
        for (i in 0 until size) {
            if (this[i] != 0.toByte()) return this
        }
        return null
    }
}
