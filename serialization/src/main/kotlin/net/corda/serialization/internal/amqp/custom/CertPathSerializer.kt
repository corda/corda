package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.DESERIALIZATION_CACHE_PROPERTY
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class CertPathSerializer(
        factory: SerializerFactory
) : CustomSerializer.Proxy<CertPath, CertPathSerializer.CertPathProxy>(
        CertPath::class.java,
        CertPathProxy::class.java,
        factory
) {
    override fun toProxy(obj: CertPath): CertPathProxy = CertPathProxy(obj.type, obj.encoded)

    override fun fromProxy(proxy: CertPathProxy): CertPath {
        try {
            val cf = CertificateFactory.getInstance(proxy.type)
            return cf.generateCertPath(proxy.encoded.inputStream())
        } catch (ce: CertificateException) {
            val nse = NotSerializableException("java.security.cert.CertPath: $type")
            nse.initCause(ce)
            throw nse
        }
    }

    override fun fromProxy(proxy: CertPathProxy, context: SerializationContext): CertPath {
        // This requires [CertPathProxy] to have correct
        // implementations for [equals] and [hashCode].
        @Suppress("unchecked_cast")
        return (context.properties[DESERIALIZATION_CACHE_PROPERTY] as? MutableMap<CertPathProxy, CertPath>)
            ?.computeIfAbsent(proxy, ::fromProxy)
            ?: fromProxy(proxy)
    }

    @KeepForDJVM
    data class CertPathProxy(val type: String, val encoded: ByteArray) {
        override fun hashCode() = (type.hashCode() * 31) + encoded.contentHashCode()
        override fun equals(other: Any?): Boolean {
            return (this === other)
                || (other is CertPathProxy && (type == other.type && encoded.contentEquals(other.encoded)))
        }
    }
}
