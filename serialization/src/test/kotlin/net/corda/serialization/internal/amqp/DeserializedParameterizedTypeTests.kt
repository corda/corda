package net.corda.serialization.internal.amqp

import org.junit.Test
import java.io.NotSerializableException
import kotlin.test.assertEquals

class DeserializedParameterizedTypeTests {
    private fun normalise(string: String): String {
        return string.replace(" ", "")
    }

    private fun verify(typeName: String) {
        val type = DeserializedParameterizedType.make(typeName)
        assertEquals(normalise(type.typeName), normalise(typeName))
    }

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
        verify("java.util.List<net.corda.core.contracts.Command<?>>")
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
        for (i in 1..DeserializedParameterizedType.MAX_DEPTH) {
            nested = "java.util.List<$nested>"
        }
        verify(nested)
    }
}