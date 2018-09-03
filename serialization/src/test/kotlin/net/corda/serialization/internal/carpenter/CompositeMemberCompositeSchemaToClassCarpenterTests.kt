package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.testutils.deserializeAndReturnEnvelope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@CordaSerializable
interface I_ {
    val a: Int
}

class CompositeMembers : AmqpCarpenterBase(AllWhitelist) {
    @Test
    fun bothKnown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(val a: Int)

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        require(obj.obj is B)

        val amqpObj = obj.obj as B

        assertEquals(testB, amqpObj.b)
        assertEquals(testA, amqpObj.a.a)
        assertEquals(2, obj.envelope.schema.types.size)
        require(obj.envelope.schema.types[0] is CompositeType)
        require(obj.envelope.schema.types[1] is CompositeType)

        var amqpSchemaA: CompositeType? = null
        var amqpSchemaB: CompositeType? = null

        for (type in obj.envelope.schema.types) {
            when (type.name.split("$").last()) {
                "A" -> amqpSchemaA = type as CompositeType
                "B" -> amqpSchemaB = type as CompositeType
            }
        }

        require(amqpSchemaA != null)
        require(amqpSchemaB != null)

        // Just ensure the amqp schema matches what we want before we go messing
        // around with the internals
        assertEquals(1, amqpSchemaA?.fields?.size)
        assertEquals("a", amqpSchemaA!!.fields[0].name)
        assertEquals("int", amqpSchemaA.fields[0].type)

        assertEquals(2, amqpSchemaB?.fields?.size)
        assertEquals("a", amqpSchemaB!!.fields[0].name)
        assertEquals(classTestName("A"), amqpSchemaB.fields[0].type)
        assertEquals("b", amqpSchemaB.fields[1].name)
        assertEquals("int", amqpSchemaB.fields[1].type)

        val metaSchema = obj.envelope.schema.carpenterSchema(ClassLoader.getSystemClassLoader())

        // if we know all the classes there is nothing to really achieve here
        require(metaSchema.carpenterSchemas.isEmpty())
        require(metaSchema.dependsOn.isEmpty())
        require(metaSchema.dependencies.isEmpty())
    }

    // you cannot have an element of a composite class we know about
    // that is unknown as that should be impossible. If we have the containing
    // class in the class path then we must have all of it's constituent elements
    @Test(expected = UncarpentableException::class)
    fun nestedIsUnknown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)

        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))
        val amqpSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A")))

        require(obj.obj is B)

        amqpSchema.carpenterSchema(ClassLoader.getSystemClassLoader())
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
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        require(obj.obj is B)

        val amqpSchema = obj.envelope.schema.mangleNames(listOf(classTestName("B")))
        val carpenterSchema = amqpSchema.carpenterSchema(ClassLoader.getSystemClassLoader())

        assertEquals(1, carpenterSchema.size)

        val metaCarpenter = MetaCarpenter(carpenterSchema, ClassCarpenterImpl(whitelist = AllWhitelist))

        metaCarpenter.build()

        require(mangleName(classTestName("B")) in metaCarpenter.objects)
    }

    @Test
    fun BothUnknown() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val b = B(A(testA), testB)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        require(obj.obj is B)

        val amqpSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A"), classTestName("B")))
        val carpenterSchema = amqpSchema.carpenterSchema(ClassLoader.getSystemClassLoader())

        // just verify we're in the expected initial state, A is carpentable, B is not because
        // it depends on A and the dependency chains are in place
        assertEquals(1, carpenterSchema.size)
        assertEquals(mangleName(classTestName("A")), carpenterSchema.carpenterSchemas.first().name)
        assertEquals(1, carpenterSchema.dependencies.size)
        require(mangleName(classTestName("B")) in carpenterSchema.dependencies)
        assertEquals(1, carpenterSchema.dependsOn.size)
        require(mangleName(classTestName("A")) in carpenterSchema.dependsOn)

        val metaCarpenter = TestMetaCarpenter(carpenterSchema, ClassCarpenterImpl(whitelist = AllWhitelist))

        assertEquals(0, metaCarpenter.objects.size)

        // first iteration, carpent A, resolve deps and mark B as carpentable
        metaCarpenter.build()

        // one build iteration should have carpetned up A and worked out that B is now buildable
        //  given it's depedencies have been satisfied
        assertTrue(mangleName(classTestName("A")) in metaCarpenter.objects)
        assertFalse(mangleName(classTestName("B")) in metaCarpenter.objects)

        assertEquals(1, carpenterSchema.carpenterSchemas.size)
        assertEquals(mangleName(classTestName("B")), carpenterSchema.carpenterSchemas.first().name)
        assertTrue(carpenterSchema.dependencies.isEmpty())
        assertTrue(carpenterSchema.dependsOn.isEmpty())

        // second manual iteration, will carpent B
        metaCarpenter.build()
        require(mangleName(classTestName("A")) in metaCarpenter.objects)
        require(mangleName(classTestName("B")) in metaCarpenter.objects)

        // and we must be finished
        assertTrue(carpenterSchema.carpenterSchemas.isEmpty())
    }

    @Test(expected = UncarpentableException::class)
    @Suppress("UNUSED")
    fun nestedIsUnknownInherited() {
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
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(c))

        require(obj.obj is C)

        val amqpSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A"), classTestName("B")))

        amqpSchema.carpenterSchema(ClassLoader.getSystemClassLoader())
    }

    @Test(expected = UncarpentableException::class)
    @Suppress("UNUSED")
    fun nestedIsUnknownInheritedUnknown() {
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
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(c))

        require(obj.obj is C)

        val amqpSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A"), classTestName("B")))

        amqpSchema.carpenterSchema(ClassLoader.getSystemClassLoader())
    }

    @Suppress("UNUSED")
    @Test(expected = UncarpentableException::class)
    fun parentsIsUnknownWithUnknownInheritedMember() {
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
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(c))

        require(obj.obj is C)

        val carpenterSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A"), classTestName("B")))
        TestMetaCarpenter(carpenterSchema.carpenterSchema(
                ClassLoader.getSystemClassLoader()), ClassCarpenterImpl(whitelist = AllWhitelist))
    }

    /*
     * TODO serializer doesn't support inheritnace at the moment, when it does this should work
    @Test
    fun `inheritance`() {
        val testA = 10
        val testB = 20

        @CordaSerializable
        open class A(open val a: Int)

        @CordaSerializable
        class B(override val a: Int, val b: Int) : A (a)

        val b = B(testA, testB)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        require(obj.obj is B)

        val carpenterSchema = obj.envelope.schema.mangleNames(listOf(classTestName("A"), classTestName("B")))
        val metaCarpenter = TestMetaCarpenter(carpenterSchema.carpenterSchema())

        assertEquals(1, metaCarpenter.schemas.carpenterSchemas.size)
        assertEquals(mangleNames(classTestName("B")), metaCarpenter.schemas.carpenterSchemas.first().name)
        assertEquals(1, metaCarpenter.schemas.dependencies.size)
        assertTrue(mangleNames(classTestName("A")) in metaCarpenter.schemas.dependencies)
    }
    */
}

