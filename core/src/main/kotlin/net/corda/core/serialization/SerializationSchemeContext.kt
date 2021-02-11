package net.corda.core.serialization

import net.corda.core.DoNotImplement

/**
 * This is used to pass information into [CustomSerializationScheme] about how the object should be (de)serialized.
 * This context can change depending on the specific circumstances in the node when (de)serialization occurs.
 */
@DoNotImplement
interface SerializationSchemeContext {
    /**
     * The class loader to use for deserialization. This is guaranteed to be able to load all the required classes passed into
     * [CustomSerializationScheme.deserialize].
     */
    val deserializationClassLoader: ClassLoader
    /**
     * A whitelist that contains (mostly for security purposes) which classes are authorised to be deserialized.
     * A secure implementation will not instantiate any object which is not either whitelisted or annotated with [CordaSerializable] when
     * deserializing. To catch classes missing from the whitelist as early as possible it is HIGHLY recommended to also check this
     * whitelist when serializing (as well as deserializing) objects.
     */
    val whitelist: ClassWhitelist
    /**
     * A map of any additional properties specific to the particular use case. If these properties are set via
     * [toWireTransaction][net.corda.core.transactions.TransactionBuilder.toWireTransaction] then they might not be available when
     * deserializing. If the properties are required when deserializing, they can be added into the blob when serializing and read back
     * when deserializing.
     */
    val properties: Map<Any, Any>
}