/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.JavaSerializer
import contracts.Cash
import contracts.CommercialPaper
import contracts.CrowdFund
import core.*
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import java.util.*
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
 *   - The ImmutableClassSerializer knows how to build deserialised objects using the primary constructor. Any invariants
 *     enforced by this constructor are therefore enforced (in Kotlin this means logic inside an init{} block, in
 *     Java it means any code in the only defined constructor).
 *   - The ICS asserts that Kryo is configured to only deserialise registered classes. Every class that might appear
 *     in a stream must be specified up front: Kryo will not rummage through the classpath to find any arbitrary
 *     class the stream happens to mention. This improves both performance and security at a loss of developer
 *     convenience.
 *
 * The ICS is intended to be used with classes that meet the following constraints:
 *
 *   - Must be immutable: all properties are final. Note that Kotlin never generates bare public fields, but we should
 *     add some checks for this being done anyway for cases where a contract is defined using Java.
 *   - Requires that the data class be marked as intended for serialization using a marker interface.
 *   - Properties that are not in the constructor are not serialised (but as they are final, they must be either
 *     initialised to a constant or derived from the constructor arguments unless they are reading external state,
 *     which is intended to be forbidden).
 *
 *
 * CONVENIENCE
 * -----------
 *
 * We define a few utilities to make using Kryo convenient.
 *
 * The createKryo() function returns a pre-configured Kryo class with a very small number of classes registered, that
 * are known to be safe.
 *
 * A serialize() extension function is added to the SerializeableWithKryo marker interface. This is intended for
 * serializing immutable data classes (i.e immutable javabeans). A deserialize() method is added to ByteArray to
 * get the given class back from the stream. A Kryo.registerImmutableClass<>() function is added to clean up the Java
 * syntax a bit:
 *
 *     data class Person(val name: String, val birthdate: Instant?) : SerializeableWithKryo
 *
 *     val kryo = kryo()
 *     kryo.registerImmutableClass<Person>()
 *
 *     val bits: ByteArray = somePerson.serialize(kryo)
 *     val person2 = bits.deserialize<Person>(kryo)
 *
 */


/**
 * Marker interface for classes to use with [ImmutableClassSerializer]. Note that only constructor defined properties will
 * be serialised!
 */
interface SerializeableWithKryo

class ImmutableClassSerializer<T : SerializeableWithKryo>(val klass: KClass<T>) : Serializer<T>() {
    val props = klass.memberProperties.sortedBy { it.name }
    val propsByName = props.toMapBy { it.name }
    val constructor = klass.primaryConstructor!!

    init {
        // Verify that this class is immutable (all properties are final)
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

val THREAD_LOCAL_KRYO = ThreadLocal.withInitial { createKryo() }

inline fun <reified T : SerializeableWithKryo> Kryo.registerImmutableClass() = register(T::class.java, ImmutableClassSerializer(T::class))
inline fun <reified T : SerializeableWithKryo> ByteArray.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): T = kryo.readObject(Input(this), T::class.java)
inline fun <reified T : SerializeableWithKryo> OpaqueBytes.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): T = kryo.readObject(Input(this.bits), T::class.java)

fun SerializeableWithKryo.serialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): ByteArray {
    val stream = ByteArrayOutputStream()
    Output(stream).use {
        kryo.writeObject(it, this)
    }
    return stream.toByteArray()
}

private val UNUSED_EC_KEYPAIR = KeyPairGenerator.getInstance("EC").genKeyPair()

fun createKryo(): Kryo {
    return Kryo().apply {
        // Require explicit listing of all types that can be deserialised, to defend against classes that aren't
        // designed for serialisation being unexpectedly instantiated.
        isRegistrationRequired = true

        // Allow various array and list types. Sometimes when the type is private/internal we have to give an example
        // instead and then get the class from that. These have built in Kryo serializers that are safe to use.
        register(ByteArray::class.java)
        register(Collections.EMPTY_LIST.javaClass)
        register(Collections.EMPTY_MAP.javaClass)
        register(Collections.singletonList(null).javaClass)
        register(Collections.singletonMap(1, 2).javaClass)
        register(ArrayList::class.java)
        register(emptyList<Any>().javaClass)
        register(Arrays.asList(1,3).javaClass)

        // These JDK classes use a very minimal custom serialization format and are written to defend against malicious
        // streams, so we can just kick it over to java serialization. We get ECPublicKeyImpl/ECPrivteKeyImpl via an
        // example: it'd be faster to just import the sun.security.ec package directly, but that wouldn't play nice
        // when Java 9 is released, as Project Jigsaw will make internal packages will become unavailable without hacks.
        register(Instant::class.java, JavaSerializer())
        register(Currency::class.java, JavaSerializer())   // Only serialises the currency code as a string.
        register(UNUSED_EC_KEYPAIR.private.javaClass, JavaSerializer())
        register(UNUSED_EC_KEYPAIR.public.javaClass, JavaSerializer())
        register(PublicKey::class.java, JavaSerializer())

        // Now register platform types.
        registerImmutableClass<SecureHash.SHA256>()
        registerImmutableClass<Amount>()
        registerImmutableClass<PartyReference>()
        registerImmutableClass<Party>()
        registerImmutableClass<OpaqueBytes>()
        registerImmutableClass<SignedWireTransaction>()
        registerImmutableClass<ContractStateRef>()
        registerImmutableClass<WireTransaction>()
        registerImmutableClass<WireCommand>()
        registerImmutableClass<TimestampedWireTransaction>()
        registerImmutableClass<StateAndRef<ContractState>>()

        // Can't use data classes for this in Kotlin 1.0 due to lack of support for inheritance: must write a manual
        // serialiser instead :(
        register(DigitalSignature.WithKey::class.java, object : Serializer<DigitalSignature.WithKey>(false, true) {
            override fun write(kryo: Kryo, output: Output, sig: DigitalSignature.WithKey) {
                output.writeVarInt(sig.bits.size, true)
                output.write(sig.bits)
                output.writeInt(sig.covering, true)
                kryo.writeObject(output, sig.by)
            }

            override fun read(kryo: Kryo, input: Input, type: Class<DigitalSignature.WithKey>): DigitalSignature.WithKey {
                val sigLen = input.readVarInt(true)
                val sigBits = input.readBytes(sigLen)
                val covering = input.readInt(true)
                val pubkey = kryo.readObject(input, PublicKey::class.java)
                return DigitalSignature.WithKey(pubkey, sigBits, covering)
            }
        })

        // TODO: This is obviously a short term hack: there needs to be a way to bundle up and register contracts.
        registerImmutableClass<Cash.State>()
        register(Cash.Commands.Move::class.java)
        registerImmutableClass<Cash.Commands.Exit>()
        registerImmutableClass<Cash.Commands.Issue>()
        registerImmutableClass<CommercialPaper.State>()
        register(CommercialPaper.Commands.Move::class.java)
        register(CommercialPaper.Commands.Redeem::class.java)
        register(CommercialPaper.Commands.Issue::class.java)
        registerImmutableClass<CrowdFund.State>()
        registerImmutableClass<CrowdFund.Pledge>()
        registerImmutableClass<CrowdFund.Campaign>()
        register(CrowdFund.Commands.Register::class.java)
        register(CrowdFund.Commands.Pledge::class.java)
        register(CrowdFund.Commands.Close::class.java)

        // And for unit testing ...
        registerImmutableClass<DummyPublicKey>()
    }
}