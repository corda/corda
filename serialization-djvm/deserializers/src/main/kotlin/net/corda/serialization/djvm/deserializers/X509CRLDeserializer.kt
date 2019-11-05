package net.corda.serialization.djvm.deserializers

import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.util.function.Function

class X509CRLDeserializer : Function<ByteArray, X509CRL> {
    override fun apply(bytes: ByteArray): X509CRL {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCRL(bytes.inputStream()) as X509CRL
    }
}
