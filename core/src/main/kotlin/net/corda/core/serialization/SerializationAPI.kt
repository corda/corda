/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:KeepForDJVM
package net.corda.core.serialization

import net.corda.core.CordaInternal
import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.sequence
import java.sql.Blob

data class ObjectWithCompatibleContext<out T : Any>(val obj: T, val context: SerializationContext)

/**
 * An abstraction for serializing and deserializing objects, with support for versioning of the wire format via
 * a header / prefix in the bytes.
 */
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
    val currentContext: SerializationContext? get() = _currentContext.get()

    /**
     * A context to use as a default if you do not require a specially configured context.  It will be the current context
     * if the use is somehow nested (see [currentContext]).
     */
    val defaultContext: SerializationContext get() = currentContext ?: effectiveSerializationEnv.p2pContext

    private val _currentContext = ThreadLocal<SerializationContext?>()

    /**
     * Change the current context inside the block to that supplied.
     */
    fun <T> withCurrentContext(context: SerializationContext?, block: () -> T): T {
        val priorContext = _currentContext.get()
        if (context != null) _currentContext.set(context)
        try {
            return block()
        } finally {
            if (context != null) _currentContext.set(priorContext)
        }
    }

    /**
     * Allow subclasses to temporarily mark themselves as the current factory for the current thread during serialization/deserialization.
     * Will restore the prior context on exiting the block.
     */
    fun <T> asCurrent(block: SerializationFactory.() -> T): T {
        val priorContext = _currentFactory.get()
        _currentFactory.set(this)
        try {
            return this.block()
        } finally {
            _currentFactory.set(priorContext)
        }
    }

    companion object {
        private val _currentFactory = ThreadLocal<SerializationFactory?>()

        /**
         * A default factory for serialization/deserialization, taking into account the [currentFactory] if set.
         */
        val defaultFactory: SerializationFactory get() = currentFactory ?: effectiveSerializationEnv.serializationFactory

        /**
         * If there is a need to nest serialization/deserialization with a modified context during serialization or deserialization,
         * this will return the current factory used to start serialization/deserialization.
         */
        val currentFactory: SerializationFactory? get() = _currentFactory.get()
    }
}
typealias SerializationMagic = ByteSequence
@DoNotImplement
interface SerializationEncoding

/**
 * Parameters to serialization and deserialization.
 */
@KeepForDJVM
@DoNotImplement
interface SerializationContext {
    /**
     * When serializing, use the format this header sequence represents.
     */
    val preferredSerializationVersion: SerializationMagic
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
     * If true the carpenter will happily synthesis classes that implement interfaces containing methods that are not
     * getters for any AMQP fields. Invoking these methods will throw an [AbstractMethodError]. If false then an exception
     * will be thrown during deserialization instead.
     *
     * The default is false.
     */
    val lenientCarpenterEnabled: Boolean
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
     * Return a new context based on this one but with a lenient carpenter.
     * @see lenientCarpenterEnabled
     */
    fun withLenientCarpenter(): SerializationContext

    /**
     * Helper method to return a new context based on this context with the deserialization class loader changed.
     */
    fun withClassLoader(classLoader: ClassLoader): SerializationContext

    /**
     * Helper method to return a new context based on this context with the appropriate class loader constructed from the passed attachment identifiers.
     * (Requires the attachment storage to have been enabled).
     */
    @Throws(MissingAttachmentsException::class)
    fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext

    /**
     * Helper method to return a new context based on this context with the given class specifically whitelisted.
     */
    fun withWhitelisted(clazz: Class<*>): SerializationContext

    /**
     * Helper method to return a new context based on this context but with serialization using the format this header sequence represents.
     */
    fun withPreferredSerializationVersion(magic: SerializationMagic): SerializationContext

    /**
     * A shallow copy of this context but with the given (possibly null) encoding.
     */
    fun withEncoding(encoding: SerializationEncoding?): SerializationContext

    /**
     * A shallow copy of this context but with the given encoding whitelist.
     */
    fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist): SerializationContext

    /**
     * The use case that we are serializing for, since it influences the implementations chosen.
     */
    @KeepForDJVM
    enum class UseCase { P2P, RPCServer, RPCClient, Storage, Checkpoint, Testing }
}

/**
 * Set of well known properties that may be set on a serialization context. This doesn't preclude
 * others being set that aren't keyed on this enumeration, but for general use properties adding a
 * well known key here is preferred.
 */
@KeepForDJVM
enum class ContextPropertyKeys {
    SERIALIZERS
}

/**
 * Global singletons to be used as defaults that are injected elsewhere (generally, in the node or in RPC client).
 */
@KeepForDJVM
object SerializationDefaults {
    val SERIALIZATION_FACTORY get() = effectiveSerializationEnv.serializationFactory
    val P2P_CONTEXT get() = effectiveSerializationEnv.p2pContext
    @DeleteForDJVM val RPC_SERVER_CONTEXT get() = effectiveSerializationEnv.rpcServerContext
    @DeleteForDJVM val RPC_CLIENT_CONTEXT get() = effectiveSerializationEnv.rpcClientContext
    @DeleteForDJVM val STORAGE_CONTEXT get() = effectiveSerializationEnv.storageContext
    @DeleteForDJVM val CHECKPOINT_CONTEXT get() = effectiveSerializationEnv.checkpointContext
}

/**
 * Convenience extension method for deserializing a ByteSequence, utilising the defaults.
 */
inline fun <reified T : Any> ByteSequence.deserialize(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                                                      context: SerializationContext = serializationFactory.defaultContext): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Additionally returns [SerializationContext] which was used for encoding.
 * It might be helpful to know [SerializationContext] to use the same encoding in the reply.
 */
inline fun <reified T : Any> ByteSequence.deserializeWithCompatibleContext(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                                                                           context: SerializationContext = serializationFactory.defaultContext): ObjectWithCompatibleContext<T> {
    return serializationFactory.deserializeWithCompatibleContext(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing SerializedBytes with type matching, utilising the defaults.
 */
inline fun <reified T : Any> SerializedBytes<T>.deserialize(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                                                            context: SerializationContext = serializationFactory.defaultContext): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing a ByteArray, utilising the defaults.
 */
inline fun <reified T : Any> ByteArray.deserialize(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                                                   context: SerializationContext = serializationFactory.defaultContext): T {
    require(isNotEmpty()) { "Empty bytes" }
    return this.sequence().deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for deserializing a JDBC Blob, utilising the defaults.
 */
@DeleteForDJVM
inline fun <reified T : Any> Blob.deserialize(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                                              context: SerializationContext = serializationFactory.defaultContext): T {
    return this.getBytes(1, this.length().toInt()).deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for serializing an object of type T, utilising the defaults.
 */
fun <T : Any> T.serialize(serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                          context: SerializationContext = serializationFactory.defaultContext): SerializedBytes<T> {
    return serializationFactory.serialize(this, context)
}

/**
 * A type safe wrapper around a byte array that contains a serialised object. You can call [SerializedBytes.deserialize]
 * to get the original object back.
 */
@Suppress("unused")
@KeepForDJVM
class SerializedBytes<T : Any>(bytes: ByteArray) : OpaqueBytes(bytes) {
    companion object {
        /**
         * Serializes the given object and returns a [SerializedBytes] wrapper for it. An alias for [Any.serialize]
         * intended to make the calling smoother for Java users.
         *
         * TODO: Take out the @CordaInternal annotation post-Enterprise GA when we can add API again.
         *
         * @suppress
         */
        @JvmStatic
        @CordaInternal
        @JvmOverloads
        fun <T : Any> from(obj: T, serializationFactory: SerializationFactory = SerializationFactory.defaultFactory,
                           context: SerializationContext = serializationFactory.defaultContext): SerializedBytes<T> {
            return obj.serialize(serializationFactory, context)
        }
    }

    // It's OK to use lazy here because SerializedBytes is configured to use the ImmutableClassSerializer.
    val hash: SecureHash by lazy { bytes.sha256() }
}

@KeepForDJVM
interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}

@KeepForDJVM
@DoNotImplement
interface EncodingWhitelist {
    fun acceptEncoding(encoding: SerializationEncoding): Boolean
}
