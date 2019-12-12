package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.CertPathSerializer.CertPathProxy
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.util.function.Function

class CertPathDeserializer : Function<CertPathProxy, CertPath> {
    override fun apply(proxy: CertPathProxy): CertPath {
        val factory = CertificateFactory.getInstance(proxy.type)
        return factory.generateCertPath(proxy.encoded.inputStream())
    }
}
