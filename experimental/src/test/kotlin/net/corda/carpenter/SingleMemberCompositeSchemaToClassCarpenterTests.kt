package net.corda.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.ClassCarpenterSchema
import net.corda.core.serialization.amqp.*
import org.junit.Test
import kotlin.test.assertEquals


class SingleMemberCompositeSchemaToClassCarpenterTests {
    private var factory = SerializerFactory()

    fun serialise (clazz : Any) = SerializationOutput(factory).serialize(clazz)

    @Test
    fun singleInteger() {
        val test = 10

        @CordaSerializable
        data class A(val a : Int)

        val a   = A (test)
        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        val amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,     amqpSchema.fields.size)
        assertEquals ("a",   amqpSchema.fields[0].name)
        assertEquals ("int", amqpSchema.fields[0].type)

        val pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }

    @Test
    fun singleString() {
        val test = "ten"

        @CordaSerializable
        data class A(val a : String)
        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,        amqpSchema.fields.size)
        assertEquals ("a",      amqpSchema.fields[0].name)
        assertEquals ("string", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }

    /*
    @Test
    fun singleChar () {
        val test = 'c'

        @CordaSerializable
        data class A(val a : Char)
        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,      amqpSchema.fields.size)
        assertEquals ("a",    amqpSchema.fields[0].name)
        assertEquals ("char", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchema.name, amqpSchema.carpenterSchema()))

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }
    */

    @Test
    fun singleLong() {
        val test = 10L

        @CordaSerializable
        data class A(val a : Long)

        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,      amqpSchema.fields.size)
        assertEquals ("a",    amqpSchema.fields[0].name)
        assertEquals ("long", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }

    @Test
    fun singleShort() {
        val test = 10.toShort()

        @CordaSerializable
        data class A(val a : Short)

        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,       amqpSchema.fields.size)
        assertEquals ("a",     amqpSchema.fields[0].name)
        assertEquals ("short", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }

    /*
    @Test
    fun singleBool() {
        val test = true

        @CordaSerializable
        data class A(val a : Boolean)

        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,         amqpSchema.fields.size)
        assertEquals ("a",       amqpSchema.fields[0].name)
        assertEquals ("boolean", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(ClassCarpenter.Schema(amqpSchema.name, amqpSchema.carpenterSchema()))

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }
    */

    @Test
    fun singleDouble() {
        val test = 10.0

        @CordaSerializable
        data class A(val a : Double)

        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,        amqpSchema.fields.size)
        assertEquals ("a",      amqpSchema.fields[0].name)
        assertEquals ("double", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }

    @Test
    fun singleFloat() {
        val test = 10.0F

        @CordaSerializable
        data class A(val a : Float)

        var a = A (test)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise (a))

        assert (obj.first is A)
        val amqpObj  = obj.first as A

        assertEquals (test, amqpObj.a)
        assertEquals (1, obj.second.schema.types.size)
        assert (obj.second.schema.types[0] is CompositeType)

        var amqpSchema = obj.second.schema.types[0] as CompositeType

        assertEquals (1,       amqpSchema.fields.size)
        assertEquals ("a",     amqpSchema.fields[0].name)
        assertEquals ("float", amqpSchema.fields[0].type)

        var pinochio   = ClassCarpenter().build(amqpSchema.carpenterSchema())

        val p = pinochio.constructors[0].newInstance (test)

//        assertEquals (pinochio.getMethod("getA").invoke (p), amqpObj.a)
    }
}