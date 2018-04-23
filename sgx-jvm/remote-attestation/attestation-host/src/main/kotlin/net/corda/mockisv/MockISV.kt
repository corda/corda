package net.corda.mockisv

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.ReportProxyResponse
import net.corda.attestation.message.ReportRequest
import net.corda.attestation.message.ServiceResponse
import net.corda.attestation.message.ias.ManifestStatus
import net.corda.attestation.message.ias.QuoteStatus
import net.corda.attestation.message.ias.ReportResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/mockisv/isv")
class MockISV {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(MockISV::class.java)
        private const val SPID = "84D402C36BA9EF9B0A86EF1A9CC8CE4F"
        private const val linkableQuote = 1.toShort()
        private const val QUOTE_BODY_SIZE = 432

        private const val BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n"
        private const val END_CERT = "\n-----END CERTIFICATE-----\n"
        private val platformInfo = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte(), 0x11, 0x22)
        private const val signatureAlias = "ias"
        private val storePassword = "attestation".toCharArray()
        private val keyStore: KeyStore = KeyStore.getInstance("PKCS12").apply {
            MockISV::class.java.classLoader.getResourceAsStream("dummyIAS.pfx")?.use { input ->
                load(input, storePassword)
            }
        }
        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
        private val signingKey = keyStore.getKey(signatureAlias, storePassword) as PrivateKey
        private val signingCertHeader: String = keyStore.getCertificateChain(signatureAlias).let { chain ->
            StringBuilder().apply {
                chain.forEach { cert -> append(cert.toPEM()) }
            }.toString()
        }

        private fun ByteArray.encodeBase64(): ByteArray = Base64.getEncoder().encode(this)

        private fun Certificate.toPEM(): String = ByteArrayOutputStream().let { out ->
            out.write(BEGIN_CERT.toByteArray())
            out.write(encoded.encodeBase64())
            out.write(END_CERT.toByteArray())
            String(out.toByteArray(), UTF_8)
        }
    }

    @GET
    @Path("/service")
    fun challenge(): Response {
        log.info("Mock Service Information")
        return Response.ok(ServiceResponse(SPID, linkableQuote)).build()
    }

    @GET
    @Path("/sigrl/{gid}")
    fun revocationList(@PathParam("gid") gid: String): Response {
        log.info("Mock Signature Revocation List")
        return Response.ok(if (gid.toLowerCase() == "00000000") "AAIADgAAAAEAAAABAAAAAGSf/es1h/XiJeCg7bXmX0S/NUpJ2jmcEJglQUI8VT5sLGU7iMFu3/UTCv9uPADal3LhbrQvhBa6+/dWbj8hnsE=" else "")
            .build()
    }

    @POST
    @Path("/report")
    fun getReport(request: ReportRequest): Response {
        log.info("Mock IAS Report")
        val report = ReportResponse(
            id = "9497457846286849067596886882708771068",
            isvEnclaveQuoteStatus = QuoteStatus.OK,
            isvEnclaveQuoteBody = if (request.isvEnclaveQuote.size > QUOTE_BODY_SIZE)
                request.isvEnclaveQuote.copyOf(QUOTE_BODY_SIZE)
            else
                request.isvEnclaveQuote,
            pseManifestStatus = request.pseManifest?.toStatus(),
            platformInfoBlob = platformInfo,
            nonce = request.nonce,
            timestamp = LocalDateTime.now(Clock.systemUTC())
        )
        val reportData = mapper.writeValueAsString(report).toByteArray()
        val response = ReportProxyResponse(
            signature = signatureOf(reportData).encodeBase64().toString(UTF_8),
            certificatePath = signingCertHeader,
            report = reportData
        )
        return Response.ok(response).build()
    }

    private fun signatureOf(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").let { signer ->
        signer.initSign(signingKey)
        signer.update(data)
        signer.sign()
    }

    private fun ByteArray.toStatus(): ManifestStatus
            = if (this.isEmpty() || this[0] == 0.toByte()) ManifestStatus.INVALID else ManifestStatus.OK
}
