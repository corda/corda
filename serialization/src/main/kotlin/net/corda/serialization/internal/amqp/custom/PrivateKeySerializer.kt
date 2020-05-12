package net.corda.serialization.internal.amqp.custom

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.Crypto
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.Storage
import net.corda.serialization.internal.amqp.*
import net.corda.serialization.internal.checkUseCase
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PrivateKey

@DeleteForDJVM
object PrivateKeySerializer
    : CustomSerializer.Implements<PrivateKey>(
        PrivateKey::class.java
) {

    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: PrivateKey, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        checkUseCase(Storage)
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): PrivateKey {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return Crypto.decodePrivateKey(bits)
    }
}