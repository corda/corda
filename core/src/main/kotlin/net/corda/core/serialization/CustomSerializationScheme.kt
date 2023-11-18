package net.corda.core.serialization

import net.corda.core.utilities.ByteSequence
import java.io.NotSerializableException

/***
 * Implement this interface to add your own Serialization Scheme. This is an experimental feature. All methods in this class MUST be
 * thread safe i.e. methods from the same instance of this class can be called in different threads simultaneously.
 */
interface CustomSerializationScheme {
    /**
     * This method must return an id used to uniquely identify the Scheme. This should be unique within a network as serialized data might
     * be sent over the wire.
     */
    fun getSchemeId(): Int

    /**
     * This method must deserialize the data stored [bytes] into an instance of [T].
     *
     * @param bytes the serialized data.
     * @param clazz the class to instantiate.
     * @param context used to pass information about how the object should be deserialized.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationSchemeContext): T

    /**
     * This method must be able to serialize any object [T] into a ByteSequence.
     *
     * @param obj the object to be serialized.
     * @param context used to pass information about how the object should be serialized.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence
}