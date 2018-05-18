package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import org.junit.Test
import kotlin.test.assertEquals

// Prior to certain fixes being made within the [PropertySerializaer] classes these simple
// deserialization operations would've blown up with type mismatch errors where the deserlized
// char property of the class would've been treated as an Integer and given to the constructor
// as such
class DeserializeSimpleTypesTests {

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    val sf1 = testDefaultFactoryNoEvolution()
    val sf2 = testDefaultFactoryNoEvolution()

    @Test
    fun testChar() {
        data class C(val c: Char)

        var deserializedC = DeserializationInput(sf1).deserialize(SerializationOutput(sf1).serialize(C('c')))
        assertEquals('c', deserializedC.c)

        // CYRILLIC CAPITAL LETTER YU (U+042E)
        deserializedC = DeserializationInput(sf1).deserialize(SerializationOutput(sf1).serialize(C('Ю')))
        assertEquals('Ю', deserializedC.c)

        // 	ARABIC LETTER FEH WITH DOT BELOW (U+06A3)
        deserializedC = DeserializationInput(sf1).deserialize(SerializationOutput(sf1).serialize(C('ڣ')))
        assertEquals('ڣ', deserializedC.c)

        // 	ARABIC LETTER DAD WITH DOT BELOW (U+06FB)
        deserializedC = DeserializationInput(sf1).deserialize(SerializationOutput(sf1).serialize(C('ۻ')))
        assertEquals('ۻ', deserializedC.c)

        // BENGALI LETTER AA (U+0986)
        deserializedC = DeserializationInput(sf1).deserialize(SerializationOutput(sf1).serialize(C('আ')))
        assertEquals('আ', deserializedC.c)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun testCharacter() {
        data class C(val c: Character)

        val c = C(Character('c'))
        val serialisedC = SerializationOutput(sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }

    @Test
    fun testNullCharacter() {
        data class C(val c: Char?)

        val c = C(null)
        val serialisedC = SerializationOutput(sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }

    @Test
    fun testArrayOfInt() {
        class IA(val ia: Array<Int>)

        val ia = IA(arrayOf(1, 2, 3))

        assertEquals("class [Ljava.lang.Integer;", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf1).serialize(ia)
        val deserializedIA = DeserializationInput(sf1).deserialize(serialisedIA)

        assertEquals(ia.ia.size, deserializedIA.ia.size)
        assertEquals(ia.ia[0], deserializedIA.ia[0])
        assertEquals(ia.ia[1], deserializedIA.ia[1])
        assertEquals(ia.ia[2], deserializedIA.ia[2])
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun testArrayOfInteger() {
        class IA(val ia: Array<Integer>)

        val ia = IA(arrayOf(Integer(1), Integer(2), Integer(3)))

        assertEquals("class [Ljava.lang.Integer;", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf1).serialize(ia)
        val deserializedIA = DeserializationInput(sf1).deserialize(serialisedIA)

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
        class IA(val ia: IntArray)

        val v = IntArray(3)
        v[0] = 1; v[1] = 2; v[2] = 3
        val ia = IA(v)

        assertEquals("class [I", ia.ia::class.java.toString())
        assertEquals(SerializerFactory.nameForType(ia.ia::class.java), "int[p]")

        val serialisedIA = TestSerializationOutput(VERBOSE, sf1).serialize(ia)
        val deserializedIA = DeserializationInput(sf1).deserialize(serialisedIA)

        assertEquals(ia.ia.size, deserializedIA.ia.size)
        assertEquals(ia.ia[0], deserializedIA.ia[0])
        assertEquals(ia.ia[1], deserializedIA.ia[1])
        assertEquals(ia.ia[2], deserializedIA.ia[2])
    }

    @Test
    fun testArrayOfChars() {
        class C(val c: Array<Char>)

        val c = C(arrayOf('a', 'b', 'c'))

        assertEquals("class [Ljava.lang.Character;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "char[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

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

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        var deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])

        // second test with more interesting characters
        v[0] = 'ই'; v[1] = ' '; v[2] = 'ਔ'
        val c2 = C(v)

        deserializedC = DeserializationInput(sf1).deserialize(TestSerializationOutput(VERBOSE, sf1).serialize(c2))

        assertEquals(c2.c.size, deserializedC.c.size)
        assertEquals(c2.c[0], deserializedC.c[0])
        assertEquals(c2.c[1], deserializedC.c[1])
        assertEquals(c2.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfBoolean() {
        class C(val c: Array<Boolean>)

        val c = C(arrayOf(true, false, false, true))

        assertEquals("class [Ljava.lang.Boolean;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "boolean[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

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

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
        assertEquals(c.c[3], deserializedC.c[3])
    }

    @Test
    fun testArrayOfByte() {
        class C(val c: Array<Byte>)

        val c = C(arrayOf(0b0001, 0b0101, 0b1111))

        assertEquals("class [Ljava.lang.Byte;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "byte[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testByteArray() {
        class C(val c: ByteArray)

        val c = C(ByteArray(3))
        c.c[0] = 0b0001; c.c[1] = 0b0101; c.c[2] = 0b1111

        assertEquals("class [B", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "binary")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfShort() {
        class C(val c: Array<Short>)

        val c = C(arrayOf(1, 2, 3))

        assertEquals("class [Ljava.lang.Short;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "short[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testShortArray() {
        class C(val c: ShortArray)

        val c = C(ShortArray(3))
        c.c[0] = 1; c.c[1] = 2; c.c[2] = 5

        assertEquals("class [S", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "short[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfLong() {
        class C(val c: Array<Long>)

        val c = C(arrayOf(2147483650, -2147483800, 10))

        assertEquals("class [Ljava.lang.Long;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "long[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testLongArray() {
        class C(val c: LongArray)

        val c = C(LongArray(3))
        c.c[0] = 2147483650; c.c[1] = -2147483800; c.c[2] = 10

        assertEquals("class [J", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "long[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfFloat() {
        class C(val c: Array<Float>)

        val c = C(arrayOf(10F, 100.023232F, -1455.433400F))

        assertEquals("class [Ljava.lang.Float;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "float[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testFloatArray() {
        class C(val c: FloatArray)

        val c = C(FloatArray(3))
        c.c[0] = 10F; c.c[1] = 100.023232F; c.c[2] = -1455.433400F

        assertEquals("class [F", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "float[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testArrayOfDouble() {
        class C(val c: Array<Double>)

        val c = C(arrayOf(10.0, 100.2, -1455.2))

        assertEquals("class [Ljava.lang.Double;", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "double[]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun testDoubleArray() {
        class C(val c: DoubleArray)

        val c = C(DoubleArray(3))
        c.c[0] = 10.0; c.c[1] = 100.2; c.c[2] = -1455.2

        assertEquals("class [D", c.c::class.java.toString())
        assertEquals(SerializerFactory.nameForType(c.c::class.java), "double[p]")

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0], deserializedC.c[0])
        assertEquals(c.c[1], deserializedC.c[1])
        assertEquals(c.c[2], deserializedC.c[2])
    }

    @Test
    fun arrayOfArrayOfInt() {
        class C(val c: Array<Array<Int>>)

        val c = C(arrayOf(arrayOf(1, 2, 3), arrayOf(4, 5, 6)))

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0].size, deserializedC.c[0].size)
        assertEquals(c.c[0][0], deserializedC.c[0][0])
        assertEquals(c.c[0][1], deserializedC.c[0][1])
        assertEquals(c.c[0][2], deserializedC.c[0][2])
        assertEquals(c.c[1].size, deserializedC.c[1].size)
        assertEquals(c.c[1][0], deserializedC.c[1][0])
        assertEquals(c.c[1][1], deserializedC.c[1][1])
        assertEquals(c.c[1][2], deserializedC.c[1][2])
    }

    @Test
    fun arrayOfIntArray() {
        class C(val c: Array<IntArray>)

        val c = C(arrayOf(IntArray(3), IntArray(3)))
        c.c[0][0] = 1; c.c[0][1] = 2; c.c[0][2] = 3
        c.c[1][0] = 4; c.c[1][1] = 5; c.c[1][2] = 6

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        assertEquals(c.c.size, deserializedC.c.size)
        assertEquals(c.c[0].size, deserializedC.c[0].size)
        assertEquals(c.c[0][0], deserializedC.c[0][0])
        assertEquals(c.c[0][1], deserializedC.c[0][1])
        assertEquals(c.c[0][2], deserializedC.c[0][2])
        assertEquals(c.c[1].size, deserializedC.c[1].size)
        assertEquals(c.c[1][0], deserializedC.c[1][0])
        assertEquals(c.c[1][1], deserializedC.c[1][1])
        assertEquals(c.c[1][2], deserializedC.c[1][2])
    }

    @Test
    fun arrayOfArrayOfIntArray() {
        class C(val c: Array<Array<IntArray>>)

        val c = C(arrayOf(arrayOf(IntArray(3), IntArray(3), IntArray(3)),
                arrayOf(IntArray(3), IntArray(3), IntArray(3)),
                arrayOf(IntArray(3), IntArray(3), IntArray(3))))

        for (i in 0..2) {
            for (j in 0..2) {
                for (k in 0..2) {
                    c.c[i][j][k] = i + j + k
                }
            }
        }

        val serialisedC = TestSerializationOutput(VERBOSE, sf1).serialize(c)
        val deserializedC = DeserializationInput(sf1).deserialize(serialisedC)

        for (i in 0..2) {
            for (j in 0..2) {
                for (k in 0..2) {
                    assertEquals(c.c[i][j][k], deserializedC.c[i][j][k])
                }
            }
        }
    }

    @Test
    fun nestedRepeatedTypes() {
        class A(val a: A?, val b: Int)

        var a = A(A(A(A(A(null, 1), 2), 3), 4), 5)

        val sa = TestSerializationOutput(VERBOSE, sf1).serialize(a)
        val da1 = DeserializationInput(sf1).deserialize(sa)
        val da2 = DeserializationInput(sf2).deserialize(sa)

        assertEquals(5, da1.b)
        assertEquals(4, da1.a?.b)
        assertEquals(3, da1.a?.a?.b)
        assertEquals(2, da1.a?.a?.a?.b)
        assertEquals(1, da1.a?.a?.a?.a?.b)

        assertEquals(5, da2.b)
        assertEquals(4, da2.a?.b)
        assertEquals(3, da2.a?.a?.b)
        assertEquals(2, da2.a?.a?.a?.b)
        assertEquals(1, da2.a?.a?.a?.a?.b)

    }
}

