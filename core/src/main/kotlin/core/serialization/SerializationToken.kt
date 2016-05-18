package core.serialization

import com.esotericsoftware.kryo.DefaultSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.ref.WeakReference
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
 *
 * With Kryo serialisation, these classes should also annotate themselves with <code>@DefaultSerializer</code>.  See below.
 *
 */
interface SerializeAsToken {
    val token: SerializationToken
}

/**
 * This represents a token in the serialized stream for an instance of a type that implements [SerializeAsToken]
 */
interface SerializationToken {
    fun fromToken(): Any
}

/**
 * A Kryo serializer for [SerializeAsToken] implementations.
 *
 * Annotate the [SerializeAsToken] with <code>@DefaultSerializer(SerializeAsTokenSerializer::class)</code>
 */
class SerializeAsTokenSerializer<T : SerializeAsToken> : Serializer<T>() {
    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.writeClassAndObject(output, obj.token)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        val token = (kryo.readClassAndObject(input) as? SerializationToken) ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val fromToken = token.fromToken()
        if (type.isAssignableFrom(fromToken.javaClass)) {
            return type.cast(fromToken)
        } else {
            throw KryoException("Token read did not return tokenized type: ${type.name}")
        }
    }
}

/**
 * A class representing a [SerializationToken] for some object that is not serializable but can be re-created or looked up
 * (when deserialized) via a [String] key.
 */
private data class SerializationStringToken(private val key: String, private val className: String) : SerializationToken {

    constructor(key: String, toBeProxied: SerializeAsStringToken) : this(key, toBeProxied.javaClass.name) {
        tokenized.put(this, WeakReference(toBeProxied))
    }

    companion object {
        val tokenized = Collections.synchronizedMap(WeakHashMap<SerializationStringToken, WeakReference<SerializeAsStringToken>>())
    }

    override fun fromToken(): Any = tokenized.get(this)?.get() ?:
            throw IllegalStateException("Unable to find tokenized instance of ${className} for key $key")
}

/**
 * A base class for implementing large objects / components / services that need to serialize themselves to a string token
 * to indicate which instance the token is a serialized form of.
 *
 * This class will also double check that the class is annotated for Kryo serialization.  Note it does this on every
 * instance constructed but given this is designed to represent heavyweight services or components, this should not be significant.
 */
abstract class SerializeAsStringToken(val key: String) : SerializeAsToken {

    init {
        // Verify we have the annotation
        val annotation = javaClass.getAnnotation(DefaultSerializer::class.java)
        if (annotation == null || annotation.value.java.name != SerializeAsTokenSerializer::class.java.name) {
            throw IllegalStateException("${this.javaClass.name} is not annotated with @${DefaultSerializer::class.java.simpleName} set to ${SerializeAsTokenSerializer::class.java.simpleName}")
        }
    }

    override val token: SerializationToken = SerializationStringToken(key, this)
}