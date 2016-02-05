/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.crypto;

import org.junit.*;

import java.math.*;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** From the bitcoinj library */
public class Base58Test {
    @Test
    public void testEncode() throws Exception {
        byte[] testbytes = "Hello World".getBytes();
        assertEquals("JxF12TrwUP45BMd", Base58.encode(testbytes));

        BigInteger bi = BigInteger.valueOf(3471844090L);
        assertEquals("16Ho7Hs", Base58.encode(bi.toByteArray()));

        byte[] zeroBytes1 = new byte[1];
        assertEquals("1", Base58.encode(zeroBytes1));

        byte[] zeroBytes7 = new byte[7];
        assertEquals("1111111", Base58.encode(zeroBytes7));

        // test empty encode
        assertEquals("", Base58.encode(new byte[0]));
    }

    @Test
    public void testDecode() throws Exception {
        byte[] testbytes = "Hello World".getBytes();
        byte[] actualbytes = Base58.decode("JxF12TrwUP45BMd");
        assertTrue(new String(actualbytes), Arrays.equals(testbytes, actualbytes));

        assertTrue("1", Arrays.equals(Base58.decode("1"), new byte[1]));
        assertTrue("1111", Arrays.equals(Base58.decode("1111"), new byte[4]));

        try {
            Base58.decode("This isn't valid base58");
            fail();
        } catch (AddressFormatException e) {
            // expected
        }

        Base58.decodeChecked("4stwEBjT6FYyVV");

        // Checksum should fail.
        try {
            Base58.decodeChecked("4stwEBjT6FYyVW");
            fail();
        } catch (AddressFormatException e) {
            // expected
        }

        // Input is too short.
        try {
            Base58.decodeChecked("4s");
            fail();
        } catch (AddressFormatException e) {
            // expected
        }

        // Test decode of empty String.
        assertEquals(0, Base58.decode("").length);

        // Now check we can correctly decode the case where the high bit of the first byte is not zero, so BigInteger
        // sign extends. Fix for a bug that stopped us parsing keys exported using sipas patch.
        Base58.decodeChecked("93VYUMzRG9DdbRP72uQXjaWibbQwygnvaCu9DumcqDjGybD864T");
    }

    @Test
    public void testDecodeToBigInteger() {
        byte[] input = Base58.decode("129");
        assertEquals(new BigInteger(1, input), Base58.decodeToBigInteger("129"));
    }
}
