@file:JvmName("ReportSigner")
package net.corda.mockias

import net.corda.mockias.io.SignatureOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.util.*
import javax.ws.rs.NameBinding
import javax.ws.rs.ext.Provider
import javax.ws.rs.ext.WriterInterceptor
import javax.ws.rs.ext.WriterInterceptorContext


@Provider
@IASReport
class ReportSigner : WriterInterceptor {
    private companion object {
        private const val BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n"
        private const val END_CERT = "\n-----END CERTIFICATE-----\n"
        private const val signatureAlias = "ias"
        private val storePassword = "attestation".toCharArray()
        private val keyStore: KeyStore = KeyStore.getInstance("PKCS12").apply {
            ReportSigner::class.java.classLoader.getResourceAsStream("dummyIAS.pfx")?.use { input ->
                load(input, storePassword)
            }
        }
        private val signingKey = keyStore.getKey(signatureAlias, storePassword) as PrivateKey
        private val signingCertHeader: String = keyStore.getCertificateChain(signatureAlias).let { chain ->
            StringBuilder().apply {
                chain.forEach { cert -> append(cert.toPEM()) }
            }.toString().encodeURL()
        }

        private fun ByteArray.encodeBase64(): ByteArray = Base64.getEncoder().encode(this)
        private fun String.encodeURL(): String = URLEncoder.encode(this, "UTF-8")

        private fun Certificate.toPEM(): String = ByteArrayOutputStream().let { out ->
            out.write(BEGIN_CERT.toByteArray())
            out.write(encoded.encodeBase64())
            out.write(END_CERT.toByteArray())
            String(out.toByteArray(), UTF_8)
        }
    }

    @Throws(IOException::class)
    override fun aroundWriteTo(context: WriterInterceptorContext) {
        val contentStream = context.outputStream
        val baos = ByteArrayOutputStream()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(signingKey)
        }
        context.outputStream = SignatureOutputStream(baos, signature)
        try {
            context.proceed()
            context.headers?.apply {
                add("X-IASReport-Signature", signature.sign().encodeBase64().toString(UTF_8))
                add("X-IASReport-Signing-Certificate", signingCertHeader)
            }
        } finally {
            baos.writeTo(contentStream)
        }
    }
}

@NameBinding
annotation class IASReport
