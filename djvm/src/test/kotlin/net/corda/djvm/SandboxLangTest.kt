package net.corda.djvm

import org.assertj.core.api.Assertions.*
import org.junit.Assert.*
import org.junit.Test
import sandbox.java.lang.sandbox
import sandbox.java.lang.unsandbox

class SandboxLangTest {

    @Test
    fun testDJVMString() {
        val djvmString = sandbox.java.lang.String("New Value")
        assertNotEquals(djvmString, "New Value")
        assertEquals(djvmString, "New Value".sandbox())
    }

    @Test
    fun testSimpleIntegerFormats() {
        val result = sandbox.java.lang.String.format("%d-%d-%d-%d".toDJVM(),
                          10.toDJVM(), 999999L.toDJVM(), 1234.toShort().toDJVM(), 108.toByte().toDJVM()).toString()
        assertEquals("10-999999-1234-108", result)
    }

    @Test
    fun testHexFormat() {
        val result = sandbox.java.lang.String.format("%0#6x".toDJVM(), 768.toDJVM()).toString()
        assertEquals("0x0300", result)
    }

    @Test
    fun testDoubleFormat() {
        val result = sandbox.java.lang.String.format("%9.4f".toDJVM(), 1234.5678.toDJVM()).toString()
        assertEquals("1234.5678", result)
    }

    @Test
    fun testFloatFormat() {
        val result = sandbox.java.lang.String.format("%7.2f".toDJVM(), 1234.5678f.toDJVM()).toString()
        assertEquals("1234.57", result)
    }

    @Test
    fun testCharFormat() {
        val result = sandbox.java.lang.String.format("[%c]".toDJVM(), 'A'.toDJVM()).toString()
        assertEquals("[A]", result)
    }

    @Test
    fun testObjectFormat() {
        val result = sandbox.java.lang.String.format("%s".toDJVM(), object : sandbox.java.lang.Object() {}).toString()
        assertThat(result).startsWith("sandbox.java.lang.Object@")
    }

    @Test
    fun testStringEquality() {
        val number = sandbox.java.lang.String.valueOf((Double.MIN_VALUE / 2.0) * 2.0)
        require(number == "0.0".sandbox())
    }

    @Test
    fun testSandboxingArrays() {
        val result = arrayOf(1, 10L, "Hello World", '?', false, 1234.56).sandbox()
        assertThat(result)
            .isEqualTo(arrayOf(1.toDJVM(), 10L.toDJVM(), "Hello World".toDJVM(), '?'.toDJVM(), false.toDJVM(), 1234.56.toDJVM()))
    }

    @Test
    fun testUnsandboxingObjectArray() {
        val result = arrayOf<sandbox.java.lang.Object>(1.toDJVM(), 10L.toDJVM(), "Hello World".toDJVM(), '?'.toDJVM(), false.toDJVM(), 1234.56.toDJVM()).unsandbox()
        assertThat(result)
                .isEqualTo(arrayOf(1, 10L, "Hello World", '?', false, 1234.56))
    }

    @Test
    fun testSandboxingPrimitiveArray() {
        val result = intArrayOf(1, 2, 3, 10).sandbox()
        assertThat(result).isEqualTo(intArrayOf(1, 2, 3, 10))
    }

    @Test
    fun testSandboxingIntegersAsObjectArray() {
        val result = arrayOf(1, 2, 3, 10).sandbox()
        assertThat(result).isEqualTo(arrayOf(1.toDJVM(), 2.toDJVM(), 3.toDJVM(), 10.toDJVM()))
    }

    private fun String.toDJVM(): sandbox.java.lang.String = sandbox.java.lang.String.toDJVM(this)
    private fun Long.toDJVM(): sandbox.java.lang.Long = sandbox.java.lang.Long.toDJVM(this)
    private fun Int.toDJVM(): sandbox.java.lang.Integer = sandbox.java.lang.Integer.toDJVM(this)
    private fun Short.toDJVM(): sandbox.java.lang.Short = sandbox.java.lang.Short.toDJVM(this)
    private fun Byte.toDJVM(): sandbox.java.lang.Byte = sandbox.java.lang.Byte.toDJVM(this)
    private fun Float.toDJVM(): sandbox.java.lang.Float = sandbox.java.lang.Float.toDJVM(this)
    private fun Double.toDJVM(): sandbox.java.lang.Double = sandbox.java.lang.Double.toDJVM(this)
    private fun Char.toDJVM(): sandbox.java.lang.Character = sandbox.java.lang.Character.toDJVM(this)
    private fun Boolean.toDJVM(): sandbox.java.lang.Boolean = sandbox.java.lang.Boolean.toDJVM(this)
}