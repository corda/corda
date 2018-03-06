/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.junit.Test
import java.math.BigInteger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Modified from the bitcoinj library.
 */
class Base58Test {
    @Test
    fun testEncode() {
        val testbytes = "Hello World".toByteArray()
        assertEquals("JxF12TrwUP45BMd", Base58.encode(testbytes))

        val bi = BigInteger.valueOf(3471844090L)
        assertEquals("16Ho7Hs", Base58.encode(bi.toByteArray()))

        val zeroBytes1 = ByteArray(1)
        assertEquals("1", Base58.encode(zeroBytes1))

        val zeroBytes7 = ByteArray(7)
        assertEquals("1111111", Base58.encode(zeroBytes7))

        // test empty encode
        assertEquals("", Base58.encode(EMPTY_BYTE_ARRAY))
    }

    @Test
    fun testDecode() {
        val testbytes = "Hello World".toByteArray()
        val actualbytes = Base58.decode("JxF12TrwUP45BMd")
        assertTrue(String(actualbytes)) { Arrays.equals(testbytes, actualbytes) }

        assertTrue("1") { Arrays.equals(ByteArray(1), Base58.decode("1")) }
        assertTrue("1111") { Arrays.equals(ByteArray(4), Base58.decode("1111")) }

        try {
            Base58.decode("This isn't valid base58")
            fail()
        } catch (e: AddressFormatException) {
            // expected
        }

        Base58.decodeChecked("4stwEBjT6FYyVV")

        // Checksum should fail.
        try {
            Base58.decodeChecked("4stwEBjT6FYyVW")
            fail()
        } catch (e: AddressFormatException) {
            // expected
        }

        // Input is too short.
        try {
            Base58.decodeChecked("4s")
            fail()
        } catch (e: AddressFormatException) {
            // expected
        }

        // Test decode of empty String.
        assertEquals(0, Base58.decode("").size)

        // Now check we can correctly decode the case where the high bit of the first byte is not zero, so BigInteger
        // sign extends. Fix for a bug that stopped us parsing keys exported using sipas patch.
        Base58.decodeChecked("93VYUMzRG9DdbRP72uQXjaWibbQwygnvaCu9DumcqDjGybD864T")
    }

    @Test
    fun testDecodeToBigInteger() {
        val input = Base58.decode("129")
        assertEquals(BigInteger(1, input), Base58.decodeToBigInteger("129"))
    }
}
