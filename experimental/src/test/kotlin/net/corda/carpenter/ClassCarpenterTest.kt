package net.corda.carpenter

import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.test.assertEquals


class ClassCarpenterTest {
    interface DummyInterface {
        val a: String
        val b: Int
    }

    val cc = ClassCarpenter()

    // We have to ignore synthetic fields even though ClassCarpenter doesn't create any because the JaCoCo
    // coverage framework auto-magically injects one method and one field into every class loaded into the JVM.
    val Class<*>.nonSyntheticFields: List<Field> get() = declaredFields.filterNot { it.isSynthetic }
    val Class<*>.nonSyntheticMethods: List<Method> get() = declaredMethods.filterNot { it.isSynthetic }

    @Test
    fun empty() {
        val clazz = cc.build(ClassCarpenter.ClassSchema("gen.EmptyClass", emptyMap(), null))
        assertEquals(0, clazz.nonSyntheticFields.size)
        assertEquals(2, clazz.nonSyntheticMethods.size)   // get, toString
        assertEquals(0, clazz.declaredConstructors[0].parameterCount)
        clazz.newInstance()   // just test there's no exception.
    }

    @Test
    fun prims() {
        val clazz = cc.build(ClassCarpenter.ClassSchema("gen.Prims", mapOf(
                "anIntField" to Int::class.javaPrimitiveType!!,
                "aLongField" to Long::class.javaPrimitiveType!!,
                "someCharField" to Char::class.javaPrimitiveType!!,
                "aShortField" to Short::class.javaPrimitiveType!!,
                "doubleTrouble" to Double::class.javaPrimitiveType!!,
                "floatMyBoat" to Float::class.javaPrimitiveType!!,
                "byteMe" to Byte::class.javaPrimitiveType!!,
                "booleanField" to Boolean::class.javaPrimitiveType!!
        )))
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
        val clazz = cc.build(ClassCarpenter.ClassSchema("gen.Person", mapOf(
                "age" to Int::class.javaPrimitiveType!!,
                "name" to String::class.java
        )))
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
        val (clazz, i) = genPerson()
        assertEquals("Person{age=32, name=Mike}", i.toString())
    }

    @Test(expected = ClassCarpenter.DuplicateName::class)
    fun duplicates() {
        cc.build(ClassCarpenter.ClassSchema("gen.EmptyClass", emptyMap(), null))
        cc.build(ClassCarpenter.ClassSchema("gen.EmptyClass", emptyMap(), null))
    }

    @Test
    fun `can refer to each other`() {
        val (clazz1, i) = genPerson()
        val clazz2 = cc.build(ClassCarpenter.ClassSchema("gen.Referee", mapOf(
                "ref" to clazz1
        )))
        val i2 = clazz2.constructors[0].newInstance(i)
        assertEquals(i, (i2 as SimpleFieldAccess)["ref"])
    }

    @Test
    fun superclasses() {
        val schema1 = ClassCarpenter.ClassSchema("gen.A", mapOf("a" to String::class.java))
        val schema2 = ClassCarpenter.ClassSchema("gen.B", mapOf("b" to String::class.java), schema1)
        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", "xb") as SimpleFieldAccess
        assertEquals("xa", i["a"])
        assertEquals("xb", i["b"])
        assertEquals("B{a=xa, b=xb}", i.toString())
    }

    @Test
    fun interfaces() {
        val schema1 = ClassCarpenter.ClassSchema("gen.A", mapOf("a" to String::class.java))
        val schema2 = ClassCarpenter.ClassSchema("gen.B", mapOf("b" to Int::class.java), schema1, interfaces = listOf(DummyInterface::class.java))
        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", 1) as DummyInterface
        assertEquals("xa", i.a)
        assertEquals(1, i.b)
    }

    @Test(expected = ClassCarpenter.InterfaceMismatch::class)
    fun `mismatched interface`() {
        val schema1 = ClassCarpenter.ClassSchema("gen.A", mapOf("a" to String::class.java))
        val schema2 = ClassCarpenter.ClassSchema("gen.B", mapOf("c" to Int::class.java), schema1, interfaces = listOf(DummyInterface::class.java))
        val clazz = cc.build(schema2)
        val i = clazz.constructors[0].newInstance("xa", 1) as DummyInterface
        assertEquals(1, i.b)
    }

    @Test
    fun `generate interface`() {
        val schema1 = ClassCarpenter.InterfaceSchema("gen.Interface", mapOf("a" to Int::class.java))
        val iface = cc.build(schema1)

        assert(iface.isInterface())
        assert(iface.constructors.isEmpty())
        assertEquals(iface.declaredMethods.size, 1)
        assertEquals(iface.declaredMethods[0].name, "getA")

        val schema2 = ClassCarpenter.ClassSchema("gen.Derived", mapOf("a" to Int::class.java), interfaces = listOf(iface))
        val clazz = cc.build(schema2)
        val testA = 42
        val i = clazz.constructors[0].newInstance(testA) as SimpleFieldAccess

        assertEquals(testA, i["a"])
    }

    @Test
    fun `generate multiple interfaces`() {
        val iFace1 = ClassCarpenter.InterfaceSchema("gen.Interface1", mapOf("a" to Int::class.java, "b" to String::class.java))
        val iFace2 = ClassCarpenter.InterfaceSchema("gen.Interface2", mapOf("c" to Int::class.java, "d" to String::class.java))

        val class1 = ClassCarpenter.ClassSchema(
                "gen.Derived",
                mapOf(
                        "a" to Int::class.java,
                        "b" to String::class.java,
                        "c" to Int::class.java,
                        "d" to String::class.java),
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
        val iFace1 = ClassCarpenter.InterfaceSchema(
                "gen.Interface1",
                mapOf(
                        "a" to Int::class.java,
                        "b" to String::class.java))

        val iFace2 = ClassCarpenter.InterfaceSchema(
                "gen.Interface2",
                mapOf(
                        "c" to Int::class.java,
                        "d" to String::class.java),
                interfaces = listOf(cc.build(iFace1)))

        val class1 = ClassCarpenter.ClassSchema(
                "gen.Derived",
                mapOf(
                        "a" to Int::class.java,
                        "b" to String::class.java,
                        "c" to Int::class.java,
                        "d" to String::class.java),
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
}
