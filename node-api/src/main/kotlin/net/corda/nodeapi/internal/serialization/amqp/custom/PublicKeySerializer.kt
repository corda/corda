package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
object PublicKeySerializer : CustomSerializer.Implements<PublicKey>(PublicKey::class.java) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: PublicKey, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        // TODO: Instead of encoding to the default X509 format, we could have a custom per key type (space-efficient) serialiser.
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ) : PublicKey {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return Crypto.decodePublicKey(bits)
    }
}