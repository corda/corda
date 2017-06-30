package net.corda.core.serialization

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.SerializationDefaults.P2P_CONTEXT
import net.corda.core.serialization.SerializationDefaults.SERIALIZATION_FACTORY
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.WriteOnceProperty
import net.corda.core.utilities.sequence
import java.io.NotSerializableException
import java.nio.file.Files
import java.nio.file.Path

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
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization, including the serialization format version.
     */
    @Throws(NotSerializableException::class)
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
     * A whitelist that contrains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist
    /**
     * A map of any addition properties specific to the particular use case.
     */
    val properties: Map<Any, Any>
    /**
     * Are duplicate references to the same object preserved in the wire format and when deserialized.
     */
    val objectReferencesEnabled: Boolean
    /**
     * What is the use case we are serializing or deserializing for.  See [UseCase].
     */
    val useCase: UseCase

    /**
     * A number of helper methods for creating new contexts based on this one but with the following alterations.
     */
    fun withProperty(property: Any, value: Any): SerializationContext

    fun withoutReferences(): SerializationContext
    fun withClassLoader(classLoader: ClassLoader): SerializationContext
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
 * Convenience extension methods utilising the defaults.
 */

inline fun <reified T : Any> ByteSequence.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

inline fun <reified T : Any> SerializedBytes<T>.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

fun <T : Any> T.serialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): SerializedBytes<T> {
    return serializationFactory.serialize(this, context)
}

inline fun <reified T : Any> ByteArray.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): T = this.sequence().deserialize(serializationFactory, context)


/**
 * A type safe wrapper around a byte array that contains a serialised object. You can call [SerializedBytes.deserialize]
 * to get the original object back.
 */
@Suppress("unused") // Type parameter is just for documentation purposes.
class SerializedBytes<T : Any>(bytes: ByteArray) : OpaqueBytes(bytes) {
    // It's OK to use lazy here because SerializedBytes is configured to use the ImmutableClassSerializer.
    val hash: SecureHash by lazy { bytes.sha256() }

    fun writeToFile(path: Path): Path = Files.write(path, bytes)
}

// The more specific deserialize version results in the bytes being cached, which is faster.
@JvmName("SerializedBytesWireTransaction")
fun SerializedBytes<WireTransaction>.deserialize(serializationFactory: SerializationFactory = SERIALIZATION_FACTORY, context: SerializationContext = P2P_CONTEXT): WireTransaction = WireTransaction.deserialize(this, serializationFactory, context)
