/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import net.corda.core.crypto.AddressFormatException
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EncodingUtilsTest {

    val testString = "Hello World"
    val testBytes = testString.toByteArray()
    val testBase58String = "JxF12TrwUP45BMd" // Base58 format for Hello World.
    val testBase64String = "SGVsbG8gV29ybGQ=" // Base64 format for Hello World.
    val testHexString = "48656C6C6F20576F726C64" // HEX format for Hello World.

    // Encoding tests
    @Test
    fun `encoding Hello World`() {
        assertEquals(testBase58String, testBytes.toBase58())
        assertEquals(testBase64String, testBytes.toBase64())
        assertEquals(testHexString, testBytes.toHex())
    }

    @Test
    fun `empty encoding`() {
        assertEquals("", EMPTY_BYTE_ARRAY.toBase58())
        assertEquals("", EMPTY_BYTE_ARRAY.toBase64())
        assertEquals("", EMPTY_BYTE_ARRAY.toHex())
    }

    @Test
    fun `encoding 7 zero bytes`() {
        val sevenZeroByteArray = ByteArray(7)
        assertEquals("1111111", sevenZeroByteArray.toBase58())
        assertEquals("AAAAAAAAAA==", sevenZeroByteArray.toBase64())
        assertEquals("00000000000000", sevenZeroByteArray.toHex())
    }

    //Decoding tests
    @Test
    fun `decoding to real String`() {
        assertEquals(testString, testBase58String.base58ToRealString())
        assertEquals(testString, testBase64String.base64ToRealString())
        assertEquals(testString, testHexString.hexToRealString())
    }

    @Test
    fun `decoding empty Strings`() {
        assertEquals("", "".base58ToRealString())
        assertEquals("", "".base64ToRealString())
        assertEquals("", "".hexToRealString())
    }

    @Test
    fun `decoding lowercase and mixed HEX`() {
        val testHexStringLowercase = testHexString.toLowerCase()
        assertEquals(testHexString.hexToRealString(), testHexStringLowercase.hexToRealString())

        val testHexStringMixed = testHexString.replace('C', 'c')
        assertEquals(testHexString.hexToRealString(), testHexStringMixed.hexToRealString())
    }

    @Test
    fun `decoding on wrong format`() {
        // the String "Hello World" is not a valid Base58 or Base64 or HEX format
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

    //Encoding changers tests
    @Test
    fun `change encoding between base58, base64, hex`() {
        // base58 to base64
        assertEquals(testBase64String, testBase58String.base58toBase64())
        // base58 to hex
        assertEquals(testHexString, testBase58String.base58toHex())
        // base64 to base58
        assertEquals(testBase58String, testBase64String.base64toBase58())
        // base64 to hex
        assertEquals(testHexString, testBase64String.base64toHex())
        // hex to base58
        assertEquals(testBase58String, testHexString.hexToBase58())
        // hex to base64
        assertEquals(testBase64String, testHexString.hexToBase64())
    }
}
