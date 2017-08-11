package net.corda.core.serialization

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.WriteOnceProperty
import net.corda.core.serialization.SerializationDefaults.P2P_CONTEXT
import net.corda.core.serialization.SerializationDefaults.SERIALIZATION_FACTORY
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.sequence

/**
 * An abstraction for serializing and deserializing objects, with support for versioning of the wire format via
 * a header / prefix in the bytes.
 */
interface SerializationFactory {
    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     */
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization, including the serialization format version.
     */
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}

/**
 * Parameters to serialization and deserialization.
 */
interface SerializationContext {
    /**
     * When serializing, use the format this header sequence represents.
     */
    val preferedSerializationVersion: ByteSequence
    /**
     * The class loader to use for deserialization.
     */
    val deserializationClassLoader: ClassLoader
    /**
     * A whitelist that contains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist
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
     * The use case we are serializing or deserializing for.  See [UseCase].
     */
    val useCase: UseCase
    /**
     * Helper method to return a new context based on this context with the property added.
     */
    fun withProperty(property: Any, value: Any): SerializationContext

    /**
     * Helper method to return a new context based on this context with object references disabled.
     */
    fun withoutReferences(): SerializationContext

    /**
     * Helper method to return a new context based on this context with the deserialization class loader changed.
     */
    fun withClassLoader(classLoader: ClassLoader): SerializationContext

    /**
     * Helper method to return a new context based on this context with the given class specifically whitelisted.
     */
    fun withWhitelisted(clazz: Class<*>): SerializationContext

    /**
     * The use case that we are serializing for, since it influences the implementations chosen.
     */
    enum class UseCase { P2P, RPCServer, RPCClient, Storage, Checkpoint }
}

/**
 * Global singletons to be used as defaults that are injected elsewhere (generally, in the node or in RPC client).
 */
object SerializationDefaults {
    var SERIALIZATION_FACTORY: SerializationFactory by WriteOnceProperty()
    var P2P_CONTEXT: SerializationContext by WriteOnceProperty()
    var RPC_SERVER_CONTEXT: SerializationContext by WriteOnceProperty()
    var RPC_CLIENT_CONTEXT: SerializationContext by WriteOnceProperty()
    var STORAGE_CONTEXT: SerializationContext by WriteOnceProperty()
    var CHECKPOINT_CONTEXT: SerializationContext by WriteOnceProperty()
}

/**
 * Convenience extension method for deserializing a ByteSequence, utilising the defaults.
 */
inline fun <reified T : Any> ByteSequence.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing SerializedBytes with type matching, utilising the defaults.
 */
inline fun <reified T : Any> SerializedBytes<T>.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing a ByteArray, utilising the defaults.
 */
inline fun <reified T : Any> ByteArray.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T = this.sequence().deserialize(serializationFactory, context)

/**
 * Convenience extension method for serializing an object of type T, utilising the defaults.
 */
fun <T : Any> T.serialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): SerializedBytes<T> {
    return serializationFactory.serialize(this, context)
}

/**
 * A type safe wrapper around a byte array that contains a serialised object. You can call [SerializedBytes.deserialize]
 * to get the original object back.
 */
@Suppress("unused") // Type parameter is just for documentation purposes.
class SerializedBytes<T : Any>(bytes: ByteArray) : OpaqueBytes(bytes) {
    // It's OK to use lazy here because SerializedBytes is configured to use the ImmutableClassSerializer.
    val hash: SecureHash by lazy { bytes.sha256() }
}

interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}