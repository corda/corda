package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import org.junit.Test
import kotlin.test.assertEquals

class CalculatedValuesToClassCarpenterTests : AmqpCarpenterBase(AllWhitelist) {

    interface Parent {
        @SerializableCalculatedProperty
        fun getDoubled(): Int
    }

    @Test
    fun calculatedValues() {
        data class C(val i: Int): Parent {
            @get:SerializableCalculatedProperty
            val squared = (i * i).toString()

            override fun getDoubled(): Int = i * 2
        }

        val factory = testDefaultFactoryNoEvolution()
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(C(2)))

        val amqpObj = obj.obj as C
        val serSchema = obj.envelope.schema

        assertEquals(2, amqpObj.i)
        assertEquals("4", amqpObj.squared)
        assertEquals(2, serSchema.types.size)
        require(serSchema.types[0] is CompositeType)

        val concrete = serSchema.types[0] as CompositeType
        assertEquals(3, concrete.fields.size)
        assertEquals("doubled", concrete.fields[0].name)
        assertEquals("int", concrete.fields[0].type)
        assertEquals("i", concrete.fields[1].name)
        assertEquals("int", concrete.fields[1].type)
        assertEquals("squared", concrete.fields[2].name)
        assertEquals("string", concrete.fields[2].type)


        val l1 = serSchema.carpenterSchema(ClassLoader.getSystemClassLoader())
        assertEquals(0, l1.size)
        val mangleSchema = serSchema.mangleNames(listOf(classTestName("C")))
        val l2 = mangleSchema.carpenterSchema(ClassLoader.getSystemClassLoader())
        val aName = mangleName(classTestName("C"))

        assertEquals(1, l2.size)
        val aSchema = l2.carpenterSchemas.find { it.name == aName }!!

        val pinochio = ClassCarpenterImpl(whitelist = AllWhitelist).build(aSchema)
        val p = pinochio.constructors[0].newInstance(4, 2, "4")

        assertEquals(pinochio.getMethod("getI").invoke(p), amqpObj.i)
        assertEquals(pinochio.getMethod("getSquared").invoke(p), amqpObj.squared)
        assertEquals(pinochio.getMethod("getDoubled").invoke(p), amqpObj.getDoubled())

        val upcast = p as Parent
        assertEquals(upcast.getDoubled(), amqpObj.getDoubled())
    }
}