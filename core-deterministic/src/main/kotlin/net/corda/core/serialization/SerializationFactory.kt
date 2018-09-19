package net.corda.core.serialization

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.ByteSequence

/**
 * An abstraction for serializing and deserializing objects, with support for versioning of the wire format via
 * a header / prefix in the bytes.
 */
@KeepForDJVM
abstract class SerializationFactory {
    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     */
    abstract fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     * @return deserialized object along with [SerializationContext] to identify encoding used.
     */
    abstract fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): ObjectWithCompatibleContext<T>

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization, including the serialization format version.
     */
    abstract fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>

    /**
     * If there is a need to nest serialization/deserialization with a modified context during serialization or deserialization,
     * this will return the current context used to start serialization/deserialization.
     */
    val currentContext: SerializationContext? get() = _currentContext

    /**
     * A context to use as a default if you do not require a specially configured context.  It will be the current context
     * if the use is somehow nested (see [currentContext]).
     */
    val defaultContext: SerializationContext get() = currentContext ?: effectiveSerializationEnv.p2pContext

    private var _currentContext: SerializationContext? = null

    /**
     * Change the current context inside the block to that supplied.
     */
    fun <T> withCurrentContext(context: SerializationContext?, block: () -> T): T {
        val priorContext = _currentContext
        if (context != null) _currentContext = context
        try {
            return block()
        } finally {
            if (context != null) _currentContext = priorContext
        }
    }

    /**
     * Was intended to allow subclasses to temporarily mark themselves as the current factory for the current thread
     * during serialization/deserialization, restoring the prior context on exiting the block.
     *
     * There is no internal use case for this, and the method now simply runs the provided block.
     */
    @Deprecated("Has no effect, as 'current' factory is now always default factory")
    fun <T> asCurrent(block: SerializationFactory.() -> T): T = block()

    companion object {
        /**
         * A default factory for serialization/deserialization, taking into account the [currentFactory] if set.
         */
        val defaultFactory: SerializationFactory get() = effectiveSerializationEnv.serializationFactory

        /**
         * Always just the default factory.
         */
        @Deprecated("Has no meaning distinct from defaultFactory, which should be used instead")
        val currentFactory: SerializationFactory? get() = defaultFactory
    }
}