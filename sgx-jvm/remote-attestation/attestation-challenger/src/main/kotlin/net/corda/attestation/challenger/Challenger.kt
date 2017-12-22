package net.corda.attestation.challenger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.*
import net.corda.attestation.message.*
import net.corda.attestation.message.ias.ReportResponse
import org.apache.http.HttpStatus.*
import org.apache.http.client.config.CookieSpecs.*
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
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.util.*

class Challenger(
    private val keyPair: KeyPair,
    private val enclaveHost: URI,
    private val pkixParameters: PKIXParameters
) {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(Challenger::class.java)

        private const val AES_CMAC_FUNC = 1.toShort()
        private const val tlvHeaderSize = 8

        private val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setCookieSpec(STANDARD_STRICT)
            .setConnectTimeout(20_000)
            .setSocketTimeout(5_000)
            .build()
        private val httpSocketConfig: SocketConfig = SocketConfig.custom()
            .setSoReuseAddress(true)
            .setTcpNoDelay(true)
            .build()
    }

    private val mapper = ObjectMapper().registerModule(JavaTimeModule())
    private val keyFactory: KeyFactory = KeyFactory.getInstance("EC")
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
    private val transientKeyPair: KeyPair
    private val ecParameters: ECParameterSpec
    private val crypto = Crypto()

    private val cookies = BasicCookieStore()

    init {
        ecParameters = (crypto.generateKeyPair().public as ECPublicKey).params
        log.info("Elliptic Curve Parameters: {}", ecParameters)

        transientKeyPair = crypto.generateKeyPair()
    }


    fun attestToEnclave(): AttestationResult {
        createHttpClient().use { client ->
            val context = HttpClientContext.create().apply {
                cookieStore = cookies
            }

            // Send our public key, and receive the host's transient DH public key.
            val challengeResponse: ChallengeResponse = try {
                val challengeURI = enclaveHost.toString() + "/challenge"
                log.info("Invoking host: {}", challengeURI)

                val challengeRequest = ChallengeRequest(
                    nonce = createNonce(),
                    gc = (keyPair.public as ECPublicKey).toLittleEndian()
                )
                val httpRequest = HttpPost(challengeURI).apply {
                    entity = StringEntity(mapper.writeValueAsString(challengeRequest), APPLICATION_JSON)
                }
                client.execute(httpRequest, context).use { httpResponse ->
                    val statusCode = httpResponse.statusLine.statusCode
                    if (statusCode != SC_OK) {
                        throw ChallengerException("Challenge request to enclave failed (HTTP $statusCode)")
                    }
                    mapper.readValue(httpResponse.entity.content)
                }
            } catch (e: IOException) {
                log.error("HTTP client error", e)
                throw ChallengerException(e.message, e)
            }

            val peerPublicKey = keyFactory.generatePublic(challengeResponse.ga.toBigEndianKeySpec(ecParameters)) as ECPublicKey
            val smk = crypto.generateSMK(transientKeyPair.private, peerPublicKey)

            // Send our public key and signatures to the enclave.
            val reportBody: ReportProxyResponse = try {
                val attestURI = enclaveHost.toString() + "/attest"
                log.info("Invoking host: {}", attestURI)

                val publicKey = transientKeyPair.public as ECPublicKey
                val signatureGbGa = signatureOf(publicKey, peerPublicKey)
                val gb = publicKey.toLittleEndian()
                val signatureRequest = AttestationRequest(
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
                val httpRequest = HttpPost(attestURI).apply {
                    entity = StringEntity(mapper.writeValueAsString(signatureRequest), APPLICATION_JSON)
                }
                client.execute(httpRequest, context).use { httpResponse ->
                    val statusCode = httpResponse.statusLine.statusCode
                    if (statusCode != SC_OK) {
                        throw ChallengerException("Failed sending signatures to enclave (HTTP $statusCode)")
                    }
                    mapper.readValue(httpResponse.entity.content)
                }
            } catch (e: IOException) {
                log.error("HTTP request error", e)
                throw ChallengerException(e.message, e)
            }

            // Check that this message really came from Intel.
            validateSignature(reportBody)

            val reportResponse: ReportResponse = mapper.readValue(reportBody.report.inputStream())
            val platformInfo = reportResponse.platformInfoBlob?.removeHeader(tlvHeaderSize)
            log.info("Attestation completed")

            // Successful response
            return AttestationResult(
                reportID = reportResponse.id,
                quoteStatus = reportResponse.isvEnclaveQuoteStatus,
                peerPublicKey = peerPublicKey,
                platformInfo = platformInfo,
                timestamp = reportResponse.timestamp
            )
        }
    }

    fun setSecret(secretValue: String, attestation: AttestationResult) {
        val mk = crypto.generateMK(transientKeyPair.private, attestation.peerPublicKey)
        val secretKey = crypto.generateSecretKey(transientKeyPair.private, attestation.peerPublicKey)

        try {
            createHttpClient().use { client ->
                val secretIV = crypto.createIV()
                val secretData = crypto.encrypt(secretValue.toByteArray(), secretKey, secretIV)
                val secretURI = enclaveHost.toString() + "/secret"
                log.info("Invoking host: {}", secretURI)

                val context = HttpClientContext.create().apply {
                    cookieStore = cookies
                }

                val secretRequest = SecretRequest(
                    data = secretData.encryptedData(),
                    authTag = secretData.authenticationTag(),
                    iv = secretIV,
                    platformInfo = attestation.platformInfo,
                    aesCMAC = crypto.aesCMAC(mk, { aes ->
                        aes.update(attestation.platformInfo)
                    })
                )
                val httpRequest = HttpPost(secretURI).apply {
                    entity = StringEntity(mapper.writeValueAsString(secretRequest), APPLICATION_JSON)
                }
                client.execute(httpRequest, context).use { httpResponse ->
                    val statusCode = httpResponse.statusLine.statusCode
                    if (statusCode != SC_OK) {
                        throw ChallengerException("Failed sending secret to enclave (HTTP $statusCode)")
                    }
                }

                log.info("Successfully provisioned to enclave")
            }
        } catch (e: IOException) {
            log.error("HTTP client error", e)
            throw ChallengerException(e.message, e)
        }
    }

    private fun createNonce(): String = UUID.randomUUID().let { uuid ->
        String.format("%016x%016x", uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    private fun createHttpClient(): CloseableHttpClient {
        return HttpClients.custom()
            .setConnectionManager(BasicHttpClientConnectionManager().apply {
                socketConfig = httpSocketConfig
            })
            .setDefaultRequestConfig(httpRequestConfig)
            .build()
    }

    private fun validateSignature(reportResponse: ReportProxyResponse) {
        val certificatePath = try {
            parseCertificates(reportResponse.certificatePath)
        } catch (e: CertificateException) {
            log.error("Failed to parse certificate from HTTP header '{}': {}", reportResponse.certificatePath, e.message)
            throw e
        }

        try {
            val certValidator = CertPathValidator.getInstance("PKIX")
            certValidator.validate(certificatePath, pkixParameters)
        } catch (e: GeneralSecurityException) {
            log.error("Certificate '{}' is invalid: {}", certificatePath, e.message)
            throw e
        }

        val signature = try {
            Signature.getInstance("SHA256withRSA").apply {
                initVerify(certificatePath.certificates[0])
            }
        } catch (e: GeneralSecurityException) {
            log.error("Failed to initialise signature: {}", e.message)
            throw e
        }

        try {
            signature.update(reportResponse.report)
            if (!signature.verify(reportResponse.signature.toByteArray().decodeBase64())) {
                throw ChallengerException("Report failed IAS signature check")
            }
        } catch (e: SignatureException) {
            log.error("Failed to parse signature from IAS: {}", e.message)
            throw e
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

    private fun signatureOf(publicKey: ECPublicKey, peerKey: ECPublicKey): ByteArray {
        val signature = Signature.getInstance("SHA256WithECDSA").let { signer ->
            signer.initSign(keyPair.private, crypto.random)
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
    private fun ByteArray.removeHeader(headerSize: Int) = copyOfRange(headerSize, size)
}