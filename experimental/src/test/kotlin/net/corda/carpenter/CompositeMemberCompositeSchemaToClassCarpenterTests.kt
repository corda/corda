package net.corda.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.amqp.*

import org.junit.Test
import kotlin.test.assertEquals
import net.corda.carpenter.test.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@CordaSerializable
interface I_ {
    val a: Int
}

/*
 * Where a class has a member that is also a composite type or interface
 */
class CompositeMembers : AmqpCarpenterBase() {

    @Test
    fun bothKnown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(val a: Int)

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)

        val amqpObj = obj.first as B

        assertEquals(testB, amqpObj.b)
        assertEquals(testA, amqpObj.a.a)
        assertEquals(2, obj.second.schema.types.size)
        assert(obj.second.schema.types[0] is CompositeType)
        assert(obj.second.schema.types[1] is CompositeType)

        var amqpSchemaA: CompositeType? = null
        var amqpSchemaB: CompositeType? = null

        for (type in obj.second.schema.types) {
            when (type.name.split ("$").last()) {
                "A" -> amqpSchemaA = type as CompositeType
                "B" -> amqpSchemaB = type as CompositeType
            }
        }

        assert(amqpSchemaA != null)
        assert(amqpSchemaB != null)

        /*
         * Just ensure the amqp schema matches what we want before we go messing
         * around with the internals
         */
        assertEquals(1, amqpSchemaA?.fields?.size)
        assertEquals("a", amqpSchemaA!!.fields[0].name)
        assertEquals("int", amqpSchemaA.fields[0].type)

        assertEquals(2, amqpSchemaB?.fields?.size)
        assertEquals("a", amqpSchemaB!!.fields[0].name)
        assertEquals(classTestName("A"), amqpSchemaB.fields[0].type)
        assertEquals("b", amqpSchemaB.fields[1].name)
        assertEquals("int", amqpSchemaB.fields[1].type)

        val metaSchema = obj.second.schema.carpenterSchema()

        /* if we know all the classes there is nothing to really achieve here */
        assert(metaSchema.carpenterSchemas.isEmpty())
        assert(metaSchema.dependsOn.isEmpty())
        assert(metaSchema.dependencies.isEmpty())
    }

    /* you cannot have an element of a composite class we know about
       that is unknown as that should be impossible. If we have the containing
       class in the class path then we must have all of it's constituent elements */
    @Test(expected = UncarpentableException::class)
    fun nestedIsUnknown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))
        val amqpSchema = obj.second.schema.curruptName(listOf (classTestName ("A")))

        assert(obj.first is B)

        amqpSchema.carpenterSchema()
    }

    @Test
    fun ParentIsUnknown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)

        val amqpSchema = obj.envelope.schema.curruptName(listOf(classTestName("B")))

        val carpenterSchema = amqpSchema.carpenterSchema()

        assertEquals(1, carpenterSchema.size)

        val metaCarpenter = MetaCarpenter(carpenterSchema)

        metaCarpenter.build()

        assert(curruptName(classTestName("B")) in metaCarpenter.objects)
    }

    @Test
    fun BothUnkown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)

        val amqpSchema = obj.envelope.schema.curruptName(listOf(classTestName("A"), classTestName("B")))

        val carpenterSchema = amqpSchema.carpenterSchema()

        /* just verify we're in the expected initial state, A is carpentable, B is not because
           it depends on A and the dependency chains are in place */
        assertEquals(1, carpenterSchema.size)
        assertEquals(curruptName(classTestName("A")), carpenterSchema.carpenterSchemas.first().name)
        assertEquals(1, carpenterSchema.dependencies.size)
        assert(curruptName(classTestName("B")) in carpenterSchema.dependencies)
        assertEquals(1, carpenterSchema.dependsOn.size)
        assert(curruptName(classTestName("A")) in carpenterSchema.dependsOn)

        /* test meta carpenter lets us single step over the creation */
        val metaCarpenter = TestMetaCarpenter(carpenterSchema)

        /* we've built nothing so nothing should be there */
        assertEquals(0, metaCarpenter.objects.size)

        /* first iteration, carpent A, resolve deps and mark B as carpentable */
        metaCarpenter.build()

        /* one build iteration should have carpetned up A and worked out that B is now buildable
           given it's depedencies have been satisfied */
        assertTrue(curruptName(classTestName("A")) in metaCarpenter.objects)
        assertFalse(curruptName(classTestName("B")) in metaCarpenter.objects)

        assertEquals(1, carpenterSchema.carpenterSchemas.size)
        assertEquals(curruptName(classTestName("B")), carpenterSchema.carpenterSchemas.first().name)
        assertTrue(carpenterSchema.dependencies.isEmpty())
        assertTrue(carpenterSchema.dependsOn.isEmpty())

        /* second manual iteration, will carpent B */
        metaCarpenter.build()
        assert(curruptName(classTestName("A")) in metaCarpenter.objects)
        assert(curruptName(classTestName("B")) in metaCarpenter.objects)

        assertTrue(carpenterSchema.carpenterSchemas.isEmpty())
    }

    @Test(expected = UncarpentableException::class)
    fun nestedIsUnkownInherited() {
        val testA = 10
        val testB = 20
        val testC = 30

        @CordaSerializable
        open class A(val a: Int)

        @CordaSerializable
        class B(a: Int, var b: Int) : A(a)

        @CordaSerializable
        data class C(val b: B, var c: Int)

        val c = C(B(testA, testB), testC)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(c))

        assert(obj.first is C)

        val amqpSchema = obj.envelope.schema.curruptName(listOf(classTestName("A"), classTestName("B")))

        amqpSchema.carpenterSchema()
    }

    @Test(expected = UncarpentableException::class)
    fun nestedIsUnknownInheritedUnkown() {
        val testA = 10
        val testB = 20
        val testC = 30

        @CordaSerializable
        open class A(val a: Int)

        @CordaSerializable
        class B(a: Int, var b: Int) : A(a)

        @CordaSerializable
        data class C(val b: B, var c: Int)

        val c = C(B(testA, testB), testC)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(c))

        assert(obj.first is C)

        val amqpSchema = obj.envelope.schema.curruptName(listOf(classTestName("A"), classTestName("B")))

        amqpSchema.carpenterSchema()
    }

    @Test
    fun parentsIsUnknownWithUnkownInheritedMember() {
        val testA = 10
        val testB = 20
        val testC = 30

        @CordaSerializable
        open class A(val a: Int)

        @CordaSerializable
        class B(a: Int, var b: Int) : A(a)

        @CordaSerializable
        data class C(val b: B, var c: Int)

        val c = C(B(testA, testB), testC)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(c))

        assert(obj.first is C)

        val amqpSchema = obj.envelope.schema.curruptName(listOf(classTestName("A"), classTestName("B")))
    }


    /*
     * In this case B holds an element of Interface I_ which is an A but we don't know of A
     * but we do know about I_
     */
    @Test
    fun nestedIsInterfaceToUnknown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)
    }

    @Test
    fun nestedIsUnknownInterface() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)
    }

    @Test
    fun ParentsIsInterfaceToUnkown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeRtnEnvelope(serialise(b))

        assert(obj.first is B)
    }
}

