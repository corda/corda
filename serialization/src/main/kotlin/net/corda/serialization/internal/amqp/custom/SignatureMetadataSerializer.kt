package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPTypeIdentifiers
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.RestrictedType
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.withList
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

/**
 * Writes SignatureMetadata in the exact legacy two-field form when proofChain is null, preserving the old bytes.
 * When proofChain is present, an extended three-field form is written under the same type descriptor.
 */
object SignatureMetadataSerializer : CustomSerializer.Is<SignatureMetadata>(SignatureMetadata::class.java) {
    private val legacyFieldTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    private val extendedFieldTypes = legacyFieldTypes + KeyRotationProofChain::class.java

    override val schemaForDocumentation: Schema = Schema(listOf(RestrictedType(
            AMQPTypeIdentifiers.nameForType(type),
            "",
            listOf(AMQPTypeIdentifiers.nameForType(type)),
            AMQPTypeIdentifiers.nameForType(type),
            descriptor,
            emptyList()
    )))

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
