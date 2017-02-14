package net.corda.core.crypto

import org.junit.Test
import java.math.BigInteger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class EncodingUtilsTest {
    @Test
    fun testEncode() {
        // Test Hello World.
        val testbytes = "Hello World".toByteArray()
        assertEquals("JxF12TrwUP45BMd", testbytes.toBase58())
        assertEquals("SGVsbG8gV29ybGQ=", testbytes.toBase64())
        assertEquals("48656C6C6F20576F726C64", testbytes.toHex())

        // Test empty encode.
        val emptyByteArray = ByteArray(0)
        assertEquals("", emptyByteArray.toBase58())
        assertEquals("", emptyByteArray.toBase64())
        assertEquals("", emptyByteArray.toHex())

        // Test 7 zero bytes.
        val sevenZeroByteArray = ByteArray(7)
        assertEquals("1111111", sevenZeroByteArray.toBase58())
        assertEquals("AAAAAAAAAA==", sevenZeroByteArray.toBase64())
        assertEquals("00000000000000", sevenZeroByteArray.toHex())
    }

    @Test
    fun testDecode() {
        val testString = "Hello World"
        val testBase58String = "JxF12TrwUP45BMd"
        val testBase64String = "SGVsbG8gV29ybGQ="
        val testHexString = "48656C6C6F20576F726C64"

        assertEquals(testString, testBase58String.base58ToRealString())
        assertEquals(testString, testBase64String.base64ToRealString())
        assertEquals(testString, testHexString.hexToRealString())

        // Test empty Strings.
        assertEquals("", "".base58ToRealString())
        assertEquals("", "".base64ToRealString())
        assertEquals("", "".hexToRealString())

        // Test for Hex lowercase.
        val testHexStringLowercase = testHexString.toLowerCase()
        assertEquals(testHexString.hexToRealString(), testHexStringLowercase.hexToRealString())

        // Test for Hex mixed.
        val testHexStringMixed = testHexString.replace('C','c')
        assertEquals(testHexString.hexToRealString(), testHexStringMixed.hexToRealString())

        // Test for wrong format.
        try {
            testString.base58ToRealString()
            fail()
        } catch (e: AddressFormatException) {
            // expected.
        }

        try {
            testString.base64ToRealString()
            fail()
        } catch (e: IllegalArgumentException) {
            // expected.
        }

        try {
            testString.hexToRealString()
            fail()
        } catch (e: IllegalArgumentException) {
            // expected.
        }

    }

}
