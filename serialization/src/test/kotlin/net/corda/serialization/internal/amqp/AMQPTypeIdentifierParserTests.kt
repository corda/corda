package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.model.TypeIdentifier
import org.junit.Test
import java.lang.reflect.Type
import java.time.LocalDateTime
import kotlin.test.assertEquals

class AMQPTypeIdentifierParserTests {

    @Test
    fun `primitives and arrays`() {
        assertParseResultIs(Int::class.javaPrimitiveType!!, "int", true)
        assertParseResult<Int>("int", false)
        assertParseResult<IntArray>("int[p]")
        assertParseResult<Array<Int>>("int[]")
    }

    @Test
    fun `unparameterised types`() {
        assertParseResult<LocalDateTime>("java.time.LocalDateTime")
        assertParseResult<Array<LocalDateTime>>("java.time.LocalDateTime[]")
    }

    @Test
    fun `erased type`() {
        assertParseResultIs(List::class.java, "java.collections.List")
    }

    private fun assertParseResultIs(expectedType: Type, typeString: String, forcePrimitive: Boolean = false, anySubstitute: String? = null) {
        assertEquals(TypeIdentifier.forGenericType(expectedType), AMQPTypeIdentifierParser.parse(typeString, anySubstitute, forcePrimitive))
    }

    private inline fun <reified T> assertParseResult(typeString: String, forcePrimitive: Boolean = false, anySubstitute: String? = null) {
        assertEquals(TypeIdentifier.forGenericType(typeOf<T>()), AMQPTypeIdentifierParser.parse(typeString, anySubstitute, forcePrimitive))
    }

    private inline fun <reified T> typeOf() = object : TypeToken<T>() {}.type
}