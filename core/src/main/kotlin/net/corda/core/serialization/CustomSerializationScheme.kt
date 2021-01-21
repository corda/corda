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
    fun getSerializationMagic(): CustomSerializationMagic

    /**
     * This method must deserialize (any) object from SerializedBytes.
     */
    @Throws(NotSerializableException::class)
    fun deserialize(bytes: SerializedBytes<*>, clazz: Class<*>, context: CustomSerializationContext): Any

    /**
     * This method must serialize (any) object into SerializedBytes.
     */
    @Throws(NotSerializableException::class)
    fun serialize(obj: Any, context: CustomSerializationContext): SerializedBytes<*>
}

class CustomSerializationMagic(val magicNumber: Int)

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