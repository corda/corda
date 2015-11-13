package core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.JavaSerializer
import core.Amount
import core.InstitutionReference
import core.OpaqueBytes
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.security.PublicKey
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.jvm.javaType
import kotlin.reflect.memberProperties
import kotlin.reflect.primaryConstructor

/**
 * Serialization utilities, using the Kryo framework with a custom serialiser for immutable data classes and a dead
 * simple, totally non-extensible binary (sub)format.
 *
 * This is NOT what should be used in any final platform product, rather, the final state should be a precisely
 * specified and standardised binary format with attention paid to anti-malleability, versioning and performance.
 * FIX SBE is a potential candidate: it prioritises performance over convenience and was designed for HFT. Google
 * Protocol Buffers with a minor tightening to make field reordering illegal is another possibility.
 *
 * FIX SBE:
 *     https://real-logic.github.io/simple-binary-encoding/
 *     http://mechanical-sympathy.blogspot.co.at/2014/05/simple-binary-encoding.html
 * Protocol buffers:
 *     https://developers.google.com/protocol-buffers/
 *
 * But for now we use Kryo to maximise prototyping speed.
 *
 * The goals of this code are twofold:
 *
 *   1) Security
 *   2) Convenience
 *
 * in that order.
 *
 * SECURITY
 * --------
 *
 * Even though this is prototype code, we should still respect the Java Secure Coding Guidelines and the advice it
 * gives for the use of object graph serialisation:
 *
 *    http://www.oracle.com/technetwork/java/seccodeguide-139067.html
 *
 * Object graph serialisation is convenient but has a long history of exposing apps to security holes when type system
 * invariants are violated by objects being reconstructed unexpectedly, or in illegal states.
 *
 * Therefore we take the following measures:
 *
 *   - The DataClassSerializer knows how to build deserialised objects using the primary constructor. Any invariants
 *     enforced by this constructor are therefore enforced (in Kotlin this means logic inside an init{} block, in
 *     Java it means any code in the only defined constructor).
 *   - The DCS asserts that Kryo is configured to only deserialise registered classes. Every class that might appear
 *     in a stream must be specified up front: Kryo will not rummage through the classpath to find any arbitrary
 *     class the stream happens to mention. This improves both performance and security at a loss of developer
 *     convenience.
 *
 * The DCS is intended to be used with classes that meet the following constraints:
 *
 *   - Must be immutable: all properties are final. Note that Kotlin never generates bare public fields, but we should
 *     add some checks for this being done anyway for cases where a contract is defined using Java.
 *   - The only properties that exist must be arguments to the constructor. This will need to be relaxed to allow
 *     for pure-code (no backing state) getters, and to support constant fields like legalContractRef.
 *   - Requires that the data class be marked as intended for serialization using a marker interface.
 *
 *
 * CONVENIENCE
 * -----------
 *
 * We define a few utilities to make using Kryo convenient.
 *
 * The kryo() function returns a pre-configured Kryo class with a very small number of classes registered, that are
 * known to be safe.
 *
 * A serialize() extension function is added to the SerializeableWithKryo marker interface. This is intended for
 * serializing immutable data classes (i.e immutable javabeans). A deserialize() method is added to ByteArray to
 * get the given class back from the stream. A Kryo.registerDataClass<>() function is added to clean up the Java
 * syntax a bit:
 *
 *     data class Person(val name: String, val birthdate: Instant?) : SerializeableWithKryo
 *
 *     val kryo = kryo()
 *     kryo.registerDataClass<Person>()
 *
 *     val bits: ByteArray = somePerson.serialize(kryo)
 *     val person2 = bits.deserialize<Person>(kryo)
 *
 */

interface SerializeableWithKryo

class DataClassSerializer<T : SerializeableWithKryo>(val klass: KClass<T>) : Serializer<T>() {
    val props = klass.memberProperties.sortedBy { it.name }
    val propsByName = props.toMapBy { it.name }
    val constructor = klass.primaryConstructor!!

    init {
        // Verify that this class is safe to serialise.
        //
        // 1) No properties that aren't in the constructor.
        // 2) Objects are immutable (all properties are final)
        assert(props.size == constructor.parameters.size)
        assert(props.map { it.name }.toSortedSet() == constructor.parameters.map { it.name }.toSortedSet())
        assert(props.none { it is KMutableProperty<*> })
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        output.writeVarInt(constructor.parameters.size, true)
        output.writeInt(constructor.parameters.hashCode())
        for (param in constructor.parameters) {
            val kProperty = propsByName[param.name!!]!!
            when (param.type.javaType.typeName) {
                "int" -> output.writeVarInt(kProperty.get(obj) as Int, true)
                "long" -> output.writeVarLong(kProperty.get(obj) as Long, true)
                "short" -> output.writeShort(kProperty.get(obj) as Int)
                "char" -> output.writeChar(kProperty.get(obj) as Char)
                "byte" -> output.writeByte(kProperty.get(obj) as Byte)
                "double" -> output.writeDouble(kProperty.get(obj) as Double)
                "float" -> output.writeFloat(kProperty.get(obj) as Float)
                else -> kryo.writeClassAndObject(output, kProperty.get(obj))
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        assert(type.kotlin == klass)
        assert(kryo.isRegistrationRequired)
        val numFields = input.readVarInt(true)
        val fieldTypeHash = input.readInt()

        // A few quick checks for data evolution. Note that this is not guaranteed to catch every problem! But it's
        // good enough for a prototype.
        if (numFields != constructor.parameters.size)
            throw KryoException("Mismatch between number of constructor parameters and number of serialised fields for ${klass.qualifiedName} ($numFields vs ${constructor.parameters.size})")
        if (fieldTypeHash != constructor.parameters.hashCode())
            throw KryoException("Hashcode mismatch for parameter types for ${klass.qualifiedName}: unsupported type evolution has happened.")

        val args = arrayOfNulls<Any?>(numFields)
        var cursor = 0
        for (param in constructor.parameters) {
            args[cursor++] = when (param.type.javaType.typeName) {
                "int" -> input.readVarInt(true)
                "long" -> input.readVarLong(true)
                "short" -> input.readShort()
                "char" -> input.readChar()
                "byte" -> input.readByte()
                "double" -> input.readDouble()
                "float" -> input.readFloat()
                else -> kryo.readClassAndObject(input)
            }
        }
        // If the constructor throws an exception, pass it through instead of wrapping it.
        return try { constructor.call(*args) } catch (e: InvocationTargetException) { throw e.cause!! }
    }
}

inline fun <reified T : SerializeableWithKryo> Kryo.registerDataClass() = register(T::class.java, DataClassSerializer(T::class))
inline fun <reified T : SerializeableWithKryo> ByteArray.deserialize(kryo: Kryo): T = kryo.readObject(Input(this), T::class.java)

fun SerializeableWithKryo.serialize(kryo: Kryo): ByteArray {
    val stream = ByteArrayOutputStream()
    Output(stream).use {
        kryo.writeObject(it, this)
    }
    return stream.toByteArray()
}

fun kryo(): Kryo {
    return Kryo().apply {
        isRegistrationRequired = true
        register(ByteArray::class.java)
        register(IntArray::class.java)

        // These JDK classes use a very minimal custom serialization format and are written to defend against malicious
        // streams, so we can just kick it over to java serialization.
        register(Instant::class.java, JavaSerializer())
        register(PublicKey::class.java, JavaSerializer())

        // Now register platform types.
        registerDataClass<Amount>()
        registerDataClass<InstitutionReference>()
        registerDataClass<OpaqueBytes>()
    }
}