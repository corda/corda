package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.cert.X509CRL

object X509CRLSerializer : CustomSerializer.Implements<X509CRL>(X509CRL::class.java) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            SerializerFactory.primitiveTypeName(ByteArray::class.java)!!,
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: X509CRL, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.encoded, data, clazz)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput): X509CRL {
        val bytes = input.readObject(obj, schemas, ByteArray::class.java) as ByteArray
        return X509CertificateFactory().delegate.generateCRL(bytes.inputStream()) as X509CRL
    }
}