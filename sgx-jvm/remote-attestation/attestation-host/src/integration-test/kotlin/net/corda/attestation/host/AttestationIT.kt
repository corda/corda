package net.corda.attestation.host

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.*
import net.corda.attestation.message.*
import org.apache.http.HttpStatus.*
import org.apache.http.client.CookieStore
import org.apache.http.client.config.CookieSpecs.STANDARD_STRICT
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
import org.apache.http.util.EntityUtils
import org.junit.*
import org.junit.Assert.*
import java.nio.charset.StandardCharsets.UTF_8
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.cert.PKIXRevocationChecker.Option.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.util.*

class AttestationIT {
    private companion object {
        private val httpPort = Integer.getInteger("test.httpPort")
        private val revocationListOptions = EnumSet.of(SOFT_FAIL, PREFER_CRLS, NO_FALLBACK)
        private const val AES_CMAC_FUNC = 1.toShort()
    }
    private lateinit var certificateFactory: CertificateFactory
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var cookies: CookieStore
    private lateinit var challengerKeyPair: KeyPair
    private lateinit var transientKeyPair: KeyPair
    private lateinit var ecParameters: ECParameterSpec
    private lateinit var pkixParameters: PKIXParameters
    private lateinit var challengeResponse: ChallengeResponse
    private lateinit var peerPublicKey: ECPublicKey
    private lateinit var smk: ByteArray
    private lateinit var mapper: ObjectMapper
    private lateinit var crypto: Crypto

    @Rule
    @JvmField
    val cryptoProvider = CryptoProvider()

    @Rule
    @JvmField
    val signatureProvider = SignatureProvider(cryptoProvider)

    @Rule
    @JvmField
    val keyStoreProvider = KeyStoreProvider("dummyIAS-trust.pfx", "attestation")

    @Before
    fun setup() {
        cookies = BasicCookieStore()
        crypto = cryptoProvider.crypto
        certificateFactory = CertificateFactory.getInstance("X.509")
        challengerKeyPair = crypto.generateKeyPair()
        transientKeyPair = crypto.generateKeyPair()
        ecParameters = (transientKeyPair.public as ECPublicKey).params
        mapper = ObjectMapper().registerModule(JavaTimeModule())

        pkixParameters = PKIXParameters(keyStoreProvider.trustAnchorsFor("ias")).apply {
            val rlChecker = CertPathValidator.getInstance("PKIX").revocationChecker as PKIXRevocationChecker
            addCertPathChecker(rlChecker.apply { options = revocationListOptions })
        }

        val httpRequestConfig: RequestConfig = RequestConfig.custom()
            .setCookieSpec(STANDARD_STRICT)
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

        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        challengerKeyPair = crypto.generateKeyPair()
        val challengeRequest = ChallengeRequest((challengerKeyPair.public as ECPublicKey).toLittleEndian(), "nonce-value")
        val request = HttpPost("http://localhost:$httpPort/host/challenge").apply {
            entity = StringEntity(mapper.writeValueAsString(challengeRequest), APPLICATION_JSON)
        }
        val response = httpClient.execute(request, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
        challengeResponse = mapper.readValue(response)

        val keyFactory = KeyFactory.getInstance("EC")
        peerPublicKey = keyFactory.generatePublic(challengeResponse.ga.toBigEndianKeySpec(ecParameters)) as ECPublicKey
        smk = crypto.generateSMK(transientKeyPair.private, peerPublicKey)
    }

    @After
    fun done() {
        httpClient.close()
    }

    @Test
    fun postAttestation() {
        val context = HttpClientContext.create().apply {
            cookieStore = cookies
        }

        val gb = (transientKeyPair.public as ECPublicKey).toLittleEndian()
        val signatureGbGa = signatureProvider.signatureOf(challengerKeyPair.private, transientKeyPair.public as ECPublicKey, peerPublicKey)
        val attestRequest = AttestationRequest(
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
        val httpRequest = HttpPost("http://localhost:$httpPort/host/attest").apply {
            entity = StringEntity(mapper.writeValueAsString(attestRequest), APPLICATION_JSON)
        }
        val responseBody = httpClient.execute(httpRequest, context).use { response ->
            val output = EntityUtils.toString(response.entity, UTF_8)
            assertEquals(output, SC_OK, response.statusLine.statusCode)
            output
        }
        validateSignature(mapper.readValue(responseBody))
    }

    private fun validateSignature(reportResponse: ReportProxyResponse) {
        val certificatePath = parseCertificates(reportResponse.certificatePath)
        CertPathValidator.getInstance("PKIX").apply {
            validate(certificatePath, pkixParameters)
        }

        Signature.getInstance("SHA256withRSA").apply {
            initVerify(certificatePath.certificates[0])
            update(reportResponse.report)
            if (!verify(reportResponse.signature.toByteArray().decodeBase64())) {
                throw IllegalArgumentException("Incorrect response signature")
            }
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

    private fun ByteArray.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
}