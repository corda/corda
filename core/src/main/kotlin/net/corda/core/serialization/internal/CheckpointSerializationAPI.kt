package net.corda.core.serialization.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.sequence
import java.io.NotSerializableException


object CheckpointSerializationDefaults {
    @DeleteForDJVM
    val CHECKPOINT_CONTEXT get() = effectiveSerializationEnv.checkpointContext
    val CHECKPOINT_SERIALIZER get() = effectiveSerializationEnv.checkpointSerializer
}

@KeepForDJVM
@DoNotImplement
interface CheckpointSerializer {
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): SerializedBytes<T>
}

/**
 * Parameters to checkpoint serialization and deserialization.
 */
@KeepForDJVM
@DoNotImplement
interface CheckpointSerializationContext {
    /**
     * If non-null, apply this encoding (typically compression) when serializing.
     */
    val encoding: SerializationEncoding?
    /**
     * The class loader to use for deserialization.
     */
    val deserializationClassLoader: ClassLoader
    /**
     * A whitelist that contains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist
    /**
     * A whitelist that determines (mostly for security purposes) whether a particular encoding may be used when deserializing.
     */
    val encodingWhitelist: EncodingWhitelist
    /**
     * A map of any addition properties specific to the particular use case.
     */
    val properties: Map<Any, Any>
    /**
     * Duplicate references to the same object preserved in the wire format and when deserialized when this is true,
     * otherwise they appear as new copies of the object.
     */
    val objectReferencesEnabled: Boolean

    /**
     * Helper method to return a new context based on this context with the property added.
     */
    fun withProperty(property: Any, value: Any): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with object references disabled.
     */
    fun withoutReferences(): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the deserialization class loader changed.
     */
    fun withClassLoader(classLoader: ClassLoader): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the appropriate class loader constructed from the passed attachment identifiers.
     * (Requires the attachment storage to have been enabled).
     */
    @Throws(MissingAttachmentsException::class)
    fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): CheckpointSerializationContext

    /**
     * Helper method to return a new context based on this context with the given class specifically whitelisted.
     */
    fun withWhitelisted(clazz: Class<*>): CheckpointSerializationContext

    /**
     * A shallow copy of this context but with the given (possibly null) encoding.
     */
    fun withEncoding(encoding: SerializationEncoding?): CheckpointSerializationContext

    /**
     * A shallow copy of this context but with the given encoding whitelist.
     */
    fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist): CheckpointSerializationContext
}

/*
 * The following extension methods are disambiguated from the AMQP-serialization methods by requiring that an
 * explicit [CheckpointSerializationContext] parameter be provided.
 */

/*
 * Convenience extension method for deserializing a ByteSequence, utilising the default factory.
 */
@JvmOverloads
inline fun <reified T : Any> ByteSequence.checkpointDeserialize(
        context: CheckpointSerializationContext = effectiveSerializationEnv.checkpointContext): T {
    return effectiveSerializationEnv.checkpointSerializer.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing SerializedBytes with type matching, utilising the default factory.
 */
@JvmOverloads
inline fun <reified T : Any> SerializedBytes<T>.checkpointDeserialize(
        context: CheckpointSerializationContext = effectiveSerializationEnv.checkpointContext): T {
    return effectiveSerializationEnv.checkpointSerializer.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing a ByteArray, utilising the default factory.
 */
@JvmOverloads
inline fun <reified T : Any> ByteArray.checkpointDeserialize(
        context: CheckpointSerializationContext = effectiveSerializationEnv.checkpointContext): T {
    require(isNotEmpty()) { "Empty bytes" }
    return this.sequence().checkpointDeserialize(context)
}

/**
 * Convenience extension method for serializing an object of type T, utilising the default factory.
 */
@JvmOverloads
fun <T : Any> T.checkpointSerialize(
        context: CheckpointSerializationContext = effectiveSerializationEnv.checkpointContext): SerializedBytes<T> {
    return effectiveSerializationEnv.checkpointSerializer.serialize(this, context)
}