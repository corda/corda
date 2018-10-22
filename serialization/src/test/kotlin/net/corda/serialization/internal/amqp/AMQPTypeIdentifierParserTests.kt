package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.UnsignedShort
import org.junit.Test
import java.lang.IllegalArgumentException
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AMQPTypeIdentifierParserTests {

    @Test
    fun `primitives and arrays`() {
        assertParseResult<Int>("int")
        assertParseResult<IntArray>("int[p]")
        assertParseResult<Array<Int>>("int[]")
        assertParseResult<Array<IntArray>>("int[p][]")
        assertParseResult<Array<Array<Int>>>("int[][]")
        assertParseResult<ByteArray>("binary")
        assertParseResult<Array<ByteArray>>("binary[]")
        assertParseResult<Array<UnsignedShort>>("ushort[]")
        assertParseResult<Array<Array<String>>>("string[][]")

        // We set a limit to the depth of arrays-of-arrays-of-arrays...
        assertFailsWith<IllegalArgumentException> {
            AMQPTypeIdentifierParser.parse("string" + "[]".repeat(33))
        }
    }

    @Test
    fun `unparameterised types`() {
        assertParseResult<LocalDateTime>("java.time.LocalDateTime")
        assertParseResult<Array<LocalDateTime>>("java.time.LocalDateTime[]")
        assertParseResult<Array<Array<LocalDateTime>>>("java.time.LocalDateTime[][]")
    }

    interface WithParameter<T> {
        val value: T
    }

    interface WithParameters<P, Q> {
        val p: Array<out P>
        val q: WithParameter<Array<Q>>
    }

    @Test
    fun `parameterised types, nested, with arrays`() {
        assertParsesTo<WithParameters<IntArray, WithParameter<Array<WithParameters<Array<Array<Date>>, UUID>>>>>(
                "WithParameters<int[], WithParameter<WithParameters<Date[][], UUID>[]>>"
        )

        // We set a limit to the maximum depth of nested type parameters.
        assertFailsWith<IllegalArgumentException> {
            AMQPTypeIdentifierParser.parse("WithParameter<".repeat(33) + ">".repeat(33))
        }
    }

    @Test
    fun `compatibility test`() {
        assertParsesCompatibly<Int>()
        assertParsesCompatibly<IntArray>()
        assertParsesCompatibly<Array<Int>>()
        // List<Int> is treated as List<? extends Int>,
        // and typeForName resolves wildcards to ? rather than their upper bounds
        assertParsesTo<List<Int>>("List<?>")
        // No idea
        assertParsesTo<WithParameter<*>>("WithParameter<?>")
        assertParsesCompatibly<WithParameter<Int>>()
        assertParsesCompatibly<Array<out WithParameter<Int>>>()
        assertParsesCompatibly<WithParameters<IntArray, WithParameter<Array<WithParameters<Array<Array<Date>>, UUID>>>>>()
    }

    private inline fun <reified T> assertParseResult(typeString: String) {
        assertEquals(TypeIdentifier.forGenericType(typeOf<T>()), AMQPTypeIdentifierParser.parse(typeString))
    }

    private inline fun <reified T> typeOf() = object : TypeToken<T>() {}.type

    private inline fun <reified T> assertParsesCompatibly() = assertParsesCompatibly(typeOf<T>())

    private fun assertParsesCompatibly(type: Type) {
        assertParsesTo(type, TypeIdentifier.forGenericType(type).prettyPrint())
    }

    private inline fun <reified T> assertParsesTo(expectedIdentifierPrettyPrint: String) {
        assertParsesTo(typeOf<T>(), expectedIdentifierPrettyPrint)
    }

    private fun assertParsesTo(type: Type, expectedIdentifierPrettyPrint: String) {
        val nameAccordingToSerializerFactory = SerializerFactory.nameForType(type)
        val actualIdentifier = AMQPTypeIdentifierParser.parse(nameAccordingToSerializerFactory)
        assertEquals(expectedIdentifierPrettyPrint, actualIdentifier.prettyPrint())
    }
}