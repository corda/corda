package net.corda.serialization.djvm.deserializers

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.function.Function

class X509CertificateDeserializer : Function<ByteArray, X509Certificate> {
    override fun apply(bits: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(bits.inputStream()) as X509Certificate
    }
}
