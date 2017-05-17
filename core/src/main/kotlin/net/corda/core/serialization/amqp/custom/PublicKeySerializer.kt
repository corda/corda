package net.corda.core.serialization.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.amqp.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PublicKey

class PublicKeySerializer : CustomSerializer.Implements<PublicKey>(PublicKey::class.java) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()

    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(Binary::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: PublicKey, data: Data, type: Type, output: SerializationOutput) {
        // TODO: Instead of encoding to the default X509 format, we could have a custom per key type (space-efficient) serialiser.
        output.writeObject(Binary((obj as PublicKey).encoded), data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): PublicKey {
        val A = input.readObject(obj, schema, ByteArray::class.java) as Binary
        return Crypto.decodePublicKey(A.array)
    }
}