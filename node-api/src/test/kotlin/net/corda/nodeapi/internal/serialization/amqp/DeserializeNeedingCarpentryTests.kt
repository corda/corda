package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.*
import net.corda.core.serialization.carpenter.ClassCarpenter
import net.corda.core.serialization.carpenter.ClassSchema
import net.corda.core.serialization.carpenter.NonNullableField

interface I {
    fun getName() : String
}

class DeserializeNeedingCarpentryTests {
    @Test
    fun verySimpleType() {
        val testVal = 10
        val cc = ClassCarpenter()
        val schema = ClassSchema("oneType", mapOf("a" to NonNullableField(Int::class.java)))
        val clazz = cc.build (schema)
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertEquals (testVal, deserializedObj::class.java.getMethod("getA").invoke(deserializedObj))
    }

    @Test
    fun simpleTypeKnownInterface() {
        val cc = ClassCarpenter()
        val schema = ClassSchema("oneType", mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java))
        val clazz = cc.build (schema)
        val testVal = "Andrew Person"
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = SerializationOutput().serialize(classInstance)
        val deserializedObj = DeserializationInput().deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals(testVal, (deserializedObj as I).getName())
    }

    @Test
    fun nestedTypes() {
        val cc = ClassCarpenter()
        val nestedClass = cc.build (
                ClassSchema("nestedType",
                        mapOf("name" to NonNullableField(String::class.java))))

        val outerClass = cc.build (
                ClassSchema("outerType",
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
        val nestedClass = cc.build (
                ClassSchema("nestedType",
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
        val cc = ClassCarpenter()
        val unknownClass = cc.build (ClassSchema("unknownClass", mapOf(
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
}
