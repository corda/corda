/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import core.OpaqueBytes
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.ByteArrayOutputStream
import java.util.*

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

val THREAD_LOCAL_KRYO = ThreadLocal.withInitial { createKryo() }

inline fun <reified T : Any> ByteArray.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): T = kryo.readObject(Input(this), T::class.java)
inline fun <reified T : Any> OpaqueBytes.deserialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): T = kryo.readObject(Input(this.bits), T::class.java)

fun Any.serialize(kryo: Kryo = THREAD_LOCAL_KRYO.get()): ByteArray {
    val stream = ByteArrayOutputStream()
    Output(stream).use {
        kryo.writeObject(it, this)
    }
    return stream.toByteArray()
}

fun createKryo(): Kryo {
    return Kryo().apply {
        // Allow any class to be deserialized (this is insecure but for prototyping we don't care)
        isRegistrationRequired = false
        // Allow construction of objects using a JVM backdoor that skips invoking the constructors, if there is no
        // no-arg constructor available.
        instantiatorStrategy = Kryo.DefaultInstantiatorStrategy(StdInstantiatorStrategy())

        register(Arrays.asList( "" ).javaClass, ArraysAsListSerializer());
    }
}