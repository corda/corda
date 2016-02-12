/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.JavaSerializer
import core.SignedWireTransaction
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.crypto.sha256
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType

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
 * Note that this code ignores *ALL* concerns beyond convenience, in particular it ignores:
 *
 * - Performance
 * - Security
 *
 * This code will happily deserialise literally anything, including malicious streams that would reconstruct classes
 * in invalid states, thus violating system invariants. It isn't designed to handle malicious streams and therefore,
 * isn't usable beyond the prototyping stage. But that's fine: we can revisit serialisation technologies later after
 * a formal evaluation process.
 */

// A convenient instance of Kryo pre-configured with some useful things. Used as a default by various functions.
val THREAD_LOCAL_KRYO = ThreadLocal.withInitial { createKryo() }

/**
 * A type safe wrapper around a byte array that contains a serialised object. You can call [SerializedBytes.deserialize]
 * to get the original object back.
 */
class SerializedBytes<T : Any>(bits: ByteArray) : OpaqueBytes(bits) {
    val hash: SecureHash by lazy { bits.sha256() }

    fun writeToFile(path: Path) = Files.write(path, bits)
}

// Some extension functions that make deserialisation convenient and provide auto-casting of the result.
inline fun <reified T : Any> ByteArray.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get(), includeClassName: Boolean = false): T {
    if (includeClassName)
        return kryo.readClassAndObject(Input(this)) as T
    else
        return kryo.readObject(Input(this), T::class.java)
}
inline fun <reified T : Any> OpaqueBytes.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get(), includeClassName: Boolean = false): T {
    return this.bits.deserialize(kryo, includeClassName)
}
inline fun <reified T : Any> SerializedBytes<T>.deserialize(): T = bits.deserialize()

/**
 * Can be called on any object to convert it to a byte array (wrapped by [SerializedBytes]), regardless of whether
 * the type is marked as serializable or was designed for it (so be careful!)
 */
fun <T : Any> T.serialize(kryo: Kryo = THREAD_LOCAL_KRYO.get(), includeClassName: Boolean = false): SerializedBytes<T> {
    val stream = ByteArrayOutputStream()
    Output(stream).use {
        if (includeClassName)
            kryo.writeClassAndObject(it, this)
        else
            kryo.writeObject(it, this)
    }
    return SerializedBytes(stream.toByteArray())
}

/**
 * Serializes properties and deserializes by using the constructor. This assumes that all backed properties are
 * set via the constructor and the class is immutable.
 */
class ImmutableClassSerializer<T : Any>(val klass: KClass<T>) : Serializer<T>() {
    val props = klass.memberProperties.sortedBy { it.name }
    val propsByName = props.associateBy { it.name }
    val constructor = klass.primaryConstructor!!

    init {
        // Verify that this class is immutable (all properties are final)
        assert(props.none { it is KMutableProperty<*> })
    }

    // Just a utility to help us catch cases where nodes are running out of sync versions.
    private fun hashParameters(params: List<KParameter>): Int {
        return params.map {
            (it.name ?: "") + it.index.toString() + it.type.javaType.typeName
        }.hashCode()
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        output.writeVarInt(constructor.parameters.size, true)
        output.writeInt(hashParameters(constructor.parameters))
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
                else -> try {
                    kryo.writeClassAndObject(output, kProperty.get(obj))
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to serialize ${param.name} in ${klass.qualifiedName}", e)
                }
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        assert(type.kotlin == klass)
        val numFields = input.readVarInt(true)
        val fieldTypeHash = input.readInt()

        // A few quick checks for data evolution. Note that this is not guaranteed to catch every problem! But it's
        // good enough for a prototype.
        if (numFields != constructor.parameters.size)
            throw KryoException("Mismatch between number of constructor parameters and number of serialised fields " +
                    "for ${klass.qualifiedName} ($numFields vs ${constructor.parameters.size})")
        if (fieldTypeHash != hashParameters(constructor.parameters))
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

fun createKryo(k: Kryo = Kryo()): Kryo {
    return k.apply {
        // Allow any class to be deserialized (this is insecure but for prototyping we don't care)
        isRegistrationRequired = false
        // Allow construction of objects using a JVM backdoor that skips invoking the constructors, if there is no
        // no-arg constructor available.
        instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())

        register(Arrays.asList( "" ).javaClass, ArraysAsListSerializer());

        // Because we like to stick a Kryo object in a ThreadLocal to speed things up a bit, we can end up trying to
        // serialise the Kryo object itself when suspending a fiber. That's dumb, useless AND can cause crashes, so
        // we avoid it here.
        register(Kryo::class.java, object : Serializer<Kryo>() {
            override fun write(kryo: Kryo, output: Output, obj: Kryo) {
            }

            override fun read(kryo: Kryo, input: Input, type: Class<Kryo>): Kryo {
                return createKryo((Fiber.getFiberSerializer() as KryoSerializer).kryo)
            }
        })

        // Some things where the JRE provides an efficient custom serialisation.
        val ser = JavaSerializer()
        val keyPair = generateKeyPair()
        register(keyPair.public.javaClass, ser)
        register(keyPair.private.javaClass, ser)
        register(Instant::class.java, ser)

        // Some classes have to be handled with the ImmutableClassSerializer because they need to have their
        // constructors be invoked (typically for lazy members).
        val immutables = listOf(
            SignedWireTransaction::class,
            SerializedBytes::class
        )

        immutables.forEach {
            register(it.java, ImmutableClassSerializer(it))
        }

        // TODO: See if we can make Lazy<T> serialize properly so we can use "by lazy" in serialized object.
    }
}
