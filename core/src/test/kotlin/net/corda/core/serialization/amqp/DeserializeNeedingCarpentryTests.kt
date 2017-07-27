package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.*
import net.corda.core.serialization.carpenter.*
import net.corda.core.serialization.amqp.test.TestSerializationOutput

interface I {
    fun getName() : String
}

interface II {
    fun returnName() : String
}

interface III {
    fun returnAge() : Int
    fun returnThingWithName(): II
}

interface B {
    fun getName() : String
}

interface BB {
    fun getAge() : Int
    fun getThingWithName(): II
}

/**
 * These tests work by having the class carpenter build the classes we serialise and then deserialise. Because
 * those classes don't exist within the system's Class Loader the deserialiser will be forced to carpent
 * versions of them up using its own internal class carpenter (each carpenter houses it's own loader). This
 * replicates the situation where a receiver doesn't have some or all elements of a schema present on it's classpath
 */
class DeserializeNeedingCarpentryTests {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    val sf = SerializerFactory()

    @Test
    fun verySimpleType() {
        val testVal = 10
        val clazz = ClassCarpenter().build(ClassSchema("oneType", mapOf("a" to NonNullableField(Int::class.java))))
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(classInstance)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

        assertEquals (testVal, deserializedObj::class.java.getMethod("getA").invoke(deserializedObj))
    }

    @Test
    fun simpleTypeKnownInterface() {
        val clazz = ClassCarpenter().build (ClassSchema(
                "oneType", mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf (I::class.java)))
        val testVal = "Some Person"
        val classInstance = clazz.constructors[0].newInstance(testVal)

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(classInstance)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

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
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(classInstance)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

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

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(classInstance)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

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

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(toSerialise)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)
        var sentinel = 1
        deserializedObj.l.forEach {
            assertEquals(sentinel++, it::class.java.getMethod("getV1").invoke(it))
            assertEquals(sentinel++, it::class.java.getMethod("getV2").invoke(it))
        }
    }

    // technically this test doesn't test anything (relevent to carpanter / serialiser interaction) since
    // all the classes are knwon, what does do is replicate the test below to demonstrate it should
    // all work
    @Test
    fun mapOfKnown() {
        class lII (val name: String) : II {
            override fun returnName() = name
        }

        class lIII (val age: Int, val thingWithName: II): III {
            override fun returnAge(): Int = age
            override fun returnThingWithName() = thingWithName
        }

        data class Wrapper(val IIIs: MutableMap<String, III>)
        val wrapper = Wrapper (mutableMapOf())
        val testData = arrayOf(Pair ("Fred", 12), Pair ("Bob", 50), Pair ("Thirsty", 101))

        testData.forEach {
            wrapper.IIIs[it.first] = lIII(it.second, lII(it.first))
        }

        // Now do the actual test by serialising and deserialising [wrapper]
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(wrapper)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)
    }

    // TODO This class shows that the problem isn't with the carpented class but a general
    // TODO Bug / feature of the code...
    /*
    @Test
    fun linkedHashMapTest() {
        data class C(val c : LinkedHashMap<String, Int>)
        val c = C (LinkedHashMap (mapOf("A" to 1, "B" to 2)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

    }
    */

    // TODO the problem here is that the wrapper class as created by the serialiser
    // TODO contains a [LinkedHashMap] and not a [Map] and we thus can't serialise
    // TODO it - Talk to Rick about weather we should be able to or not
    /*
    @Test
    fun mapOfInterfaces() {
        val cc = ClassCarpenter()

        val implementsI = cc.build(ClassSchema(
                "implementsI", mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf (B::class.java)))

        val implementsII = cc.build(ClassSchema("ImplementsII", mapOf (
                "age" to NonNullableField(Int::class.java),
                "thingWithName" to NullableField(B::class.java)),
                interfaces = listOf (BB::class.java)))

                //        inline fun getval(reified T : Any) : return T::class.java

        val wrapper = cc.build(ClassSchema("wrapper", mapOf (
                "BBs" to NonNullableField(mutableMapOf<String, BB>()::class.java
        ))))

        val tmp : MutableMap<String, BB> = mutableMapOf()
        val toSerialise = wrapper.constructors.first().newInstance(tmp)
        val testData = arrayOf(Pair ("Fred", 12), Pair ("Bob", 50), Pair ("Thirsty", 101))

        testData.forEach {
            (wrapper.getMethod("getBBs").invoke(toSerialise) as MutableMap<String, BB>)[it.first] =
                    implementsII.constructors.first().newInstance(it.second,
                            implementsI.constructors.first().newInstance(it.first) as B) as BB
        }

        // Now do the actual test by serialising and deserialising [wrapper]
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(toSerialise)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)
    }
    */

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

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(
                concreteClass.constructors.first().newInstance(12, "timmy"))
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)

        assertTrue(deserializedObj is I)
        assertEquals("timmy", (deserializedObj as I).getName())
        assertEquals("timmy", deserializedObj::class.java.getMethod("getName").invoke(deserializedObj))
        assertEquals(12, deserializedObj::class.java.getMethod("getAge").invoke(deserializedObj))
    }
}
