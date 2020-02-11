package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.testSerializationContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MultiMemberCompositeSchemaToClassCarpenterTests : AmqpCarpenterBase(AllWhitelist) {

    @Test(timeout=300_000)
	fun anIntAndALong() {
        @CordaSerializable
        data class A(val a: Int, val b: Long)

        val (_, env) = A(23, 42).roundTrip()
        val carpentedInstance = env.getMangled<A>().load(testSerializationContext).new(23, 42)

        assertEquals(23, carpentedInstance.get("a"))
        assertEquals(42L, carpentedInstance.get("b"))
    }

    @Test(timeout=300_000)
	fun intAndStr() {
        @CordaSerializable
        data class A(val a: Int, val b: String)

        val (_, env) = A(23, "skidoo").roundTrip()
        val carpentedInstance = env.getMangled<A>().load(testSerializationContext).new(23, "skidoo")

        assertEquals(23, carpentedInstance.get("a"))
        assertEquals("skidoo", carpentedInstance.get("b"))
    }

    interface Parent {
        @get:SerializableCalculatedProperty
        val doubled: Int
    }

    @Test(timeout=300_000)
	fun calculatedValues() {
        data class C(val i: Int): Parent {
            @get:SerializableCalculatedProperty
            val squared = (i * i).toString()

            override val doubled get() = i * 2
        }

        val (amqpObj, envelope) = C(2).roundTrip()
        val remoteTypeInformation = envelope.typeInformationFor<C>()

        assertEquals("""
            C: Parent
              doubled: int
              i: int
              squared: String
              """.trimIndent(), remoteTypeInformation.prettyPrint())

        val pinochio = remoteTypeInformation.mangle<C>().load(testSerializationContext)
        assertNotEquals(pinochio.name, C::class.java.name)
        assertNotEquals(pinochio, C::class.java)

        // Note that params are given in alphabetical order: doubled, i, squared
        val p = pinochio.new(4, 2, "4")

        assertEquals(2, p.get("i"))
        assertEquals("4", p.get("squared"))
        assertEquals(4, p.get("doubled"))

        val upcast = p as Parent
        assertEquals(upcast.doubled, amqpObj.doubled)
    }

    @Test(timeout=300_000)
	fun implementingClassDoesNotCalculateValue() {
        class C(override val doubled: Int): Parent

        val (_, env) = C(5).roundTrip()

        val pinochio = env.getMangled<C>().load(testSerializationContext)
        val p = pinochio.new(5)

        assertEquals(5, p.get("doubled"))

        val upcast = p as Parent
        assertEquals(5, upcast.doubled)
    }
}

