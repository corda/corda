package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.*
import net.corda.core.serialization.carpenter.ClassCarpenter
import net.corda.core.serialization.carpenter.ClassSchema
import net.corda.core.serialization.carpenter.InterfaceSchema
import net.corda.core.serialization.carpenter.NonNullableField

interface I {
    fun getName() : String
}

/**
 * These tests work by having the class carpenter build the classes we serialise and then deserialise. Because
 * those classes don't exist within the system's Class Loader the deserialiser will be forced to carpent
 * versions of them up using its own internal class carpenter (each carpenter houses it's own loader). This
 * replicates the situation where a reciever doesn't have some or all elements of a schema present on it's classpath
 */
class DeserializeNeedingCarpentryTests {
    @Test
    fun verySimpleType() {
        val testVal = 10
        val clazz = ClassCarpenter().build(ClassSchema("oneType", mapOf("a" to NonNullableField(Int::class.java))))
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertEquals (testVal, deserializedObj::class.java.getMethod("getA").invoke(deserializedObj))
    }

    @Test
    fun simpleTypeKnownInterface() {
        val clazz = ClassCarpenter().build (ClassSchema(
                "oneType", mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java)))
        val testVal = "Some Person"
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals(testVal, (deserializedObj as I).getName())
    }

    @Test
    fun nestedTypes() {
        val cc = ClassCarpenter()
        val nestedClass = cc.build (ClassSchema("nestedType",
                mapOf("name" to NonNullableField(String::class.java))))

        val outerClass = cc.build (ClassSchema("outerType",
                mapOf("inner" to NonNullableField(nestedClass))))

        val classInstance = outerClass.constructors.first().newInstance(nestedClass.constructors.first().newInstance("name"))
        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        val inner = deserializedObj::class.java.getMethod("getInner").invoke(deserializedObj)
        assertEquals("name", inner::class.java.getMethod("getName").invoke(inner))
    }

    @Test
    fun repeatedNestedTypes() {
        val cc = ClassCarpenter()
        val nestedClass = cc.build (ClassSchema("nestedType",
                mapOf("name" to NonNullableField(String::class.java))))

        data class outer(val a: Any, val b: Any)

        val classInstance = outer (
                nestedClass.constructors.first().newInstance("foo"),
                nestedClass.constructors.first().newInstance("bar"))

        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertEquals ("foo", deserializedObj.a::class.java.getMethod("getName").invoke(deserializedObj.a))
        assertEquals ("bar", deserializedObj.b::class.java.getMethod("getName").invoke(deserializedObj.b))
    }

    @Test
    fun listOfType() {
        val unknownClass = ClassCarpenter().build (ClassSchema("unknownClass", mapOf(
                "v1" to NonNullableField(Int::class.java),
                "v2" to NonNullableField(Int::class.java))))

        data class outer (val l : List<Any>)
        val toSerialise = outer (listOf (
                unknownClass.constructors.first().newInstance(1, 2),
                unknownClass.constructors.first().newInstance(3, 4),
                unknownClass.constructors.first().newInstance(5, 6),
                unknownClass.constructors.first().newInstance(7, 8)))

        val serialisedBytes = SerializationOutput().serialize(toSerialise)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)
        var sentinel = 1
        deserializedObj.l.forEach {
            assertEquals(sentinel++, it::class.java.getMethod("getV1").invoke(it))
            assertEquals(sentinel++, it::class.java.getMethod("getV2").invoke(it))
        }
    }

    @Test
    fun unknownInterface() {
        val cc = ClassCarpenter()

        val interfaceClass = cc.build (InterfaceSchema(
                "gen.Interface",
                mapOf("age" to NonNullableField (Int::class.java))))

        val concreteClass = cc.build (ClassSchema ("gen.Class", mapOf(
                "age" to NonNullableField (Int::class.java),
                "name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java, interfaceClass)))

        val serialisedBytes = SerializationOutput().serialize(
                concreteClass.constructors.first().newInstance(12, "timmy"))
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals("timmy", (deserializedObj as I).getName())
        assertEquals("timmy", deserializedObj::class.java.getMethod("getName").invoke(deserializedObj))
        assertEquals(12, deserializedObj::class.java.getMethod("getAge").invoke(deserializedObj))
    }
}
