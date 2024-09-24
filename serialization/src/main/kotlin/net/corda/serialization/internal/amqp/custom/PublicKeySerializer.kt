package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.DESERIALIZATION_CACHE_PROPERTY
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPTypeIdentifiers
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.RestrictedType
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
object PublicKeySerializer
    : CustomSerializer.Implements<PublicKey>(
        PublicKey::class.java
) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: PublicKey, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        // TODO: Instead of encoding to the default X509 format, we could have a custom per key type (space-efficient) serialiser.
        output.writeObject(Crypto.encodePublicKey(obj), data, clazz, context)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): PublicKey {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        @Suppress("unchecked_cast")
        return (context.properties[DESERIALIZATION_CACHE_PROPERTY] as? MutableMap<CacheKey, PublicKey>)
            ?.computeIfAbsent(CacheKey(bits)) { key ->
                Crypto.decodePublicKey(key.bytes)
            } ?: Crypto.decodePublicKey(bits)
    }
}
