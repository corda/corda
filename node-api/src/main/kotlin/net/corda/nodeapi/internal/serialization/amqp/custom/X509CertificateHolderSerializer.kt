package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import org.bouncycastle.cert.X509CertificateHolder
import java.lang.reflect.Type

/**
 * A serializer that writes out a certificate in X.509 format.
 */
object X509CertificateHolderSerializer : CustomSerializer.Implements<X509CertificateHolder>(X509CertificateHolder::class.java) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: X509CertificateHolder, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.encoded, data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): X509CertificateHolder {
        val bits = input.readObject(obj, schema, ByteArray::class.java) as ByteArray
        return X509CertificateHolder(bits)
    }
}