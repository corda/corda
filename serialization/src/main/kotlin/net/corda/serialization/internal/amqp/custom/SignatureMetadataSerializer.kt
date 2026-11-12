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
import net.corda.serialization.internal.amqp.withList
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

private val INT: Type = Int::class.javaPrimitiveType!!
private val PROOF_CHAIN: Type = KeyRotationProofChain::class.java

private fun intField(name: String) = Field(name, AMQPTypeIdentifiers.primitiveTypeName(Int::class.java), emptyList(), "0", null, true, false)

/**
 * The serializer of [SignatureMetadata]. Its own form has three fields, the platform version, the scheme number and the proof chain,
 * and is written under its type descriptor, as for any custom serializer.
 *
 * It is only used for metadata that has a proof chain. The metadata of a signature is part of what is signed, so the serialized form of
 * metadata without a proof chain must stay exactly what it was before the proof chain existed: such metadata is handed to
 * [LegacySignatureMetadataSerializer], which writes that form under the descriptor it had then, and which is registered under that
 * descriptor, so that deserialization finds it from the descriptor on the wire.
 */
object SignatureMetadataSerializer : CustomSerializer.Is<SignatureMetadata>(SignatureMetadata::class.java) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(LegacySignatureMetadataSerializer)

    override val schemaForDocumentation: Schema = Schema(listOf(CompositeType(
            AMQPTypeIdentifiers.nameForType(type),
            null,
            emptyList(),
            descriptor,
            listOf(
                    intField("platformVersion"),
                    intField("schemeNumberID"),
                    Field("proofChain", AMQPTypeIdentifiers.nameForType(KeyRotationProofChain::class.java), emptyList(), null, null, true, false)
            )
    )))

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        if ((obj as SignatureMetadata).proofChain == null) {
            LegacySignatureMetadataSerializer.writeObject(obj, data, type, output, context, debugIndent)
        } else {
            super.writeObject(obj, data, type, output, context, debugIndent)
        }
    }

    override fun writeDescribedObject(
            obj: SignatureMetadata,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        val proofChain = requireNotNull(obj.proofChain) { "Signature metadata without a proof chain is written in its legacy form" }
        data.withList {
            output.writeObject(obj.platformVersion, data, INT, context)
            output.writeObject(obj.schemeNumberID, data, INT, context)
            output.writeObject(proofChain, data, PROOF_CHAIN, context)
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): SignatureMetadata {
        val fields = obj as? List<*> ?: throw IllegalArgumentException("Unexpected SignatureMetadata payload: ${obj::class.java.name}")
        val platformVersion = input.readObject(requireNotNull(fields.getOrNull(0)), schemas, INT, context) as Int
        val schemeNumberID = input.readObject(requireNotNull(fields.getOrNull(1)), schemas, INT, context) as Int
        val proofChain = input.readObject(requireNotNull(fields.getOrNull(2)), schemas, PROOF_CHAIN, context) as KeyRotationProofChain
        return SignatureMetadata(platformVersion, schemeNumberID, proofChain)
    }
}

/**
 * The serializer of the form [SignatureMetadata] had before it had a proof chain, when it was serialized as an object with two
 * properties: the same two fields, under the same fingerprint descriptor, with the same type notation in the schema, so that the bytes
 * are the same. Its type descriptor is that descriptor, under which it is registered and found when deserializing.
 *
 * It is not the serializer of the class: [SignatureMetadataSerializer] is, and hands it the metadata that has no proof chain.
 */
internal object LegacySignatureMetadataSerializer : CustomSerializer<SignatureMetadata>() {
    override val type: Type get() = SignatureMetadata::class.java
    override val typeDescriptor: Symbol = Symbol.valueOf("net.corda:IzFt8cRKytsJq3vQ+yjsGg==")
    override val descriptor: Descriptor = Descriptor(typeDescriptor)

    private val typeNotation = CompositeType(
            AMQPTypeIdentifiers.nameForType(type),
            null,
            emptyList(),
            descriptor,
            listOf(intField("platformVersion"), intField("schemeNumberID"))
    )

    override val schemaForDocumentation: Schema = Schema(listOf(typeNotation))

    /** Never the serializer of a class: it is reached by its descriptor when reading, and through [SignatureMetadataSerializer] when writing. */
    override fun isSerializerFor(clazz: Class<*>): Boolean = false

    override fun writeClassInfo(output: SerializationOutput) {}

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        output.writeTypeNotations(typeNotation)
        super.writeObject(obj, data, type, output, context, debugIndent)
    }

    override fun writeDescribedObject(
            obj: SignatureMetadata,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
    ) {
        data.withList {
            output.writeObject(obj.platformVersion, data, INT, context)
            output.writeObject(obj.schemeNumberID, data, INT, context)
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): SignatureMetadata {
        val fields = obj as? List<*> ?: throw IllegalArgumentException("Unexpected SignatureMetadata payload: ${obj::class.java.name}")
        val platformVersion = input.readObject(requireNotNull(fields.getOrNull(0)), schemas, INT, context) as Int
        val schemeNumberID = input.readObject(requireNotNull(fields.getOrNull(1)), schemas, INT, context) as Int
        return SignatureMetadata(platformVersion, schemeNumberID)
    }
}
