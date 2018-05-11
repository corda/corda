package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.carpenter.*
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import org.junit.Test
import kotlin.test.*

// These tests work by having the class carpenter build the classes we serialise and then deserialise. Because
// those classes don't exist within the system's Class Loader the deserialiser will be forced to carpent
// versions of them up using its own internal class carpenter (each carpenter houses it's own loader). This
// replicates the situation where a receiver doesn't have some or all elements of a schema present on it's classpath
class DeserializeNeedingCarpentrySimpleTypesTest : AmqpCarpenterBase(AllWhitelist) {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    private val sf = testDefaultFactoryNoEvolution()
    private val sf2 = testDefaultFactoryNoEvolution()

    @Test
    fun singleInt() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "int" to NonNullableField(Integer::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(1))
        val db = DeserializationInput(sf).deserialize(sb)
        val db2 = DeserializationInput(sf2).deserialize(sb)

        // despite being carpented, and thus not on the class path, we should've cached clazz
        // inside the serialiser object and thus we should have created the same type
        assertEquals(db::class.java, clazz)
        assertNotEquals(db2::class.java, clazz)
        assertNotEquals(db::class.java, db2::class.java)

        assertEquals(1, db::class.java.getMethod("getInt").invoke(db))
        assertEquals(1, db2::class.java.getMethod("getInt").invoke(db2))
    }

    @Test
    fun singleIntNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "int" to NullableField(Integer::class.java)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(1))
        val db1 = DeserializationInput(sf).deserialize(sb)
        val db2 = DeserializationInput(sf2).deserialize(sb)

        assertEquals(clazz, db1::class.java)
        assertNotEquals(clazz, db2::class.java)
        assertEquals(1, db1::class.java.getMethod("getInt").invoke(db1))
        assertEquals(1, db2::class.java.getMethod("getInt").invoke(db2))
    }

    @Test
    fun singleIntNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "int" to NullableField(Integer::class.java)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db1 = DeserializationInput(sf).deserialize(sb)
        val db2 = DeserializationInput(sf2).deserialize(sb)

        assertEquals(clazz, db1::class.java)
        assertNotEquals(clazz, db2::class.java)
        assertEquals(null, db1::class.java.getMethod("getInt").invoke(db1))
        assertEquals(null, db2::class.java.getMethod("getInt").invoke(db2))
    }

    @Test
    fun singleChar() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "char" to NonNullableField(Character::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance('a'))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals('a', db::class.java.getMethod("getChar").invoke(db))
    }

    @Test
    fun singleCharNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "char" to NullableField(Character::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance('a'))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals('a', db::class.java.getMethod("getChar").invoke(db))
    }

    @Test
    fun singleCharNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "char" to NullableField(java.lang.Character::class.java)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getChar").invoke(db))
    }

    @Test
    fun singleLong() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "long" to NonNullableField(Long::class.javaPrimitiveType!!)
        )))

        val l: Long = 1
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(l))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(l, (db::class.java.getMethod("getLong").invoke(db)))
    }

    @Test
    fun singleLongNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "long" to NullableField(Long::class.javaObjectType)
        )))

        val l: Long = 1
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(l))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(l, (db::class.java.getMethod("getLong").invoke(db)))
    }

    @Test
    fun singleLongNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "long" to NullableField(Long::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, (db::class.java.getMethod("getLong").invoke(db)))
    }

    @Test
    fun singleBoolean() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "boolean" to NonNullableField(Boolean::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(true))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(true, db::class.java.getMethod("getBoolean").invoke(db))
    }

    @Test
    fun singleBooleanNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "boolean" to NullableField(Boolean::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(true))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(true, db::class.java.getMethod("getBoolean").invoke(db))
    }

    @Test
    fun singleBooleanNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "boolean" to NullableField(Boolean::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getBoolean").invoke(db))
    }

    @Test
    fun singleDouble() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "double" to NonNullableField(Double::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(10.0))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(10.0, db::class.java.getMethod("getDouble").invoke(db))
    }

    @Test
    fun singleDoubleNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "double" to NullableField(Double::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(10.0))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(10.0, db::class.java.getMethod("getDouble").invoke(db))
    }

    @Test
    fun singleDoubleNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "double" to NullableField(Double::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getDouble").invoke(db))
    }

    @Test
    fun singleShort() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "short" to NonNullableField(Short::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(3.toShort()))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(3.toShort(), db::class.java.getMethod("getShort").invoke(db))
    }

    @Test
    fun singleShortNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "short" to NullableField(Short::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(3.toShort()))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(3.toShort(), db::class.java.getMethod("getShort").invoke(db))
    }

    @Test
    fun singleShortNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "short" to NullableField(Short::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getShort").invoke(db))
    }

    @Test
    fun singleFloat() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "float" to NonNullableField(Float::class.javaPrimitiveType!!)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(10.0F))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(10.0F, db::class.java.getMethod("getFloat").invoke(db))
    }

    @Test
    fun singleFloatNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "float" to NullableField(Float::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(10.0F))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(10.0F, db::class.java.getMethod("getFloat").invoke(db))
    }

    @Test
    fun singleFloatNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "float" to NullableField(Float::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getFloat").invoke(db))
    }

    @Test
    fun singleByte() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "byte" to NonNullableField(Byte::class.javaPrimitiveType!!)
        )))

        val b: Byte = 0b0101
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(b))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(b, db::class.java.getMethod("getByte").invoke(db))
        assertEquals(0b0101, (db::class.java.getMethod("getByte").invoke(db) as Byte))
    }

    @Test
    fun singleByteNullable() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "byte" to NullableField(Byte::class.javaObjectType)
        )))

        val b: Byte = 0b0101
        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(b))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(b, db::class.java.getMethod("getByte").invoke(db))
        assertEquals(0b0101, (db::class.java.getMethod("getByte").invoke(db) as Byte))
    }

    @Test
    fun singleByteNullableNull() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "byte" to NullableField(Byte::class.javaObjectType)
        )))

        val sb = TestSerializationOutput(VERBOSE, sf).serialize(clazz.constructors.first().newInstance(null))
        val db = DeserializationInput(sf2).deserialize(sb)

        assertNotEquals(clazz, db::class.java)
        assertEquals(null, db::class.java.getMethod("getByte").invoke(db))
    }

    @Test
    fun simpleTypeKnownInterface() {
        val clazz = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(
                testName(), mapOf("name" to NonNullableField(String::class.java)),
                interfaces = listOf(I::class.java)))
        val testVal = "Some Person"
        val classInstance = clazz.constructors[0].newInstance(testVal)
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(classInstance)
        val deserializedObj = DeserializationInput(sf2).deserialize(serialisedBytes)

        assertNotEquals(clazz, deserializedObj::class.java)
        assertTrue(deserializedObj is I)
        assertEquals(testVal, (deserializedObj as I).getName())
    }

    @Test
    fun manyTypes() {
        val manyClass = ClassCarpenterImpl(whitelist = AllWhitelist).build(ClassSchema(testName(), mapOf(
                "intA" to NonNullableField(Int::class.java),
                "intB" to NullableField(Integer::class.java),
                "intC" to NullableField(Integer::class.java),
                "strA" to NonNullableField(String::class.java),
                "strB" to NullableField(String::class.java),
                "strC" to NullableField(String::class.java),
                "charA" to NonNullableField(Char::class.java),
                "charB" to NullableField(Character::class.javaObjectType),
                "charC" to NullableField(Character::class.javaObjectType),
                "shortA" to NonNullableField(Short::class.javaPrimitiveType!!),
                "shortB" to NullableField(Short::class.javaObjectType),
                "shortC" to NullableField(Short::class.javaObjectType),
                "longA" to NonNullableField(Long::class.javaPrimitiveType!!),
                "longB" to NullableField(Long::class.javaObjectType),
                "longC" to NullableField(Long::class.javaObjectType),
                "booleanA" to NonNullableField(Boolean::class.javaPrimitiveType!!),
                "booleanB" to NullableField(Boolean::class.javaObjectType),
                "booleanC" to NullableField(Boolean::class.javaObjectType),
                "doubleA" to NonNullableField(Double::class.javaPrimitiveType!!),
                "doubleB" to NullableField(Double::class.javaObjectType),
                "doubleC" to NullableField(Double::class.javaObjectType),
                "floatA" to NonNullableField(Float::class.javaPrimitiveType!!),
                "floatB" to NullableField(Float::class.javaObjectType),
                "floatC" to NullableField(Float::class.javaObjectType),
                "byteA" to NonNullableField(Byte::class.javaPrimitiveType!!),
                "byteB" to NullableField(Byte::class.javaObjectType),
                "byteC" to NullableField(Byte::class.javaObjectType))))

        val serialisedBytes = TestSerializationOutput(VERBOSE, factory).serialize(
                manyClass.constructors.first().newInstance(
                        1, 2, null,
                        "a", "b", null,
                        'c', 'd', null,
                        3.toShort(), 4.toShort(), null,
                        100.toLong(), 200.toLong(), null,
                        true, false, null,
                        10.0, 20.0, null,
                        10.0F, 20.0F, null,
                        0b0101.toByte(), 0b1010.toByte(), null))

        val deserializedObj = DeserializationInput(sf2).deserialize(serialisedBytes)

        assertNotEquals(manyClass, deserializedObj::class.java)
        assertEquals(1, deserializedObj::class.java.getMethod("getIntA").invoke(deserializedObj))
        assertEquals(2, deserializedObj::class.java.getMethod("getIntB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getIntC").invoke(deserializedObj))
        assertEquals("a", deserializedObj::class.java.getMethod("getStrA").invoke(deserializedObj))
        assertEquals("b", deserializedObj::class.java.getMethod("getStrB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getStrC").invoke(deserializedObj))
        assertEquals('c', deserializedObj::class.java.getMethod("getCharA").invoke(deserializedObj))
        assertEquals('d', deserializedObj::class.java.getMethod("getCharB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getCharC").invoke(deserializedObj))
        assertEquals(3.toShort(), deserializedObj::class.java.getMethod("getShortA").invoke(deserializedObj))
        assertEquals(4.toShort(), deserializedObj::class.java.getMethod("getShortB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getShortC").invoke(deserializedObj))
        assertEquals(100.toLong(), deserializedObj::class.java.getMethod("getLongA").invoke(deserializedObj))
        assertEquals(200.toLong(), deserializedObj::class.java.getMethod("getLongB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getLongC").invoke(deserializedObj))
        assertEquals(true, deserializedObj::class.java.getMethod("getBooleanA").invoke(deserializedObj))
        assertEquals(false, deserializedObj::class.java.getMethod("getBooleanB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getBooleanC").invoke(deserializedObj))
        assertEquals(10.0, deserializedObj::class.java.getMethod("getDoubleA").invoke(deserializedObj))
        assertEquals(20.0, deserializedObj::class.java.getMethod("getDoubleB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getDoubleC").invoke(deserializedObj))
        assertEquals(10.0F, deserializedObj::class.java.getMethod("getFloatA").invoke(deserializedObj))
        assertEquals(20.0F, deserializedObj::class.java.getMethod("getFloatB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getFloatC").invoke(deserializedObj))
        assertEquals(0b0101.toByte(), deserializedObj::class.java.getMethod("getByteA").invoke(deserializedObj))
        assertEquals(0b1010.toByte(), deserializedObj::class.java.getMethod("getByteB").invoke(deserializedObj))
        assertEquals(null, deserializedObj::class.java.getMethod("getByteC").invoke(deserializedObj))
    }
}



