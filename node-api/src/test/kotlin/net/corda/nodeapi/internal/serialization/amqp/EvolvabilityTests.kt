package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.toHexString
import org.junit.Test
import java.io.File


class EvolvabilityTests {

    @Test
    fun test1() {
        val sf = SerializerFactory()

        // Basis for the serialised version
        // data class C (val a: Int)
        // var sc = SerializationOutput(sf).serialize(C(1))

        data class C (val a: Int, val b: Int)

        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.test1")
        println ("PATH = $path")
        val f = File(path.toURI())

        println (sf)
//        var sc = SerializationOutput(sf).serialize(C(1))
//        f.writeBytes(sc.bytes)
        val sc2 = f.readBytes()

        var deserializedC = DeserializationInput().deserialize(SerializedBytes<C>(sc2))

        println (deserializedC.a)



    }
}