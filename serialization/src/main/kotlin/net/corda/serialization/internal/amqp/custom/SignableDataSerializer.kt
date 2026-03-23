package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPTypeIdentifiers
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Descriptor
import net.corda.serialization.internal.amqp.Field
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.withDescribed
import net.corda.serialization.internal.amqp.withList
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Writes SignableData using the legacy descriptor and field order so the full signed payload stays byte-for-byte
 * compatible with the historical form when nested serializers also preserve their legacy contracts.
 */
object SignableDataSerializer : CustomSerializer.Is<SignableData>(SignableData::class.java) {
    private val fieldTypes = listOf(SignatureMetadata::class.java, SecureHash::class.java)
    private val legacyDescriptor = Descriptor(Symbol.valueOf("net.corda:MYA9jrnNdlQaaX06oEsmxA=="))
    private val legacySecureHashDescriptor = Descriptor(Symbol.valueOf("net.corda:b79PeMBLsHxu2A23yDYRaA=="))
    private val legacyTypeNotation = CompositeType(
            AMQPTypeIdentifiers.nameForType(type),
            null,
            emptyList(),
            legacyDescriptor,
            listOf(
                    Field("signatureMetadata", AMQPTypeIdentifiers.nameForType(SignatureMetadata::class.java), emptyList(), null, null, true, false),
                    Field("txId", AMQPTypeIdentifiers.nameForType(SecureHash::class.java), emptyList(), null, null, true, false)
            )
    )
    private val legacySecureHashTypeNotation = CompositeType(
            AMQPTypeIdentifiers.nameForType(SecureHash::class.java),
            null,
            emptyList(),
            legacySecureHashDescriptor,
            listOf(
                    Field("bytes", AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java), emptyList(), null, null, true, false),
                    Field("offset", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false),
                    Field("size", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false)
            )
    )

    override val schemaForDocumentation: Schema = Schema(listOf(legacyTypeNotation, legacySecureHashTypeNotation))

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        output.writeTypeNotations(legacyTypeNotation)
        data.withDescribed(legacyTypeNotation.descriptor) {
            @Suppress("UNCHECKED_CAST")
            writeDescribedObject(obj as SignableData, data, type, output, context)
        }
    }

    override fun writeDescribedObject(
            obj: SignableData,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        data.withList {
            output.writeObject(obj.signatureMetadata, data, fieldTypes[0], context)
            output.writeTypeNotations(legacySecureHashTypeNotation)
            output.writeObject(obj.txId, data, fieldTypes[1], context)
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): SignableData {
        val fields = obj as? List<*> ?: throw IllegalArgumentException("Unexpected SignableData payload: ${obj::class.java.name}")
        val signatureMetadata = input.readObject(requireNotNull(fields.getOrNull(0)), schemas, fieldTypes[0], context) as SignatureMetadata
        val txId = input.readObject(requireNotNull(fields.getOrNull(1)), schemas, fieldTypes[1], context) as SecureHash
        return SignableData(txId, signatureMetadata)
    }
}
