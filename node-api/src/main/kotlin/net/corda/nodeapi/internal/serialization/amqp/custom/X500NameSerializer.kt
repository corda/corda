package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.x500.X500Name
import java.lang.reflect.Type

/**
 * Custom serializer for X500 names that utilizes their ASN.1 encoding on the wire.
 */
object X500NameSerializer : CustomSerializer.Implements<X500Name>(X500Name::class.java) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: X500Name, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.encoded, data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): X500Name {
        val binary = input.readObject(obj, schema, ByteArray::class.java) as ByteArray
        return X500Name.getInstance(ASN1InputStream(binary).readObject())
    }
}