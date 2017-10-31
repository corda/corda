package net.corda.core.serialization

/**
 * The interfaces and classes in this file allow large, singleton style classes to
 * mark themselves as needing converting to some form of token representation in the serialised form
 * and converting back again when deserializing.
 *
 * Typically these classes would be used for node services and subsystems that might become reachable from
 * Fibers and thus sucked into serialization when they are checkpointed.
 */

/**
 * This interface should be implemented by classes that want to substitute a token representation of themselves if
 * they are serialized because they have a lot of internal state that does not serialize (well).
 *
 * This models a similar pattern to the readReplace/writeReplace methods in Java serialization.
 */
@CordaSerializable
interface SerializeAsToken

/**
 * A context for mapping SerializationTokens to/from SerializeAsTokens.
 */
interface SerializeAsTokenContext {
    fun putSingleton(toBeTokenized: SerializeAsToken)
    fun getSingleton(className: String): SerializeAsToken
}

/**
 * A class representing a serialization token for some object that is not serializable but can be looked up
 * (when deserialized) via just the class name.
 */
class SingletonSerializationToken private constructor(val className: String) {
    companion object {
        fun <T : SerializeAsToken> singletonSerializationToken(toBeTokenized: Class<T>) = SingletonSerializationToken(toBeTokenized.name)
    }
}

/**
 * A base class for implementing large objects / components / services that need to serialize themselves to a string token
 * to indicate which instance the token is a serialized form of.
 */
abstract class SingletonSerializeAsToken : SerializeAsToken
