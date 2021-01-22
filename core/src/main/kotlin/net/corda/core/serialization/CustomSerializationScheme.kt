package net.corda.core.serialization

import java.io.NotSerializableException

/***
 * Implement this interface to add your own Serialization Scheme.
 * This is an experimental feature.
 */
interface CustomSerializationScheme {
    /**
     * This must return a magic number used to uniquely (within a network) identify the Scheme.
     */
    fun getSchemeId(): Int

    /**
     * This method must deserialize (any) object from SerializedBytes.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: SerializedBytes<T>, clazz: Class<T>, context: CustomSerializationContext): T

    /**
     * This method must serialize (any) object into SerializedBytes.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: CustomSerializationContext): SerializedBytes<T>
}

interface CustomSerializationContext {
    /**
     * The class loader to use for deserialization.
     */
    val deserializationClassLoader: ClassLoader
    /**
     * A whitelist that contains (mostly for security purposes) which classes can be serialized and deserialized.
     */
    val whitelist: ClassWhitelist
}
