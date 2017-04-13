package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.pool.KryoPool
import com.google.common.collect.Multimaps
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken
import org.apache.commons.lang3.ClassUtils
import java.util.*
import java.util.Collections.newSetFromMap
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

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
@CordaSerializable
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
class SerializeAsTokenContext(toBeTokenized: Any, kryoPool: KryoPool) {
    private val classNameToSingletons = Multimaps.newMultimap<String, SerializeAsToken>(HashMap(), { newSetFromMap(IdentityHashMap()) })
    private var readOnly = false

    init {
        /**
         * Go ahead and eagerly serialize the object to register all of the tokens in the context.
         *
         * This results in the toToken() method getting called for any [SingletonSerializeAsToken] instances which
         * are encountered in the object graph as they are serialized by Kryo and will therefore register the token to
         * object mapping for those instances.  We then immediately set the readOnly flag to stop further adhoc or
         * accidental registrations from occuring as these could not be deserialized in a deserialization-first
         * scenario if they are not part of this iniital context construction serialization.
         */
        kryoPool.run { kryo ->
            SerializeAsTokenSerializer.setContext(kryo, this)
            toBeTokenized.serialize(kryo)
            SerializeAsTokenSerializer.clearContext(kryo)
        }
        readOnly = true
    }

    // Only allowable if we are in SerializeAsTokenContext init (readOnly == false)
    internal fun putSingleton(toBeTokenized: SerializeAsToken) {
        val impl = toBeTokenized.javaClass
        if (toBeTokenized !in classNameToSingletons[impl.name]) {
            if (readOnly) {
                throw UnsupportedOperationException("Attempt to write token for lazy registered ${impl.name}. All tokens should be registered during context construction.")
            }
            ClassUtils.hierarchy(impl, ClassUtils.Interfaces.INCLUDE).forEach { classNameToSingletons.put(it.name, toBeTokenized) }
        }
    }

    private fun noSuchSingleton(className: String): Nothing = throw IllegalStateException("Unable to find tokenized instance of $className in context $this")

    internal fun uniqueSingleton(className: String, exactMatch: Boolean): Any {
        val singletons = classNameToSingletons[className]
        if (1 != singletons.size) noSuchSingleton(className)
        val singleton = singletons.first()
        if (exactMatch && singleton.javaClass.name != className) noSuchSingleton(className)
        return singleton
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> uniqueSingletonImplementing(clazz: KClass<T>) = uniqueSingleton(clazz.java.name, false) as T
}

/**
 * A class representing a [SerializationToken] for some object that is not serializable but can be looked up
 * (when deserialized) via just the class name.
 */
class SingletonSerializationToken private constructor(private val className: String) : SerializationToken {

    override fun fromToken(context: SerializeAsTokenContext) = context.uniqueSingleton(className, true)

    fun registerWithContext(context: SerializeAsTokenContext, toBeTokenized: SerializeAsToken) = also { context.putSingleton(toBeTokenized) }

    companion object {
        fun <T : SerializeAsToken> singletonSerializationToken(toBeTokenized: Class<T>) = SingletonSerializationToken(toBeTokenized.name)
    }
}

/**
 * A base class for implementing large objects / components / services that need to serialize themselves to a string token
 * to indicate which instance the token is a serialized form of.
 */
abstract class SingletonSerializeAsToken : SerializeAsToken {
    private val token = singletonSerializationToken(javaClass)

    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)
}
