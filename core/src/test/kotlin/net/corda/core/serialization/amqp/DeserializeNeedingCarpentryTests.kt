package net.corda.core.serialization.amqp

import net.corda.core.serialization.AllWhitelist
import org.junit.Test
import kotlin.test.*
import net.corda.core.serialization.carpenter.*
import net.corda.core.serialization.amqp.test.TestSerializationOutput

interface I {
    fun getName() : String
}

/**
 * These tests work by having the class carpenter build the classes we serialise and then deserialise. Because
 * those classes don't exist within the system's Class Loader the deserialiser will be forced to carpent
 * versions of them up using its own internal class carpenter (each carpenter houses it's own loader). This
 * replicates the situation where a receiver doesn't have some or all elements of a schema present on it's classpath
 */
class DeserializeNeedingCarpentryTests : AmqpCarpenterBase() {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    @Test
    fun verySimpleType() {
        val testVal = 10
        val clazz = ClassCarpenter().build(ClassSchema(testName(), mapOf("a" to NonNullableField(Int::class.java))))
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(classInstance)
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)

        assertEquals (testVal, deserializedObj::class.java.getMethod("getA").invoke(deserializedObj))
    }

    @Test
    fun repeatedTypesAreRecognised() {
        val testValA = 10
        val testValB = 20
        val testValC = 20
        val clazz = ClassCarpenter().build(ClassSchema("${testName()}_clazz",
                mapOf("a" to NonNullableField(Int::class.java))))

        val concreteA = clazz.constructors[0].newInstance(testValA)
        val concreteB = clazz.constructors[0].newInstance(testValB)
        val concreteC = clazz.constructors[0].newInstance(testValC)

        val deserialisedA = DeserializationInput(factory).deserialize(
                TestSerializationOutput(VERBOSE, factory).serialize(concreteA))

        assertEquals (testValA, deserialisedA::class.java.getMethod("getA").invoke(deserialisedA))

        val deserialisedB = DeserializationInput(factory).deserialize(
                TestSerializationOutput(VERBOSE, factory).serialize(concreteB))

        assertEquals (testValB, deserialisedA::class.java.getMethod("getA").invoke(deserialisedB))
        assertEquals (deserialisedA::class.java, deserialisedB::class.java)

        // C is deseriliased with a different factory, meaning a different class carpenter, so the type
        // won't already exist and it will be carpented a second time showing that when A and B are the
        // same underlying class that we didn't create a second instance of the class with the
        // second deserialisation
        val lfactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val deserialisedC = DeserializationInput(lfactory).deserialize(
                TestSerializationOutput(VERBOSE, lfactory).serialize(concreteC))

        assertEquals (testValC, deserialisedC::class.java.getMethod("getA").invoke(deserialisedC))
        assertNotEquals (deserialisedA::class.java, deserialisedC::class.java)
        assertNotEquals (deserialisedB::class.java, deserialisedC::class.java)
    }

    @Test
    fun simpleTypeKnownInterface() {
        val clazz = ClassCarpenter().build (ClassSchema(
                testName(), mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java)))
        val testVal = "Some Person"
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(classInstance)
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals(testVal, (deserializedObj as I).getName())
    }

    @Test
    fun arrayOfTypes() {
        val clazz = ClassCarpenter().build(ClassSchema(testName(), mapOf("a" to NonNullableField(Int::class.java))))

        data class Outer (val a : Array<Any>)

        val outer = Outer (arrayOf (
                clazz.constructors[0].newInstance(1),
                clazz.constructors[0].newInstance(2),
                clazz.constructors[0].newInstance(3)))

        val deserializedObj = DeserializationInput(factory).deserialize(
                TestSerializationOutput(VERBOSE, factory).serialize(outer))

        assertNotEquals((deserializedObj.a[0])::class.java, (outer.a[0])::class.java)
        assertNotEquals((deserializedObj.a[1])::class.java, (outer.a[1])::class.java)
        assertNotEquals((deserializedObj.a[2])::class.java, (outer.a[2])::class.java)

        assertEquals((deserializedObj.a[0])::class.java, (deserializedObj.a[1])::class.java)
        assertEquals((deserializedObj.a[0])::class.java, (deserializedObj.a[2])::class.java)
        assertEquals((deserializedObj.a[1])::class.java, (deserializedObj.a[2])::class.java)

        assertEquals(
                outer.a[0]::class.java.getMethod("getA").invoke(outer.a[0]),
                deserializedObj.a[0]::class.java.getMethod("getA").invoke(deserializedObj.a[0]))
        assertEquals(
                outer.a[1]::class.java.getMethod("getA").invoke(outer.a[1]),
                deserializedObj.a[1]::class.java.getMethod("getA").invoke(deserializedObj.a[1]))
        assertEquals(
                outer.a[2]::class.java.getMethod("getA").invoke(outer.a[2]),
                deserializedObj.a[2]::class.java.getMethod("getA").invoke(deserializedObj.a[2]))
    }

    @Test
    fun reusedClasses() {
        val cc = ClassCarpenter()

        val innerType = cc.build(ClassSchema("${testName()}.inner", mapOf("a" to NonNullableField(Int::class.java))))
        val outerType = cc.build(ClassSchema("${testName()}.outer", mapOf("a" to NonNullableField(innerType))))
        val inner = innerType.constructors[0].newInstance(1)
        val outer = outerType.constructors[0].newInstance(innerType.constructors[0].newInstance(2))

        val serializedI = TestSerializationOutput(VERBOSE, factory).serialize(inner)
        val deserialisedI = DeserializationInput(factory).deserialize(serializedI)
        val serialisedO   = TestSerializationOutput(VERBOSE, factory).serialize(outer)
        val deserialisedO = DeserializationInput(factory).deserialize(serialisedO)

        // ensure out carpented version of inner is reused
        assertEquals (deserialisedI::class.java,
                (deserialisedO::class.java.getMethod("getA").invoke(deserialisedO))::class.java)
    }

    @Test
    fun nestedTypes() {
        val cc = ClassCarpenter()
        val nestedClass = cc.build (ClassSchema(testName(),
                mapOf("name" to NonNullableField(String::class.java))))

        val outerClass = cc.build (ClassSchema("outerType",
                mapOf("inner" to NonNullableField(nestedClass))))

        val classInstance = outerClass.constructors.first().newInstance(
                nestedClass.constructors.first().newInstance("name"))
        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(classInstance)
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)

        val inner = deserializedObj::class.java.getMethod("getInner").invoke(deserializedObj)
        assertEquals("name", inner::class.java.getMethod("getName").invoke(inner))
    }

    @Test
    fun repeatedNestedTypes() {
        val cc = ClassCarpenter()
        val nestedClass = cc.build (ClassSchema(testName(),
                mapOf("name" to NonNullableField(String::class.java))))

        data class outer(val a: Any, val b: Any)

        val classInstance = outer (
                nestedClass.constructors.first().newInstance("foo"),
                nestedClass.constructors.first().newInstance("bar"))

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(classInstance)
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)

        assertEquals ("foo", deserializedObj.a::class.java.getMethod("getName").invoke(deserializedObj.a))
        assertEquals ("bar", deserializedObj.b::class.java.getMethod("getName").invoke(deserializedObj.b))
    }

    @Test
    fun listOfType() {
        val unknownClass = ClassCarpenter().build (ClassSchema(testName(), mapOf(
                "v1" to NonNullableField(Int::class.java),
                "v2" to NonNullableField(Int::class.java))))

        data class outer (val l : List<Any>)
        val toSerialise = outer (listOf (
                unknownClass.constructors.first().newInstance(1, 2),
                unknownClass.constructors.first().newInstance(3, 4),
                unknownClass.constructors.first().newInstance(5, 6),
                unknownClass.constructors.first().newInstance(7, 8)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(toSerialise)
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)
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

        val concreteClass = cc.build (ClassSchema (testName(), mapOf(
                "age" to NonNullableField (Int::class.java),
                "name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java, interfaceClass)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(
                concreteClass.constructors.first().newInstance(12, "timmy"))
        val deserializedObj = DeserializationInput(factory).deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals("timmy", (deserializedObj as I).getName())
        assertEquals("timmy", deserializedObj::class.java.getMethod("getName").invoke(deserializedObj))
        assertEquals(12, deserializedObj::class.java.getMethod("getAge").invoke(deserializedObj))
    }
}
