package net.corda.serialization.internal.amqp

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Ignore
import org.junit.Test
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

class InStatic : Exception("Help!, help!, I'm being repressed")

class C {
    companion object {
        init {
            throw InStatic()
        }
    }
}

// To re-setup the resource file for the tests
//   * deserializeTest
//   * deserializeTest2
// comment out the companion object from here,  comment out the test code and uncomment
// the generation code, then re-run the test and copy the file shown in the output print
// to the resource directory
class C2(var b: Int) {
    /*
    companion object {
        init {
            throw InStatic()
        }
    }
    */
}

class StaticInitialisationOfSerializedObjectTest {
    @Test(timeout=300_000)
    fun itBlowsUp() {
        assertThatExceptionOfType(ExceptionInInitializerError::class.java).isThrownBy {
            C()
        }
    }

    @Ignore("Suppressing this, as it depends on obtaining internal access to serialiser cache")
    @Test(timeout=300_000)
	fun kotlinObjectWithCompanionObject() {
        data class D(val c: C)

        val sf = SerializerFactoryBuilder.build(AllWhitelist,
                ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader())
        )

        val typeMap = sf::class.java.getDeclaredField("serializersByType")
        typeMap.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val serialisersByType = typeMap.get(sf) as ConcurrentHashMap<Type, AMQPSerializer<Any>>

        // pre building a serializer, we shouldn't have anything registered
        assertEquals(0, serialisersByType.size)

        // build a serializer for type D without an instance of it to serialise, since
        // we can't actually construct one
        sf.get(D::class.java)

        // post creation of the serializer we should have two elements in the map, this
        // proves we didn't statically construct an instance of C when building the serializer
        assertEquals(2, serialisersByType.size)
    }

    @Test(timeout=300_000)
	fun deserializeTest() {
        data class D(val c: C2)

        val url = EvolvabilityTests::class.java.getResource("StaticInitialisationOfSerializedObjectTest.deserializeTest")

        // Original version of the class for the serialised version of this class
        //
        //val sf1 = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        //val sc = SerializationOutput(sf1).serialize(D(C2(20)))
        //f.writeBytes(sc.bytes)
        //println (path)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) =
                    type.name == "net.corda.serialization.internal.amqp" +
                            ".StaticInitialisationOfSerializedObjectTest\$deserializeTest\$D"
        }

        val whitelist = WL()
        val sf2 = SerializerFactoryBuilder.build(whitelist,
                ClassCarpenterImpl(whitelist, ClassLoader.getSystemClassLoader())
        )
        val bytes = url.readBytes()

        assertThatThrownBy {
            DeserializationInput(sf2).deserialize(SerializedBytes<D>(bytes))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    // Version of a serializer factory that will allow the class carpenter living on the
    // factory to have a different whitelist applied to it than the factory
    private fun testSerializerFactory(wl1: ClassWhitelist, wl2: ClassWhitelist) =
            SerializerFactoryBuilder.build(wl1, ClassCarpenterImpl(wl2, ClassLoader.getSystemClassLoader()))

    // This time have the serialization factory and the carpenter use different whitelists
    @Test(timeout=300_000)
	fun deserializeTest2() {
        data class D(val c: C2)

        val url = EvolvabilityTests::class.java.getResource("StaticInitialisationOfSerializedObjectTest.deserializeTest2")

        // Original version of the class for the serialised version of this class
        //
        //val sf1 = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        //val sc = SerializationOutput(sf1).serialize(D(C2(20)))
        //f.writeBytes(sc.bytes)
        //println (path)

        // whitelist to be used by the serialisation factory
        class WL1 : ClassWhitelist {
            override fun hasListed(type: Class<*>) =
                    type.name == "net.corda.serialization.internal.amqp" +
                            ".StaticInitialisationOfSerializedObjectTest\$deserializeTest\$D"
        }

        // whitelist to be used by the carpenter
        class WL2 : ClassWhitelist {
            override fun hasListed(type: Class<*>) = true
        }

        val sf2 = testSerializerFactory(WL1(), WL2())
        val bytes = url.readBytes()

        // Deserializing should throw because C is not on the whitelist NOT because
        // we ever went anywhere near statically constructing it prior to not actually
        // creating an instance of it
        assertThatThrownBy {
            DeserializationInput(sf2).deserialize(SerializedBytes<D>(bytes))
        }.isInstanceOf(NotSerializableException::class.java)
    }
}