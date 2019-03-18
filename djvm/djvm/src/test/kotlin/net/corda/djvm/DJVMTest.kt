package net.corda.djvm

import org.assertj.core.api.Assertions.*
import org.junit.Assert.*
import org.junit.Test
import sandbox.java.lang.sandbox
import java.text.DecimalFormatSymbols

class DJVMTest : TestBase() {

    @Test
    fun testDJVMString() {
        val djvmString = sandbox.java.lang.String("New Value")
        assertNotEquals(djvmString, "New Value")
        assertEquals(djvmString, "New Value".sandbox())
    }

    @Test
    fun testSimpleIntegerFormats() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null,
                    stringOf("%d-%d-%d-%d"),
                    arrayOf(intOf(10), longOf(999999L), shortOf(1234), byteOf(108))
                ).toString()
        }
        assertEquals("10-999999-1234-108", result)
    }

    @Test
    fun testHexFormat() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null, stringOf("%0#6x"), arrayOf(intOf(768))).toString()
        }
        assertEquals("0x0300", result)
    }

    @Test
    fun testDoubleFormat() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null, stringOf("%9.4f"), arrayOf(doubleOf(1234.5678))).toString()
        }
        val sep = DecimalFormatSymbols.getInstance().decimalSeparator
        assertEquals("1234${sep}5678", result)
    }

    @Test
    fun testFloatFormat() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null, stringOf("%7.2f"), arrayOf(floatOf(1234.5678f))).toString()
        }
        val sep = DecimalFormatSymbols.getInstance().decimalSeparator
        assertEquals("1234${sep}57", result)
    }

    @Test
    fun testCharFormat() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null, stringOf("[%c]"), arrayOf(charOf('A'))).toString()
        }
        assertEquals("[A]", result)
    }

    @Test
    fun testObjectFormat() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            stringClass.getMethod("format", stringClass, Array<Any>::class.java)
                .invoke(null, stringOf("%s"), arrayOf(object : sandbox.java.lang.Object() {})).toString()
        }
        assertThat(result).startsWith("sandbox.java.lang.Object@")
    }

    @Test
    fun testStringEquality() {
        val number = sandbox.java.lang.String.valueOf((Double.MIN_VALUE / 2.0) * 2.0)
        require(number == "0.0".sandbox())
    }

    @Test
    fun testSandboxingArrays() = parentedSandbox {
        with(DJVM(classLoader)) {
            val result = sandbox(arrayOf(1, 10L, "Hello World", '?', false, 1234.56))
            assertThat(result).isEqualTo(
                arrayOf(intOf(1), longOf(10), stringOf("Hello World"), charOf('?'), booleanOf(false), doubleOf(1234.56)))
        }
    }

    @Test
    fun testUnsandboxingObjectArray() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            unsandbox(arrayOf(intOf(1), longOf(10L), stringOf("Hello World"), charOf('?'), booleanOf(false), doubleOf(1234.56)))
        }
        assertThat(result)
            .isEqualTo(arrayOf(1, 10L, "Hello World", '?', false, 1234.56))
    }

    @Test
    fun testSandboxingPrimitiveArray() = parentedSandbox {
        val result = with(DJVM(classLoader)) {
            sandbox(intArrayOf(1, 2, 3, 10))
        }
        assertThat(result).isEqualTo(intArrayOf(1, 2, 3, 10))
    }

    @Test
    fun testSandboxingIntegersAsObjectArray() = parentedSandbox {
        with(DJVM(classLoader)) {
            val result = sandbox(arrayOf(1, 2, 3, 10))
            assertThat(result).isEqualTo(
                arrayOf(intOf(1), intOf(2), intOf(3), intOf(10))
            )
        }
    }

    @Test
    fun testUnsandboxingArrays() = parentedSandbox {
        val (array, result) = with(DJVM(classLoader)) {
            val arr = arrayOf(
                objectArrayOf(stringOf("Hello")),
                objectArrayOf(longOf(1234000L)),
                objectArrayOf(intOf(1234)),
                objectArrayOf(shortOf(923)),
                objectArrayOf(byteOf(27)),
                objectArrayOf(charOf('X')),
                objectArrayOf(floatOf(987.65f)),
                objectArrayOf(doubleOf(343.282)),
                objectArrayOf(booleanOf(true)),
                ByteArray(1) { 127.toByte() },
                CharArray(1) { '?' }
            )
            Pair(arr, unsandbox(arr) as Array<*>)
        }
        assertEquals(array.size, result.size)
        assertArrayEquals(Array(1) { "Hello" }, result[0] as Array<*>)
        assertArrayEquals(Array(1) { 1234000L }, result[1] as Array<*>)
        assertArrayEquals(Array(1) { 1234 }, result[2] as Array<*>)
        assertArrayEquals(Array(1) { 923.toShort() }, result[3] as Array<*>)
        assertArrayEquals(Array(1) { 27.toByte() }, result[4] as Array<*>)
        assertArrayEquals(Array(1) { 'X' }, result[5] as Array<*>)
        assertArrayEquals(Array(1) { 987.65f }, result[6] as Array<*>)
        assertArrayEquals(Array(1) { 343.282 }, result[7] as Array<*>)
        assertArrayEquals(Array(1) { true }, result[8] as Array<*>)
        assertArrayEquals(ByteArray(1) { 127.toByte() }, result[9] as ByteArray)
        assertArrayEquals(CharArray(1) { '?' }, result[10] as CharArray)
    }
}