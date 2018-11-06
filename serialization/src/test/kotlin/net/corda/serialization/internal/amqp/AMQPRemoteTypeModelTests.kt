package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import net.corda.serialization.internal.model.*
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.IllegalArgumentException
import java.util.*

class AMQPRemoteTypeModelTests {

    @Rule
    @JvmField
    val serializationEnvRule = SerializationEnvironmentRule()

    private val factory = testDefaultFactory()
    private val typeModel = AMQPRemoteTypeModel()

    interface Interface<P, Q, R> {
        val array: Array<out P>
        val list: List<Q>
        val map: Map<Q, R>
    }

    enum class Enum : Interface<String, IntArray, Int> {
        FOO, BAR, BAZ;

        override val array: Array<out String> get() = emptyArray()
        override val list: List<IntArray> get() = emptyList()
        override val map: Map<IntArray, Int> get() = emptyMap()
    }

    open class Superclass<K, V>(override val array: Array<out String>, override val list: List<K>, override val map: Map<K, V>)
        : Interface<String, K, V>

    class C<V>(array: Array<out String>, list: List<UUID>, map: Map<UUID, V>, val enum: Enum): Superclass<UUID, V>(array, list, map)

    @Test
    fun `round-trip some types through AMQP serialisations`() {
        arrayOf("").assertRemoteType("String[]")
        listOf(1).assertRemoteType("List<?>")
        arrayOf(listOf(1)).assertRemoteType("List[]")
        Enum.BAZ.assertRemoteType("Enum(FOO|BAR|BAZ)")
        mapOf("string" to 1).assertRemoteType("Map<?, ?>")
        arrayOf(byteArrayOf(1, 2, 3)).assertRemoteType("byte[][]")

        C(arrayOf("a", "b"), listOf(UUID.randomUUID()), mapOf(UUID.randomUUID() to intArrayOf(1, 2, 3)), Enum.BAZ)
                .assertRemoteType("""
            C: Interface<String, UUID, ?>
              array: String[]
              enum: Enum(FOO|BAR|BAZ)
              list: List<UUID>
              map: Map<UUID, ?>
        """)
    }

    private fun getRemoteType(obj: Any): RemoteTypeInformation {
        val output = SerializationOutput(factory)
        val schema = output.serializeAndReturnSchema(obj).schema
        val values = typeModel.interpret(schema).values
        return values.find { it.typeIdentifier.getLocalType().asClass().isAssignableFrom(obj::class.java) } ?:
        throw IllegalArgumentException(
                "Can't find ${obj::class.java.name} in ${values.map { it.typeIdentifier.name}}")
    }

    private fun Any.assertRemoteType(prettyPrinted: String) {
        assertEquals(prettyPrinted.trimIndent(), getRemoteType(this).prettyPrint())
    }
}