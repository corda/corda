package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.util.*

/**
 * The interfaces and classes in this file allow large, singleton style classes to
 * mark themselves as needing converting to some form of token representation in the serialised form
 * and converting back again when deserialising.
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
interface SerializeAsToken {
    fun toToken(context: SerializeAsTokenContext): SerializationToken
}

/**
 * This represents a token in the serialized stream for an instance of a type that implements [SerializeAsToken].
 */
interface SerializationToken {
    fun fromToken(context: SerializeAsTokenContext): Any
}

/**
 * A Kryo serializer for [SerializeAsToken] implementations.
 *
 * This is registered in [createKryo].
 */
class SerializeAsTokenSerializer<T : SerializeAsToken> : Serializer<T>() {
    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.writeClassAndObject(output, obj.toToken(getContext(kryo) ?: throw KryoException("Attempt to write a ${SerializeAsToken::class.simpleName} instance of ${obj.javaClass.name} without initialising a context")))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        val token = (kryo.readClassAndObject(input) as? SerializationToken) ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val fromToken = token.fromToken(getContext(kryo) ?: throw KryoException("Attempt to read a token for a ${SerializeAsToken::class.simpleName} instance of ${type.name} without initialising a context"))
        if (type.isAssignableFrom(fromToken.javaClass)) {
            return type.cast(fromToken)
        } else {
            throw KryoException("Token read ($token) did not return expected tokenized type: ${type.name}")
        }
    }

    companion object {
        private fun getContext(kryo: Kryo): SerializeAsTokenContext? = kryo.context.get(SerializeAsTokenContext::class.java) as? SerializeAsTokenContext

        fun setContext(kryo: Kryo, context: SerializeAsTokenContext) {
            kryo.context.put(SerializeAsTokenContext::class.java, context)
        }

        fun clearContext(kryo: Kryo) {
            kryo.context.remove(SerializeAsTokenContext::class.java)
        }
    }
}

/**
 * A context for mapping SerializationTokens to/from SerializeAsTokens.
 *
 * A context is initialised with an object containing all the instances of [SerializeAsToken] to eagerly register all the tokens.
 * In our case this can be the [ServiceHub].
 *
 * Then it is a case of using the companion object methods on [SerializeAsTokenSerializer] to set and clear context as necessary
 * on the Kryo instance when serializing to enable/disable tokenization.
 */
class SerializeAsTokenContext(toBeTokenized: Any, kryo: Kryo = createKryo()) {
    internal val tokenToTokenized = HashMap<SerializationToken, SerializeAsToken>()
    internal var readOnly = false

    init {
        /*
         * Go ahead and eagerly serialize the object to register all of the tokens in the context.
         *
         * This results in the toToken() method getting called for any [SerializeAsStringToken] instances which
         * are encountered in the object graph as they are serialized by Kryo and will therefore register the token to
         * object mapping for those instances.  We then immediately set the readOnly flag to stop further adhoc or
         * accidental registrations from occuring as these could not be deserialized in a deserialization-first
         * scenario if they are not part of this iniital context construction serialization.
         */
        SerializeAsTokenSerializer.setContext(kryo, this)
        toBeTokenized.serialize(kryo)
        SerializeAsTokenSerializer.clearContext(kryo)
        readOnly = true
    }
}

/**
 * A class representing a [SerializationToken] for some object that is not serializable but can be looked up
 * (when deserialized) via just the class name.
 */
data class SingletonSerializationToken private constructor(private val className: String) : SerializationToken {

    constructor(toBeTokenized: SerializeAsToken) : this(toBeTokenized.javaClass.name)

    override fun fromToken(context: SerializeAsTokenContext): Any = context.tokenToTokenized[this] ?:
            throw IllegalStateException("Unable to find tokenized instance of $className in context $context")

    companion object {
        fun registerWithContext(token: SingletonSerializationToken, toBeTokenized: SerializeAsToken, context: SerializeAsTokenContext): SerializationToken =
                if (token in context.tokenToTokenized) token else registerNewToken(token, toBeTokenized, context)

        // Only allowable if we are in SerializeAsTokenContext init (readOnly == false)
        private fun registerNewToken(token: SingletonSerializationToken, toBeTokenized: SerializeAsToken, context: SerializeAsTokenContext): SerializationToken {
            if (context.readOnly) throw UnsupportedOperationException("Attempt to write token for lazy registered ${toBeTokenized.javaClass.name}. " +
                    "All tokens should be registered during context construction.")
            context.tokenToTokenized[token] = toBeTokenized
            return token
        }
    }
}

/**
 * A base class for implementing large objects / components / services that need to serialize themselves to a string token
 * to indicate which instance the token is a serialized form of.
 */
abstract class SingletonSerializeAsToken() : SerializeAsToken {
    @Suppress("LeakingThis")
    private val token = SingletonSerializationToken(this)

    override fun toToken(context: SerializeAsTokenContext) = SingletonSerializationToken.registerWithContext(token, this, context)
}
