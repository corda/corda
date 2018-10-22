package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.UnsignedShort
import org.junit.Test
import java.io.NotSerializableException
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
        assertFailsWith<IllegalTypeNameParserStateException> {
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
        assertFailsWith<IllegalTypeNameParserStateException> {
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

    // Old tests for DeserializedParameterizedType
    @Test
    fun `test nested`() {
        verify(" java.util.Map < java.util.Map< java.lang.String, java.lang.Integer >, java.util.Map < java.lang.Long , java.lang.String > >")
    }

    @Test
    fun `test simple`() {
        verify("java.util.List<java.lang.String>")
    }

    @Test
    fun `test multiple args`() {
        verify("java.util.Map<java.lang.String,java.lang.Integer>")
    }

    @Test
    fun `test trailing whitespace`() {
        verify("java.util.Map<java.lang.String, java.lang.Integer> ")
    }

    @Test
    fun `test list of commands`() {
        verify("java.util.List<net.corda.core.contracts.Command<net.corda.core.contracts.Command<net.corda.core.contracts.CommandData>>>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test trailing text`() {
        verify("java.util.Map<java.lang.String, java.lang.Integer>foo")
    }

    @Test(expected = NotSerializableException::class)
    fun `test trailing comma`() {
        verify("java.util.Map<java.lang.String, java.lang.Integer,>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test leading comma`() {
        verify("java.util.Map<,java.lang.String, java.lang.Integer>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test middle comma`() {
        verify("java.util.Map<,java.lang.String,, java.lang.Integer>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test trailing close`() {
        verify("java.util.Map<java.lang.String, java.lang.Integer>>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test empty params`() {
        verify("java.util.Map<>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test mid whitespace`() {
        verify("java.u til.List<java.lang.String>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test mid whitespace2`() {
        verify("java.util.List<java.l ng.String>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test wrong number of parameters`() {
        verify("java.util.List<java.lang.String, java.lang.Integer>")
    }

    @Test
    fun `test no parameters`() {
        verify("java.lang.String")
    }

    @Test(expected = NotSerializableException::class)
    fun `test parameters on non-generic type`() {
        verify("java.lang.String<java.lang.Integer>")
    }

    @Test(expected = NotSerializableException::class)
    fun `test excessive nesting`() {
        var nested = "java.lang.Integer"
        for (i in 1..AMQPTypeIdentifierParser.MAX_TYPE_PARAM_DEPTH) {
            nested = "java.util.List<$nested>"
        }
        verify(nested)
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


    private fun normalise(string: String): String {
        return string.replace(" ", "")
    }

    private fun verify(typeName: String) {
        val type = AMQPTypeIdentifierParser.parse(typeName).getLocalType()
        assertEquals(normalise(typeName), normalise(type.typeName))
    }
}