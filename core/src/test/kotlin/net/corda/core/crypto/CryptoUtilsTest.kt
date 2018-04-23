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

import com.google.common.collect.Sets
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.math.GroupElement
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.util.*
import kotlin.test.*

/**
 * Run tests for cryptographic algorithms.
 */
class CryptoUtilsTest {

    private val testBytes = "Hello World".toByteArray()

    // key generation test
    @Test
    fun `Generate key pairs`() {
        // testing supported algorithms
        val rsaKeyPair = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val ecdsaKKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val ecdsaRKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val eddsaKeyPair = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val sphincsKeyPair = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)

        // not null private keys
        assertNotNull(rsaKeyPair.private)
        assertNotNull(ecdsaKKeyPair.private)
        assertNotNull(ecdsaRKeyPair.private)
        assertNotNull(eddsaKeyPair.private)
        assertNotNull(sphincsKeyPair.private)

        // not null public keys
        assertNotNull(rsaKeyPair.public)
        assertNotNull(ecdsaKKeyPair.public)
        assertNotNull(ecdsaRKeyPair.public)
        assertNotNull(eddsaKeyPair.public)
        assertNotNull(sphincsKeyPair.public)

        // fail on unsupported algorithm
        try {
            Crypto.generateKeyPair("WRONG_ALG")
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    // full process tests

    @Test
    fun `RSA full process keygen-sign-verify`() {
        val keyPair = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privKey, pubKey) = keyPair
        // test for some data
        val signedData = Crypto.doSign(privKey, testBytes)
        val verification = Crypto.doVerify(pubKey, signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            Crypto.doSign(privKey, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            Crypto.doVerify(pubKey, testBytes, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            Crypto.doVerify(pubKey, EMPTY_BYTE_ARRAY, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = Crypto.doSign(privKey, ByteArray(100))
        val verificationZeros = Crypto.doVerify(pubKey, signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = Crypto.doSign(privKey, MBbyte)
        val verificationBig = Crypto.doVerify(pubKey, signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            Crypto.doVerify(pubKey, signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `ECDSA secp256k1 full process keygen-sign-verify`() {
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privKey, pubKey) = keyPair
        // test for some data
        val signedData = Crypto.doSign(privKey, testBytes)
        val verification = Crypto.doVerify(pubKey, signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            Crypto.doSign(privKey, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            Crypto.doVerify(pubKey, testBytes, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            Crypto.doVerify(pubKey, EMPTY_BYTE_ARRAY, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = Crypto.doSign(privKey, ByteArray(100))
        val verificationZeros = Crypto.doVerify(pubKey, signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = Crypto.doSign(privKey, MBbyte)
        val verificationBig = Crypto.doVerify(pubKey, signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            Crypto.doVerify(pubKey, signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `ECDSA secp256r1 full process keygen-sign-verify`() {
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privKey, pubKey) = keyPair
        // test for some data
        val signedData = Crypto.doSign(privKey, testBytes)
        val verification = Crypto.doVerify(pubKey, signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            Crypto.doSign(privKey, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            Crypto.doVerify(pubKey, testBytes, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            Crypto.doVerify(pubKey, EMPTY_BYTE_ARRAY, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = Crypto.doSign(privKey, ByteArray(100))
        val verificationZeros = Crypto.doVerify(pubKey, signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = Crypto.doSign(privKey, MBbyte)
        val verificationBig = Crypto.doVerify(pubKey, signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            Crypto.doVerify(pubKey, signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `EDDSA ed25519 full process keygen-sign-verify`() {
        val keyPair = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privKey, pubKey) = keyPair
        // test for some data
        val signedData = Crypto.doSign(privKey, testBytes)
        val verification = Crypto.doVerify(pubKey, signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            Crypto.doSign(privKey, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            Crypto.doVerify(pubKey, testBytes, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            Crypto.doVerify(pubKey, EMPTY_BYTE_ARRAY, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = Crypto.doSign(privKey, ByteArray(100))
        val verificationZeros = Crypto.doVerify(pubKey, signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = Crypto.doSign(privKey, MBbyte)
        val verificationBig = Crypto.doVerify(pubKey, signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            Crypto.doVerify(pubKey, signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `SPHINCS-256 full process keygen-sign-verify`() {
        val keyPair = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)
        val (privKey, pubKey) = keyPair
        // test for some data
        val signedData = Crypto.doSign(privKey, testBytes)
        val verification = Crypto.doVerify(pubKey, signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            Crypto.doSign(privKey, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            Crypto.doVerify(pubKey, testBytes, EMPTY_BYTE_ARRAY)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            Crypto.doVerify(pubKey, EMPTY_BYTE_ARRAY, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = Crypto.doSign(privKey, ByteArray(100))
        val verificationZeros = Crypto.doVerify(pubKey, signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = Crypto.doSign(privKey, MBbyte)
        val verificationBig = Crypto.doVerify(pubKey, signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            Crypto.doVerify(pubKey, signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    // test list of supported algorithms
    @Test
    fun `Check supported algorithms`() {
        val algList: List<String> = Crypto.supportedSignatureSchemes().map { it.schemeCodeName }
        val expectedAlgSet = setOf("RSA_SHA256", "ECDSA_SECP256K1_SHA256", "ECDSA_SECP256R1_SHA256", "EDDSA_ED25519_SHA512", "SPHINCS-256_SHA512", "COMPOSITE")
        assertTrue { Sets.symmetricDifference(expectedAlgSet, algList.toSet()).isEmpty(); }
    }

    // Unfortunately, there isn't a standard way to encode/decode keys, so we need to test per case
    @Test
    fun `RSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privKey, pubKey) = keyPair

        // Encode and decode private key.
        val privKey2 = Crypto.decodePrivateKey(privKey.encoded)
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = Crypto.decodePublicKey(pubKey.encoded)
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `ECDSA secp256k1 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privKey, pubKey) = keyPair

        // Encode and decode private key.
        val privKey2 = Crypto.decodePrivateKey(privKey.encoded)
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = Crypto.decodePublicKey(pubKey.encoded)
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `ECDSA secp256r1 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privKey, pubKey) = keyPair

        // Encode and decode private key.
        val privKey2 = Crypto.decodePrivateKey(privKey.encoded)
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = Crypto.decodePublicKey(pubKey.encoded)
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `EdDSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privKey, pubKey) = keyPair

        // Encode and decode private key.
        val privKey2 = Crypto.decodePrivateKey(privKey.encoded)
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = Crypto.decodePublicKey(pubKey.encoded)
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `SPHINCS-256 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)
        val privKey: BCSphincs256PrivateKey = keyPair.private as BCSphincs256PrivateKey
        val pubKey: BCSphincs256PublicKey = keyPair.public as BCSphincs256PublicKey

        //1st method for encoding/decoding
        val privKey2 = Crypto.decodePrivateKey(privKey.encoded)
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = Crypto.decodePublicKey(pubKey.encoded)
        assertEquals(pubKey2, pubKey)

        //2nd method for encoding/decoding

        // Encode and decode private key.
        val privKeyInfo: PrivateKeyInfo = PrivateKeyInfo.getInstance(privKey.encoded)
        val decodedPrivKey = BCSphincs256PrivateKey(privKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(decodedPrivKey, privKey)

        // Encode and decode public key.
        val pubKeyInfo: SubjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(pubKey.encoded)
        val decodedPubKey = BCSphincs256PublicKey(pubKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(decodedPubKey, pubKey)
    }

    @Test
    fun `RSA scheme finder by key type`() {
        val keyPairRSA = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privRSA, pubRSA) = keyPairRSA
        assertEquals(privRSA.algorithm, "RSA")
        assertEquals(pubRSA.algorithm, "RSA")
    }

    @Test
    fun `ECDSA secp256k1 scheme finder by key type`() {
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privKey, pubKey) = keyPair

        // Encode and decode private key.
        val privKeyDecoded = Crypto.decodePrivateKey(privKey.encoded)
        val pubKeyDecoded = Crypto.decodePublicKey(pubKey.encoded)

        assertEquals(privKeyDecoded.algorithm, "ECDSA")
        assertEquals((privKeyDecoded as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals(pubKeyDecoded.algorithm, "ECDSA")
        assertEquals((pubKeyDecoded as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
    }

    @Test
    fun `ECDSA secp256r1 scheme finder by key type`() {
        val keyPairR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privR1, pubR1) = keyPairR1
        assertEquals(privR1.algorithm, "ECDSA")
        assertEquals((privR1 as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals(pubR1.algorithm, "ECDSA")
        assertEquals((pubR1 as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
    }

    @Test
    fun `EdDSA scheme finder by key type`() {
        val keyPairEd = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privEd, pubEd) = keyPairEd

        assertEquals(privEd.algorithm, "EdDSA")
        assertEquals((privEd as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(pubEd.algorithm, "EdDSA")
        assertEquals((pubEd as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
    }

    @Test
    fun `SPHINCS-256 scheme finder by key type`() {
        val keyPairSP = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)
        val (privSP, pubSP) = keyPairSP
        assertEquals(privSP.algorithm, "SPHINCS-256")
        assertEquals(pubSP.algorithm, "SPHINCS-256")
    }

    @Test
    fun `Automatic EdDSA key-type detection and decoding`() {
        val keyPairEd = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privEd, pubEd) = keyPairEd
        val encodedPrivEd = privEd.encoded
        val encodedPubEd = pubEd.encoded

        val decodedPrivEd = Crypto.decodePrivateKey(encodedPrivEd)
        assertEquals(decodedPrivEd.algorithm, "EdDSA")
        assertEquals(decodedPrivEd, privEd)

        val decodedPubEd = Crypto.decodePublicKey(encodedPubEd)
        assertEquals(decodedPubEd.algorithm, "EdDSA")
        assertEquals(decodedPubEd, pubEd)
    }

    @Test
    fun `Automatic ECDSA secp256k1 key-type detection and decoding`() {
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privK1, pubK1) = keyPairK1
        val encodedPrivK1 = privK1.encoded
        val encodedPubK1 = pubK1.encoded

        val decodedPrivK1 = Crypto.decodePrivateKey(encodedPrivK1)
        assertEquals(decodedPrivK1.algorithm, "ECDSA")
        assertEquals(decodedPrivK1, privK1)

        val decodedPubK1 = Crypto.decodePublicKey(encodedPubK1)
        assertEquals(decodedPubK1.algorithm, "ECDSA")
        assertEquals(decodedPubK1, pubK1)
    }

    @Test
    fun `Automatic ECDSA secp256r1 key-type detection and decoding`() {
        val keyPairR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privR1, pubR1) = keyPairR1
        val encodedPrivR1 = privR1.encoded
        val encodedPubR1 = pubR1.encoded

        val decodedPrivR1 = Crypto.decodePrivateKey(encodedPrivR1)
        assertEquals(decodedPrivR1.algorithm, "ECDSA")
        assertEquals(decodedPrivR1, privR1)

        val decodedPubR1 = Crypto.decodePublicKey(encodedPubR1)
        assertEquals(decodedPubR1.algorithm, "ECDSA")
        assertEquals(decodedPubR1, pubR1)
    }

    @Test
    fun `Automatic RSA key-type detection and decoding`() {
        val keyPairRSA = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privRSA, pubRSA) = keyPairRSA
        val encodedPrivRSA = privRSA.encoded
        val encodedPubRSA = pubRSA.encoded

        val decodedPrivRSA = Crypto.decodePrivateKey(encodedPrivRSA)
        assertEquals(decodedPrivRSA.algorithm, "RSA")
        assertEquals(decodedPrivRSA, privRSA)

        val decodedPubRSA = Crypto.decodePublicKey(encodedPubRSA)
        assertEquals(decodedPubRSA.algorithm, "RSA")
        assertEquals(decodedPubRSA, pubRSA)
    }

    @Test
    fun `Automatic SPHINCS-256 key-type detection and decoding`() {
        val keyPairSP = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)
        val (privSP, pubSP) = keyPairSP
        val encodedPrivSP = privSP.encoded
        val encodedPubSP = pubSP.encoded

        val decodedPrivSP = Crypto.decodePrivateKey(encodedPrivSP)
        assertEquals(decodedPrivSP.algorithm, "SPHINCS-256")
        assertEquals(decodedPrivSP, privSP)

        val decodedPubSP = Crypto.decodePublicKey(encodedPubSP)
        assertEquals(decodedPubSP.algorithm, "SPHINCS-256")
        assertEquals(decodedPubSP, pubSP)
    }

    @Test
    fun `Failure test between K1 and R1 keys`() {
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val privK1 = keyPairK1.private
        val encodedPrivK1 = privK1.encoded
        val decodedPrivK1 = Crypto.decodePrivateKey(encodedPrivK1)

        val keyPairR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val privR1 = keyPairR1.private
        val encodedPrivR1 = privR1.encoded
        val decodedPrivR1 = Crypto.decodePrivateKey(encodedPrivR1)

        assertNotEquals(decodedPrivK1, decodedPrivR1)
    }

    @Test
    fun `Decoding Failure on randomdata as key`() {
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val privK1 = keyPairK1.private
        val encodedPrivK1 = privK1.encoded

        // Test on random encoded bytes.
        val fakeEncodedKey = ByteArray(encodedPrivK1.size)
        val r = Random()
        r.nextBytes(fakeEncodedKey)

        // fail on fake key.
        try {
            Crypto.decodePrivateKey(fakeEncodedKey)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `Decoding Failure on malformed keys`() {
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val privK1 = keyPairK1.private
        val encodedPrivK1 = privK1.encoded

        // fail on malformed key.
        for (i in 0 until encodedPrivK1.size) {
            val b = encodedPrivK1[i]
            encodedPrivK1[i] = b.inc()
            try {
                Crypto.decodePrivateKey(encodedPrivK1)
                fail()
            } catch (e: Exception) {
                // expected
            }
            encodedPrivK1[i] = b.dec()
        }
    }

    @Test
    fun `Check ECDSA public key on curve`() {
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val pubK1 = keyPairK1.public as BCECPublicKey
        assertTrue(Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256K1_SHA256, pubK1))
        // use R1 curve for check.
        assertFalse(Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256R1_SHA256, pubK1))
        // use ed25519 curve for check.
        assertFalse(Crypto.publicKeyOnCurve(Crypto.EDDSA_ED25519_SHA512, pubK1))
    }

    @Test
    fun `Check EdDSA public key on curve`() {
        val keyPairEdDSA = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val pubEdDSA = keyPairEdDSA.public
        assertTrue(Crypto.publicKeyOnCurve(Crypto.EDDSA_ED25519_SHA512, pubEdDSA))
        // Use R1 curve for check.
        assertFalse(Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256R1_SHA256, pubEdDSA))
        // Check for point at infinity.
        val pubKeySpec = EdDSAPublicKeySpec((Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec).curve.getZero(GroupElement.Representation.P3), Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec)
        assertFalse(Crypto.publicKeyOnCurve(Crypto.EDDSA_ED25519_SHA512, EdDSAPublicKey(pubKeySpec)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Unsupported EC public key type on curve`() {
        val keyGen = KeyPairGenerator.getInstance("EC") // sun.security.ec.ECPublicKeyImpl
        keyGen.initialize(256, newSecureRandom())
        val pairSun = keyGen.generateKeyPair()
        val pubSun = pairSun.public
        // Should fail as pubSun is not a BCECPublicKey.
        Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256R1_SHA256, pubSun)
    }

    @Test
    fun `ECDSA secp256R1 deterministic key generation`() {
        val (priv, pub) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (dpriv, dpub) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())

        // Check scheme.
        assertEquals(priv.algorithm, dpriv.algorithm)
        assertEquals(pub.algorithm, dpub.algorithm)
        assertTrue(dpriv is BCECPrivateKey)
        assertTrue(dpub is BCECPublicKey)
        assertEquals((dpriv as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals((dpub as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals(Crypto.findSignatureScheme(dpriv), Crypto.ECDSA_SECP256R1_SHA256)
        assertEquals(Crypto.findSignatureScheme(dpub), Crypto.ECDSA_SECP256R1_SHA256)

        // Validate public key.
        assertTrue(Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256R1_SHA256, dpub))

        // Try to sign/verify.
        val signedData = Crypto.doSign(dpriv, testBytes)
        val verification = Crypto.doVerify(dpub, signedData, testBytes)
        assertTrue(verification)

        // Check it is a new keyPair.
        assertNotEquals(priv, dpriv)
        assertNotEquals(pub, dpub)

        // A new keyPair is always generated per different seed.
        val (dpriv2, dpub2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertNotEquals(dpriv, dpriv2)
        assertNotEquals(dpub, dpub2)

        // Check if the same input always produces the same output (i.e. deterministically generated).
        val (dpriv_1, dpub_1) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())
        assertEquals(dpriv, dpriv_1)
        assertEquals(dpub, dpub_1)
        val (dpriv_2, dpub_2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertEquals(dpriv2, dpriv_2)
        assertEquals(dpub2, dpub_2)
    }

    @Test
    fun `ECDSA secp256K1 deterministic key generation`() {
        val (priv, pub) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (dpriv, dpub) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())

        // Check scheme.
        assertEquals(priv.algorithm, dpriv.algorithm)
        assertEquals(pub.algorithm, dpub.algorithm)
        assertTrue(dpriv is BCECPrivateKey)
        assertTrue(dpub is BCECPublicKey)
        assertEquals((dpriv as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals((dpub as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals(Crypto.findSignatureScheme(dpriv), Crypto.ECDSA_SECP256K1_SHA256)
        assertEquals(Crypto.findSignatureScheme(dpub), Crypto.ECDSA_SECP256K1_SHA256)

        // Validate public key.
        assertTrue(Crypto.publicKeyOnCurve(Crypto.ECDSA_SECP256K1_SHA256, dpub))

        // Try to sign/verify.
        val signedData = Crypto.doSign(dpriv, testBytes)
        val verification = Crypto.doVerify(dpub, signedData, testBytes)
        assertTrue(verification)

        // check it is a new keyPair.
        assertNotEquals(priv, dpriv)
        assertNotEquals(pub, dpub)

        // A new keyPair is always generated per different seed.
        val (dpriv2, dpub2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertNotEquals(dpriv, dpriv2)
        assertNotEquals(dpub, dpub2)

        // Check if the same input always produces the same output (i.e. deterministically generated).
        val (dpriv_1, dpub_1) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())
        assertEquals(dpriv, dpriv_1)
        assertEquals(dpub, dpub_1)
        val (dpriv_2, dpub_2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertEquals(dpriv2, dpriv_2)
        assertEquals(dpub2, dpub_2)
    }

    @Test
    fun `EdDSA ed25519 deterministic key generation`() {
        val (priv, pub) = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (dpriv, dpub) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())

        // Check scheme.
        assertEquals(priv.algorithm, dpriv.algorithm)
        assertEquals(pub.algorithm, dpub.algorithm)
        assertTrue(dpriv is EdDSAPrivateKey)
        assertTrue(dpub is EdDSAPublicKey)
        assertEquals((dpriv as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals((dpub as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(Crypto.findSignatureScheme(dpriv), Crypto.EDDSA_ED25519_SHA512)
        assertEquals(Crypto.findSignatureScheme(dpub), Crypto.EDDSA_ED25519_SHA512)

        // Validate public key.
        assertTrue(Crypto.publicKeyOnCurve(Crypto.EDDSA_ED25519_SHA512, dpub))

        // Try to sign/verify.
        val signedData = Crypto.doSign(dpriv, testBytes)
        val verification = Crypto.doVerify(dpub, signedData, testBytes)
        assertTrue(verification)

        // Check it is a new keyPair.
        assertNotEquals(priv, dpriv)
        assertNotEquals(pub, dpub)

        // A new keyPair is always generated per different seed.
        val (dpriv2, dpub2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertNotEquals(dpriv, dpriv2)
        assertNotEquals(dpub, dpub2)

        // Check if the same input always produces the same output (i.e. deterministically generated).
        val (dpriv_1, dpub_1) = Crypto.deriveKeyPair(priv, "seed-1".toByteArray())
        assertEquals(dpriv, dpriv_1)
        assertEquals(dpub, dpub_1)
        val (dpriv_2, dpub_2) = Crypto.deriveKeyPair(priv, "seed-2".toByteArray())
        assertEquals(dpriv2, dpriv_2)
        assertEquals(dpub2, dpub_2)
    }

    @Test
    fun `EdDSA ed25519 keyPair from entropy`() {
        val keyPairPositive = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("10"))
        assertEquals("DLBL3iHCp9uRReWhhCGfCsrxZZpfAm9h9GLbfN8ijqXTq", keyPairPositive.public.toStringShort())

        val keyPairNegative = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("-10"))
        assertEquals("DLC5HXnYsJAFqmM9hgPj5G8whQ4TpyE9WMBssqCayLBwA2", keyPairNegative.public.toStringShort())

        val keyPairZero = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("0"))
        assertEquals("DL4UVhGh4tqu1G86UVoGNaDDNCMsBtNHzE6BSZuNNJN7W2", keyPairZero.public.toStringShort())

        val keyPairOne = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("1"))
        assertEquals("DL8EZUdHixovcCynKMQzrMWBnXQAcbVDHi6ArPphqwJVzq", keyPairOne.public.toStringShort())

        val keyPairBiggerThan256bits = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("2").pow(258).minus(BigInteger.TEN))
        assertEquals("DLB9K1UiBrWonn481z6NzkqoWHjMBXpfDeaet3wiwRNWSU", keyPairBiggerThan256bits.public.toStringShort())
        // The underlying implementation uses the first 256 bytes of the entropy. Thus, 2^258-10 and 2^258-50 and 2^514-10 have the same impact.
        val keyPairBiggerThan256bitsV2 = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("2").pow(258).minus(BigInteger("50")))
        assertEquals("DLB9K1UiBrWonn481z6NzkqoWHjMBXpfDeaet3wiwRNWSU", keyPairBiggerThan256bitsV2.public.toStringShort())
        val keyPairBiggerThan512bits = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("2").pow(514).minus(BigInteger.TEN))
        assertEquals("DLB9K1UiBrWonn481z6NzkqoWHjMBXpfDeaet3wiwRNWSU", keyPairBiggerThan512bits.public.toStringShort())

        // Try another big number.
        val keyPairBiggerThan258bits = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger("2").pow(259).plus(BigInteger.ONE))
        assertEquals("DL5tEFVMXMGrzwjfCAW34JjkhsRkPfFyJ38iEnmpB6L2Z9", keyPairBiggerThan258bits.public.toStringShort())
    }

    @Test
    fun `ECDSA R1 keyPair from entropy`() {
        val keyPairPositive = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("10"))
        assertEquals("DLHDcxuSt9J3cbjd2Dsx4rAgYYA7BAP7A8VLrFiq1tH9yy", keyPairPositive.public.toStringShort())
        // The underlying implementation uses the hash of entropy if it is out of range 2 < entropy < N, where N the order of the group.
        val keyPairNegative = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("-10"))
        assertEquals("DLBASmjiMZuu1g3EtdHJxfSueXE8PRoUWbkdU61Qcnpamt", keyPairNegative.public.toStringShort())

        val keyPairZero = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("0"))
        assertEquals("DLH2FEHEnsT3MpCJt2gfyNjpqRqcBxeupK4YRPXvDsVEkb", keyPairZero.public.toStringShort())
        // BigIntenger.Zero is out or range, so 1 and hash(1.toByteArray) would have the same impact.
        val zeroHashed = BigInteger(1, BigInteger("0").toByteArray().sha256().bytes)
        // Check oneHashed < N (order of the group), otherwise we would need an extra hash.
        assertEquals(-1, zeroHashed.compareTo((Crypto.ECDSA_SECP256R1_SHA256.algSpec as ECNamedCurveParameterSpec).n))
        val keyPairZeroHashed = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, zeroHashed)
        assertEquals("DLH2FEHEnsT3MpCJt2gfyNjpqRqcBxeupK4YRPXvDsVEkb", keyPairZeroHashed.public.toStringShort())

        val keyPairOne = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("1"))
        assertEquals("DLHrtKwjv6onq9HcrQDJPs8Cgtai5mZU5ZU6sb1ivJjx3z", keyPairOne.public.toStringShort())
        // BigIntenger.ONE is out or range, so 1 and hash(1.toByteArray) would have the same impact.
        val oneHashed = BigInteger(1, BigInteger("1").toByteArray().sha256().bytes)
        // Check oneHashed < N (order of the group), otherwise we would need an extra hash.
        assertEquals(-1, oneHashed.compareTo((Crypto.ECDSA_SECP256R1_SHA256.algSpec as ECNamedCurveParameterSpec).n))
        val keyPairOneHashed = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, oneHashed)
        assertEquals("DLHrtKwjv6onq9HcrQDJPs8Cgtai5mZU5ZU6sb1ivJjx3z", keyPairOneHashed.public.toStringShort())

        // 2 is in the range.
        val keyPairTwo = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("2"))
        assertEquals("DLFoz6txJ3vHcKNSM1vFxHJUoEQ69PorBwW64dHsAnEoZB", keyPairTwo.public.toStringShort())

        // Try big numbers that are out of range.
        val keyPairBiggerThan256bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("2").pow(258).minus(BigInteger.TEN))
        assertEquals("DLBv6fZqaCTbE4L7sgjbt19biXHMgU9CzR5s8g8XBJjZ11", keyPairBiggerThan256bits.public.toStringShort())
        val keyPairBiggerThan256bitsV2 = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("2").pow(258).minus(BigInteger("50")))
        assertEquals("DLANmjhGSVdLyghxcPHrn3KuGatscf6LtvqifUDxw7SGU8", keyPairBiggerThan256bitsV2.public.toStringShort())
        val keyPairBiggerThan512bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("2").pow(514).minus(BigInteger.TEN))
        assertEquals("DL9sKwMExBTD3MnJN6LWGqo496Erkebs9fxZtXLVJUBY9Z", keyPairBiggerThan512bits.public.toStringShort())
        val keyPairBiggerThan258bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger("2").pow(259).plus(BigInteger.ONE))
        assertEquals("DLBwjWwPJSF9E7b1NWaSbEJ4oK8CF7RDGWd648TiBhZoL1", keyPairBiggerThan258bits.public.toStringShort())
    }

    @Test
    fun `ECDSA K1 keyPair from entropy`() {
        val keyPairPositive = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("10"))
        assertEquals("DL6pYKUgH17az8MLdonvvUtUPN8TqwpCGcdgLr7vg3skCU", keyPairPositive.public.toStringShort())
        // The underlying implementation uses the hash of entropy if it is out of range 2 <= entropy < N, where N the order of the group.
        val keyPairNegative = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("-10"))
        assertEquals("DLnpXhxece69Nyqgm3pPt3yV7ESQYDJKoYxs1hKgfBAEu", keyPairNegative.public.toStringShort())

        val keyPairZero = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("0"))
        assertEquals("DLBC28e18T6KsYwjTFfUWJfhvHjvYVapyVf6antnqUkbgd", keyPairZero.public.toStringShort())
        // BigIntenger.Zero is out or range, so 1 and hash(1.toByteArray) would have the same impact.
        val zeroHashed = BigInteger(1, BigInteger("0").toByteArray().sha256().bytes)
        // Check oneHashed < N (order of the group), otherwise we would need an extra hash.
        assertEquals(-1, zeroHashed.compareTo((Crypto.ECDSA_SECP256K1_SHA256.algSpec as ECNamedCurveParameterSpec).n))
        val keyPairZeroHashed = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, zeroHashed)
        assertEquals("DLBC28e18T6KsYwjTFfUWJfhvHjvYVapyVf6antnqUkbgd", keyPairZeroHashed.public.toStringShort())

        val keyPairOne = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("1"))
        assertEquals("DLBimRXdEQhJUTpL6f9ri9woNdsze6mwkRrhsML13Eh7ET", keyPairOne.public.toStringShort())
        // BigIntenger.ONE is out or range, so 1 and hash(1.toByteArray) would have the same impact.
        val oneHashed = BigInteger(1, BigInteger("1").toByteArray().sha256().bytes)
        // Check oneHashed < N (order of the group), otherwise we would need an extra hash.
        assertEquals(-1, oneHashed.compareTo((Crypto.ECDSA_SECP256K1_SHA256.algSpec as ECNamedCurveParameterSpec).n))
        val keyPairOneHashed = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, oneHashed)
        assertEquals("DLBimRXdEQhJUTpL6f9ri9woNdsze6mwkRrhsML13Eh7ET", keyPairOneHashed.public.toStringShort())

        // 2 is in the range.
        val keyPairTwo = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("2"))
        assertEquals("DLG32UWaevGw9YY7w1Rf9mmK88biavgpDnJA9bG4GapVPs", keyPairTwo.public.toStringShort())

        // Try big numbers that are out of range.
        val keyPairBiggerThan256bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("2").pow(258).minus(BigInteger.TEN))
        assertEquals("DLGHsdv2xeAuM7n3sBc6mFfiphXe6VSf3YxqvviKDU6Vbd", keyPairBiggerThan256bits.public.toStringShort())
        val keyPairBiggerThan256bitsV2 = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("2").pow(258).minus(BigInteger("50")))
        assertEquals("DL9yJfiNGqteRrKPjGUkRQkeqzuQ4kwcYQWMCi5YKuUHrk", keyPairBiggerThan256bitsV2.public.toStringShort())
        val keyPairBiggerThan512bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("2").pow(514).minus(BigInteger.TEN))
        assertEquals("DL3Wr5EQGrMTaKBy5XMvG8rvSfKX1AYZLCRU8kixGbxt1E", keyPairBiggerThan512bits.public.toStringShort())
        val keyPairBiggerThan258bits = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger("2").pow(259).plus(BigInteger.ONE))
        assertEquals("DL7NbssqvuuJ4cqFkkaVYu9j1MsVswESGgCfbqBS9ULwuM", keyPairBiggerThan258bits.public.toStringShort())
    }
}
