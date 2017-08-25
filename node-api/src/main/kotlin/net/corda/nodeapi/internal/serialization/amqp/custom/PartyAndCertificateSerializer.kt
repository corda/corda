package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.identity.PartyAndCertificate
import net.corda.nodeapi.internal.serialization.amqp.*
import java.io.ByteArrayInputStream
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

/**
 * A serializer that writes out a party and certificate in encoded format.
 */
class PartyAndCertificateSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<PartyAndCertificate, PartyAndCertificateSerializer.PartyAndCertificateProxy>(PartyAndCertificate::class.java, PartyAndCertificateProxy::class.java, factory) {
    override fun toProxy(obj: PartyAndCertificate): PartyAndCertificateProxy = PartyAndCertificateProxy(obj.certPath.type, obj.certPath.encoded)

    override fun fromProxy(proxy: PartyAndCertificateProxy): PartyAndCertificate {
        try {
            val cf = CertificateFactory.getInstance(proxy.type)
            return PartyAndCertificate(cf.generateCertPath(ByteArrayInputStream(proxy.encoded)))
        } catch (ce: CertificateException) {
            val nse = NotSerializableException("java.security.cert.CertPath: " + type)
            nse.initCause(ce)
            throw nse
        }
    }

    data class PartyAndCertificateProxy(val type: String, val encoded: ByteArray)
}