package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
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
 * Writes SignatureMetadata in the exact legacy two-field form when proofChain is null, preserving the old bytes.
 * When proofChain is present, an extended three-field form is written under a separate descriptor.
 */
object SignatureMetadataSerializer : CustomSerializer.Is<SignatureMetadata>(SignatureMetadata::class.java) {
    private val legacyFieldTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    private val extendedFieldTypes = legacyFieldTypes + KeyRotationProofChain::class.java
    private val legacyDescriptor = Descriptor(Symbol.valueOf("net.corda:IzFt8cRKytsJq3vQ+yjsGg=="))
    private val extendedDescriptor = Descriptor(Symbol.valueOf("net.corda:${AMQPTypeIdentifiers.nameForType(type)}:extended"))
    private val legacyTypeNotation = CompositeType(
            AMQPTypeIdentifiers.nameForType(type),
            null,
            emptyList(),
            legacyDescriptor,
            listOf(
                    Field("platformVersion", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false),
                    Field("schemeNumberID", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false)
            )
    )
    private val extendedTypeNotation = CompositeType(
            AMQPTypeIdentifiers.nameForType(type),
            null,
            emptyList(),
            extendedDescriptor,
            listOf(
                    Field("platformVersion", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false),
                    Field("schemeNumberID", AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false),
                    Field("proofChain", AMQPTypeIdentifiers.nameForType(KeyRotationProofChain::class.java), emptyList(), null, null, false, false)
            )
    )

    override val schemaForDocumentation: Schema = Schema(listOf(extendedTypeNotation))

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        val metadata = obj as SignatureMetadata
        val typeNotation = if (metadata.proofChain == null) legacyTypeNotation else extendedTypeNotation
        output.writeTypeNotations(typeNotation)
        data.withDescribed(typeNotation.descriptor) {
            @Suppress("unchecked_cast")
            writeDescribedObject(metadata, data, type, output, context)
        }
    }

    override fun writeDescribedObject(
            obj: SignatureMetadata,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        data.withList {
            output.writeObject(obj.platformVersion, data, legacyFieldTypes[0], context)
            output.writeObject(obj.schemeNumberID, data, legacyFieldTypes[1], context)
            obj.proofChain?.let { output.writeObject(it, data, extendedFieldTypes[2], context) }
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): SignatureMetadata {
        val fields = obj as? List<*> ?: throw IllegalArgumentException("Unexpected SignatureMetadata payload: ${obj::class.java.name}")
        val platformVersion = input.readObject(requireNotNull(fields.getOrNull(0)), schemas, legacyFieldTypes[0], context) as Int
        val schemeNumberID = input.readObject(requireNotNull(fields.getOrNull(1)), schemas, legacyFieldTypes[1], context) as Int
        val proofChain = if (fields.size > 2) {
            input.readObject(requireNotNull(fields[2]), schemas, extendedFieldTypes[2], context) as KeyRotationProofChain
        } else {
            null
        }
        return SignatureMetadata(platformVersion, schemeNumberID, proofChain)
    }
}
