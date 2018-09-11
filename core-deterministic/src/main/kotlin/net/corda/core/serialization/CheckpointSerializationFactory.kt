package net.corda.core.serialization

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.ByteSequence
import java.io.NotSerializableException

/**
 * A deterministic version of [CheckpointSerializationFactory] that does not use thread-locals to manage serialization
 * context.
 */
@KeepForDJVM
class CheckpointSerializationFactory(
        private val scheme: CheckpointSerializationScheme
) {

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: CheckpointSerializationContext): T {
        return asCurrent { withCurrentContext(context) { scheme.deserialize(byteSequence, clazz, context) } }
    }

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization, including the serialization format version.
     */
    fun <T : Any> serialize(obj: T, context: CheckpointSerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { scheme.serialize(obj, context) } }
    }

    override fun toString(): String {
        return "${this.javaClass.name} scheme=$scheme ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is CheckpointSerializationFactory && other.scheme == this.scheme
    }

    override fun hashCode(): Int = scheme.hashCode()

    private var _currentContext: CheckpointSerializationContext? = null

    /**
     * Change the current context inside the block to that supplied.
     */
    fun <T> withCurrentContext(context: CheckpointSerializationContext?, block: () -> T): T {
        val priorContext = _currentContext
        if (context != null) _currentContext = context
        try {
            return block()
        } finally {
            if (context != null) _currentContext = priorContext
        }
    }

    /**
     * Allow subclasses to temporarily mark themselves as the current factory for the current thread during serialization/deserialization.
     * Will restore the prior context on exiting the block.
     */
    fun <T> asCurrent(block: CheckpointSerializationFactory.() -> T): T {
        val priorContext = _currentFactory
        _currentFactory = this
        try {
            return this.block()
        } finally {
            _currentFactory = priorContext
        }
    }

    companion object {
        private var _currentFactory: CheckpointSerializationFactory? = null

        /**
         * A default factory for serialization/deserialization, taking into account the [currentFactory] if set.
         */
        val defaultFactory: CheckpointSerializationFactory get() = currentFactory ?: effectiveSerializationEnv.checkpointSerializationFactory

        /**
         * If there is a need to nest serialization/deserialization with a modified context during serialization or deserialization,
         * this will return the current factory used to start serialization/deserialization.
         */
        val currentFactory: CheckpointSerializationFactory? get() = _currentFactory
    }
}