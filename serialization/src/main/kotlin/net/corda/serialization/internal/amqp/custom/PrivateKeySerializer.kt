package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.Storage
import net.corda.core.serialization.SerializationFactory
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.PrivateKey

object PrivateKeySerializer : CustomSerializer.Implements<PrivateKey>(PrivateKey::class.java) {

    override val schemaForDocumentation = Schema(listOf(RestrictedType(type.toString(), "", listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList())))

    override fun writeDescribedObject(obj: PrivateKey, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext
    ) {
        checkUseCase()
        output.writeObject(obj.encoded, data, clazz, context)
    }

    private fun checkUseCase() {
        val currentContext: SerializationContext = SerializationFactory.defaultFactory.currentContext
                ?: throw IllegalStateException("Current context is not set")
        if (Storage != currentContext.useCase) {
            throw IllegalStateException("UseCase '${currentContext.useCase}' is not '$Storage'")
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext): PrivateKey {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return Crypto.decodePrivateKey(bits)
    }
}