package net.corda.core.serialization.amqp

import org.junit.Test
import kotlin.test.assertEquals
import org.apache.qpid.proton.codec.Data

/**
 * Prior to certain fixes being made within the [PropertySerializaer] classes these simple
 * deserialization operations would've blown up with type mismatch errors where the deserlized
 * char property of the class would've been treated as an Integer and given to the constructor
 * as such
 */
class DeserializeSimpleTypesTests {
    class TestSerializationOutput(
            private val verbose: Boolean,
            serializerFactory: SerializerFactory = SerializerFactory()) : SerializationOutput(serializerFactory) {

        override fun writeSchema(schema: Schema, data: Data) {
            if(verbose) println(schema)
            super.writeSchema(schema, data)
        }
    }

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    val sf = SerializerFactory()

    @Test
    fun testChar() {
        data class C(val c: Char)
        val c = C('c')
        val serialisedC = SerializationOutput().serialize(c)
        val deserializedC = DeserializationInput().deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }

    @Test
    fun testCharacter() {
        data class C(val c: Character)
        val c = C(Character ('c'))
        val serialisedC = SerializationOutput().serialize(c)
        val deserializedC = DeserializationInput().deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }

    @Test
    fun testArrayOfInt() {
        class IA(val ia: Array<Int>)

        val ia = IA(arrayOf(1, 2, 3))

        assertEquals("class [Ljava.lang.Integer;", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf).serialize(ia)
        val deserializedIA = DeserializationInput(sf).deserialize(serialisedIA)

        assertEquals(ia.ia.size, deserializedIA.ia.size)
        assertEquals(ia.ia[0], deserializedIA.ia[0])
        assertEquals(ia.ia[1], deserializedIA.ia[1])
        assertEquals(ia.ia[2], deserializedIA.ia[2])
    }

    @Test
    fun testArrayOfInteger() {
        class IA (val ia: Array<Integer>)
        val ia = IA(arrayOf(Integer(1), Integer(2), Integer(3)))

        assertEquals("class [Ljava.lang.Integer;", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf).serialize(ia)
        val deserializedIA = DeserializationInput(sf).deserialize(serialisedIA)

        assertEquals(ia.ia.size, deserializedIA.ia.size)
        assertEquals(ia.ia[0], deserializedIA.ia[0])
        assertEquals(ia.ia[1], deserializedIA.ia[1])
        assertEquals(ia.ia[2], deserializedIA.ia[2])
    }

    /**
     * Test unboxed primitives
     */
    @Test
    fun testIntArray() {
        class IA (val ia: IntArray)
        val v = IntArray(3)
        v[0] = 1; v[1] = 2; v[2] = 3
        val ia = IA(v)

        assertEquals("class [I", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[p]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf).serialize(ia)
        val deserializedIA = DeserializationInput(sf).deserialize(serialisedIA)

        assertEquals(ia.ia.size, deserializedIA.ia.size)
        assertEquals(ia.ia[0], deserializedIA.ia[0])
        assertEquals(ia.ia[1], deserializedIA.ia[1])
        assertEquals(ia.ia[2], deserializedIA.ia[2])
    }

    @Test
    fun testArrayOfChars() {
        class C (val c: Array<Char>)
        val c = C(arrayOf ('a', 'b', 'c'))

        assertEquals("class [Ljava.lang.Character;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "char[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedC = DeserializationInput(sf).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testCharArray() {
        class C(val c: CharArray)
        val v = CharArray(3)
        v[0] = 'a'; v[1] = 'b'; v[2] = 'c'
        val c = C(v)

        assertEquals("class [C", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "char[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedC = DeserializationInput(sf).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfBoolean() {
        class C (val c: Array<Boolean>)
        val c = C(arrayOf (true, false, false, true))

        assertEquals("class [Ljava.lang.Boolean;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "boolean[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedC = DeserializationInput(sf).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
        assertEquals(c.c[3], deserializedC.c[3])
    }

    @Test
    fun testBooleanArray() {
        class C(val c: BooleanArray)
        val c = C(BooleanArray(4))
        c.c[0] = true; c.c[1] = false; c.c[2] = false; c.c[3] = true

        assertEquals("class [Z", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "boolean[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedC = DeserializationInput(sf).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
        assertEquals(c.c[3], deserializedC.c[3])
    }

    @Test
    fun testArrayOfByte() {
        class C(val c: Array<Byte>)
    }

    @Test
    fun testByteArray() {
        class C(val c: ByteArray)
    }

    @Test
    fun testArrayOfShort() {
        class C(val c: Array<Short>)
    }

    @Test
    fun testShortArray() {
        class C(val c: ShortArray)
    }

    @Test
    fun testArrayOfLong() {
        class C(val c: Array<Long>)
    }

    @Test
    fun testLongArray() {
        class C(val c: LongArray)
    }

    @Test
    fun testArrayOfFloat() {
        class C(val c: Array<Float>)
    }

    @Test
    fun testFloatArray() {
        class C(val c: FloatArray)
    }

    @Test
    fun testArrayOfDouble() {
        class C(val c: Array<Double>)
    }

    @Test
    fun testDoubleArray() {
        class C(val c: DoubleArray)
    }
}

