package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.SerializationContext.UseCase.*
import net.corda.nodeapi.internal.serialization.amqp.*
import net.corda.nodeapi.internal.serialization.checkUseCase
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PrivateKey
import java.util.*

object PrivateKeySerializer : CustomSerializer.Implements<PrivateKey>(PrivateKey::class.java) {

    private val allowedUseCases = EnumSet.of(Storage, Checkpoint)

    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: PrivateKey, data: Data, type: Type, output: SerializationOutput) {
        checkUseCase(allowedUseCases)
        output.writeObject(obj.encoded, data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): PrivateKey {
        val bits = input.readObject(obj, schema, ByteArray::class.java) as ByteArray
        return Crypto.decodePrivateKey(bits)
    }
}