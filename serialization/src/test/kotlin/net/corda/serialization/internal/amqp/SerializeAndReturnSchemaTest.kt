package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.serialization.internal.amqp.testutils.testName
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SerializeAndReturnSchemaTest {
    // the 'this' reference means we can't just move this to the common test utils
    @Suppress("NOTHING_TO_INLINE")
    private inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"

    val factory = testDefaultFactoryNoEvolution()

    // just a simple test to verify the internal test extension for serialize does
    // indeed give us the correct schema back. This is more useful in support of other
    // tests rather than by itself but for those to be reliable this also needs
    // testing
    @Test
    fun getSchema() {
        data class C(val a: Int, val b: Int)

        val a = 1
        val b = 2

        val sc = SerializationOutput(factory).serializeAndReturnSchema(C(a, b))

        assertEquals(1, sc.schema.types.size)
        assertEquals(classTestName("C"), sc.schema.types.first().name)
        assertTrue(sc.schema.types.first() is CompositeType)
        assertEquals(2, (sc.schema.types.first() as CompositeType).fields.size)
        assertNotNull((sc.schema.types.first() as CompositeType).fields.find { it.name == "a" })
        assertNotNull((sc.schema.types.first() as CompositeType).fields.find { it.name == "b" })
    }
}
