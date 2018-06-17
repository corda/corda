package net.corda.serialization.internal.carpenter

import net.corda.core.internal.uncheckedCast
import net.corda.serialization.internal.AllWhitelist
import org.junit.Test
import java.beans.Introspector
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.annotation.Nonnull
import javax.annotation.Nullable
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ClassCarpenterTest {
    interface DummyInterface {
        val a: String
        val b: Int
    }

    private val cc = ClassCarpenterImpl(whitelist = AllWhitelist)

    // We have to ignore synthetic fields even though ClassCarpenter doesn't create any because the JaCoCo
    // coverage framework auto-magically injects one method and one field into every class loaded into the JVM.
    private val Class<*>.nonSyntheticFields: List<Field> get() = declaredFields.filterNot { it.isSynthetic }
    private val Class<*>.nonSyntheticMethods: List<Method> get() = declaredMethods.filterNot { it.isSynthetic }

    @Test
    fun empty() {
        val clazz = cc.build(ClassSchema("gen.EmptyClass", emptyMap(), null))
        assertEquals(0, clazz.nonSyntheticFields.size)
        assertEquals(2, clazz.nonSyntheticMethods.size)   // get, toString
        assertEquals(0, clazz.declaredConstructors[0].parameterCount)
        clazz.newInstance()   // just test there's no exception.
    }

    @Test
    fun prims() {
        val clazz = cc.build(ClassSchema(
                "gen.Prims",
                mapOf(
                        "anIntField" to Int::class.javaPrimitiveType!!,
                        "aLongField" to Long::class.javaPrimitiveType!!,
                        "someCharField" to Char::class.javaPrimitiveType!!,
                        "aShortField" to Short::class.javaPrimitiveType!!,
                        "doubleTrouble" to Double::class.javaPrimitiveType!!,
                        "floatMyBoat" to Float::class.javaPrimitiveType!!,
                        "byteMe" to Byte::class.javaPrimitiveType!!,
                        "booleanField" to Boolean::class.javaPrimitiveType!!).mapValues {
                    NonNullableField(it.value)
                }))
        assertEquals(8, clazz.nonSyntheticFields.size)
        assertEquals(10, clazz.nonSyntheticMethods.size)
        assertEquals(8, clazz.declaredConstructors[0].parameterCount)
        val i = clazz.constructors[0].newInstance(1, 2L, 'c', 4.toShort(), 1.23, 4.56F, 127.toByte(), true)
        assertEquals(1, clazz.getMethod("getAnIntField").invoke(i))
        assertEquals(2L, clazz.getMethod("getALongField").invoke(i))
        assertEquals('c', clazz.getMethod("getSomeCharField").invoke(i))
        assertEquals(4.toShort(), clazz.getMethod("getAShortField").invoke(i))
        assertEquals(1.23, clazz.getMethod("getDoubleTrouble").invoke(i))
        assertEquals(4.56F, clazz.getMethod("getFloatMyBoat").invoke(i))
        assertEquals(127.toByte(), clazz.getMethod("getByteMe").invoke(i))
        assertEquals(true, clazz.getMethod("getBooleanField").invoke(i))

        val sfa = i as SimpleFieldAccess
        assertEquals(1, sfa["anIntField"])
        assertEquals(2L, sfa["aLongField"])
        assertEquals('c', sfa["someCharField"])
        assertEquals(4.toShort(), sfa["aShortField"])
        assertEquals(1.23, sfa["doubleTrouble"])
        assertEquals(4.56F, sfa["floatMyBoat"])
        assertEquals(127.toByte(), sfa["byteMe"])
        assertEquals(true, sfa["booleanField"])
    }

    private fun genPerson(): Pair<Class<*>, Any> {
        val clazz = cc.build(ClassSchema("gen.Person", mapOf(
                "age" to Int::class.javaPrimitiveType!!,
                "name" to String::class.java
        ).mapValues { NonNullableField(it.value) }))
        val i = clazz.constructors[0].newInstance(32, "Mike")
        return Pair(clazz, i)
    }

    @Test
    fun objs() {
        val (clazz, i) = genPerson()
        assertEquals("Mike", clazz.getMethod("getName").invoke(i))
        assertEquals("Mike", (i as SimpleFieldAccess)["name"])
    }

    @Test
    fun `generated toString`() {
        val (_, i) = genPerson()
        assertEquals("Person{age=32, name=Mike}", i.toString())
    }

    @Test(expected = DuplicateNameException::class)
    fun duplicates() {
        cc.build(ClassSchema("gen.EmptyClass", emptyMap(), null))
        cc.build(ClassSchema("gen.EmptyClass", emptyMap(), null))
    }

    @Test
    fun `can refer to each other`() {
        val (clazz1, i) = genPerson()
        val clazz2 = cc.build(ClassSchema("gen.Referee", mapOf(
                "ref" to NonNullableField(clazz1)
        )))
        val i2 = clazz2.constructors[0].newInstance(i)
        assertEquals(i, (i2 as SimpleFieldAccess)["ref"])
    }

    @Test
    fun superclasses() {
        val schema1 = ClassSchema(
                "gen.A",
                mapOf("a" to NonNullableField(String::class.java)))

        val schema2 = ClassSchema(
                "gen.B",
                mapOf("b" to NonNullableField(String::class.java)),
                schema1)

        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", "xb") as SimpleFieldAccess
        assertEquals("xa", i["a"])
        assertEquals("xb", i["b"])
        assertEquals("B{a=xa, b=xb}", i.toString())
    }

    @Test
    fun interfaces() {
        val schema1 = ClassSchema(
                "gen.A",
                mapOf("a" to NonNullableField(String::class.java)))

        val schema2 = ClassSchema("gen.B",
                mapOf("b" to NonNullableField(Int::class.java)),
                schema1,
                interfaces = listOf(DummyInterface::class.java))

        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", 1) as DummyInterface
        assertEquals("xa", i.a)
        assertEquals(1, i.b)
    }

    @Test(expected = InterfaceMismatchException::class)
    fun `mismatched interface`() {
        val schema1 = ClassSchema(
                "gen.A",
                mapOf("a" to NonNullableField(String::class.java)))

        val schema2 = ClassSchema(
                "gen.B",
                mapOf("c" to NonNullableField(Int::class.java)),
                schema1,
                interfaces = listOf(DummyInterface::class.java))

        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", 1) as DummyInterface
        assertEquals(1, i.b)
    }

    @Test
    fun `generate interface`() {
        val schema1 = InterfaceSchema(
                "gen.Interface",
                mapOf("a" to NonNullableField(Int::class.java)))

        val iface = cc.build(schema1)

        require(iface.isInterface)
        require(iface.constructors.isEmpty())
        assertEquals(iface.declaredMethods.size, 1)
        assertEquals(iface.declaredMethods[0].name, "getA")

        val schema2 = ClassSchema(
                "gen.Derived",
                mapOf("a" to NonNullableField(Int::class.java)),
                interfaces = listOf(iface))

        val clazz = cc.build(schema2)
        val testA = 42
        val i = clazz.constructors[0].newInstance(testA) as SimpleFieldAccess

        assertEquals(testA, i["a"])
    }

    @Test
    fun `generate multiple interfaces`() {
        val iFace1 = InterfaceSchema(
                "gen.Interface1",
                mapOf(
                        "a" to NonNullableField(Int::class.java),
                        "b" to NonNullableField(String::class.java)))

        val iFace2 = InterfaceSchema(
                "gen.Interface2",
                mapOf(
                        "c" to NonNullableField(Int::class.java),
                        "d" to NonNullableField(String::class.java)))

        val class1 = ClassSchema(
                "gen.Derived",
                mapOf(
                        "a" to NonNullableField(Int::class.java),
                        "b" to NonNullableField(String::class.java),
                        "c" to NonNullableField(Int::class.java),
                        "d" to NonNullableField(String::class.java)),
                interfaces = listOf(cc.build(iFace1), cc.build(iFace2)))

        val clazz = cc.build(class1)
        val testA = 42
        val testB = "don't touch me, I'm scared"
        val testC = 0xDEAD
        val testD = "wibble"
        val i = clazz.constructors[0].newInstance(testA, testB, testC, testD) as SimpleFieldAccess

        assertEquals(testA, i["a"])
        assertEquals(testB, i["b"])
        assertEquals(testC, i["c"])
        assertEquals(testD, i["d"])
    }

    @Test
    fun `interface implementing interface`() {
        val iFace1 = InterfaceSchema(
                "gen.Interface1",
                mapOf(
                        "a" to NonNullableField(Int::class.java),
                        "b" to NonNullableField(String::class.java)))

        val iFace2 = InterfaceSchema(
                "gen.Interface2",
                mapOf(
                        "c" to NonNullableField(Int::class.java),
                        "d" to NonNullableField(String::class.java)),
                interfaces = listOf(cc.build(iFace1)))

        val class1 = ClassSchema(
                "gen.Derived",
                mapOf(
                        "a" to NonNullableField(Int::class.java),
                        "b" to NonNullableField(String::class.java),
                        "c" to NonNullableField(Int::class.java),
                        "d" to NonNullableField(String::class.java)),
                interfaces = listOf(cc.build(iFace2)))

        val clazz = cc.build(class1)
        val testA = 99
        val testB = "green is not a creative colour"
        val testC = 7
        val testD = "I like jam"
        val i = clazz.constructors[0].newInstance(testA, testB, testC, testD) as SimpleFieldAccess

        assertEquals(testA, i["a"])
        assertEquals(testB, i["b"])
        assertEquals(testC, i["c"])
        assertEquals(testD, i["d"])
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun `null parameter small int`() {
        val className = "iEnjoySwede"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(Int::class.java)))

        val clazz = cc.build(schema)
        val a: Int? = null
        clazz.constructors[0].newInstance(a)
    }

    @Test(expected = NullablePrimitiveException::class)
    fun `nullable parameter small int`() {
        val className = "iEnjoySwede"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NullableField(Int::class.java)))

        cc.build(schema)
    }

    @Test
    fun `nullable parameter integer`() {
        val className = "iEnjoyWibble"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NullableField(Integer::class.java)))

        val clazz = cc.build(schema)
        val a1: Int? = null
        clazz.constructors[0].newInstance(a1)

        val a2: Int? = 10
        clazz.constructors[0].newInstance(a2)
    }

    @Test
    fun `non nullable parameter integer with non null`() {
        val className = "iEnjoyWibble"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(Integer::class.java)))

        val clazz = cc.build(schema)

        val a: Int? = 10
        clazz.constructors[0].newInstance(a)
    }

    @Test(expected = java.lang.reflect.InvocationTargetException::class)
    fun `non nullable parameter integer with null`() {
        val className = "iEnjoyWibble"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(Integer::class.java)))

        val clazz = cc.build(schema)

        val a: Int? = null
        clazz.constructors[0].newInstance(a)
    }

    @Test
    fun `int array`() {
        val className = "iEnjoyPotato"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(IntArray::class.java)))

        val clazz = cc.build(schema)

        val i = clazz.constructors[0].newInstance(intArrayOf(1, 2, 3)) as SimpleFieldAccess

        val arr = clazz.getMethod("getA").invoke(i)

        assertEquals(1, (arr as IntArray)[0])
        assertEquals(2, arr[1])
        assertEquals(3, arr[2])
        assertEquals("$className{a=[1, 2, 3]}", i.toString())
    }

    @Test(expected = java.lang.reflect.InvocationTargetException::class)
    fun `nullable int array throws`() {
        val className = "iEnjoySwede"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(IntArray::class.java)))

        val clazz = cc.build(schema)

        val a: IntArray? = null
        clazz.constructors[0].newInstance(a)
    }

    @Test
    fun `integer array`() {
        val className = "iEnjoyFlan"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NonNullableField(Array<Int>::class.java)))

        val clazz = cc.build(schema)

        val i = clazz.constructors[0].newInstance(arrayOf(1, 2, 3)) as SimpleFieldAccess
        val arr: Array<Int> = uncheckedCast(clazz.getMethod("getA").invoke(i))

        assertEquals(1, arr[0])
        assertEquals(2, arr[1])
        assertEquals(3, arr[2])
        assertEquals("$className{a=[1, 2, 3]}", i.toString())
    }

    @Test
    fun `int array with ints`() {
        val className = "iEnjoyCrumble"
        val schema = ClassSchema(
                "gen.$className", mapOf(
                "a" to Int::class.java,
                "b" to IntArray::class.java,
                "c" to Int::class.java).mapValues { NonNullableField(it.value) })

        val clazz = cc.build(schema)
        val i = clazz.constructors[0].newInstance(2, intArrayOf(4, 8), 16) as SimpleFieldAccess

        assertEquals(2, clazz.getMethod("getA").invoke(i))
        assertEquals(4, (clazz.getMethod("getB").invoke(i) as IntArray)[0])
        assertEquals(8, (clazz.getMethod("getB").invoke(i) as IntArray)[1])
        assertEquals(16, clazz.getMethod("getC").invoke(i))
        assertEquals("$className{a=2, b=[4, 8], c=16}", i.toString())
    }

    @Test
    fun `multiple int arrays`() {
        val className = "iEnjoyJam"
        val schema = ClassSchema(
                "gen.$className", mapOf(
                "a" to IntArray::class.java,
                "b" to Int::class.java,
                "c" to IntArray::class.java).mapValues { NonNullableField(it.value) })

        val clazz = cc.build(schema)
        val i = clazz.constructors[0].newInstance(intArrayOf(1, 2), 3, intArrayOf(4, 5, 6))

        assertEquals(1, (clazz.getMethod("getA").invoke(i) as IntArray)[0])
        assertEquals(2, (clazz.getMethod("getA").invoke(i) as IntArray)[1])
        assertEquals(3, clazz.getMethod("getB").invoke(i))
        assertEquals(4, (clazz.getMethod("getC").invoke(i) as IntArray)[0])
        assertEquals(5, (clazz.getMethod("getC").invoke(i) as IntArray)[1])
        assertEquals(6, (clazz.getMethod("getC").invoke(i) as IntArray)[2])
        assertEquals("$className{a=[1, 2], b=3, c=[4, 5, 6]}", i.toString())
    }

    @Test
    fun `string array`() {
        val className = "iEnjoyToast"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NullableField(Array<String>::class.java)))

        val clazz = cc.build(schema)

        val i = clazz.constructors[0].newInstance(arrayOf("toast", "butter", "jam"))
        val arr: Array<String> = uncheckedCast(clazz.getMethod("getA").invoke(i))

        assertEquals("toast", arr[0])
        assertEquals("butter", arr[1])
        assertEquals("jam", arr[2])
    }

    @Test
    fun `string arrays`() {
        val className = "iEnjoyToast"
        val schema = ClassSchema(
                "gen.$className",
                mapOf(
                        "a" to Array<String>::class.java,
                        "b" to String::class.java,
                        "c" to Array<String>::class.java).mapValues { NullableField(it.value) })

        val clazz = cc.build(schema)

        val i = clazz.constructors[0].newInstance(
                arrayOf("bread", "spread", "cheese"),
                "and on the side",
                arrayOf("some pickles", "some fries"))

        val arr1: Array<String> = uncheckedCast(clazz.getMethod("getA").invoke(i))
        val arr2: Array<String> = uncheckedCast(clazz.getMethod("getC").invoke(i))

        assertEquals("bread", arr1[0])
        assertEquals("spread", arr1[1])
        assertEquals("cheese", arr1[2])
        assertEquals("and on the side", clazz.getMethod("getB").invoke(i))
        assertEquals("some pickles", arr2[0])
        assertEquals("some fries", arr2[1])
    }

    @Test
    fun `nullable sets annotations`() {
        val className = "iEnjoyJam"
        val schema = ClassSchema(
                "gen.$className",
                mapOf("a" to NullableField(String::class.java),
                        "b" to NonNullableField(String::class.java)))

        val clazz = cc.build(schema)

        assertEquals(2, clazz.declaredFields.size)
        assertEquals(1, clazz.getDeclaredField("a").annotations.size)
        assertEquals(Nullable::class.java, clazz.getDeclaredField("a").annotations[0].annotationClass.java)
        assertEquals(1, clazz.getDeclaredField("b").annotations.size)
        assertEquals(Nonnull::class.java, clazz.getDeclaredField("b").annotations[0].annotationClass.java)
        assertEquals(1, clazz.getMethod("getA").annotations.size)
        assertEquals(Nullable::class.java, clazz.getMethod("getA").annotations[0].annotationClass.java)
        assertEquals(1, clazz.getMethod("getB").annotations.size)
        assertEquals(Nonnull::class.java, clazz.getMethod("getB").annotations[0].annotationClass.java)
    }

    @Test
    fun beanTest() {
        val schema = ClassSchema(
                "pantsPantsPants",
                mapOf("a" to NonNullableField(Integer::class.java)))
        val clazz = cc.build(schema)
        val descriptors = Introspector.getBeanInfo(clazz).propertyDescriptors

        assertEquals(2, descriptors.size)
        assertNotEquals(null, descriptors.find { it.name == "a" })
        assertNotEquals(null, descriptors.find { it.name == "class" })
    }
}
