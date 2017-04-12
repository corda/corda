package net.corda.core.crypto

import com.google.common.collect.Sets
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.KeyFactory
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Run tests for cryptographic algorithms
 */
class CryptoUtilsTest {

    init {
        Security.addProvider(BouncyCastleProvider())
        Security.addProvider(BouncyCastlePQCProvider())
    }

    val testString = "Hello World"
    val testBytes = testString.toByteArray()

    // key generation test
    @Test
    fun `Generate key pairs`() {
        // testing supported algorithms
        val rsaKeyPair = Crypto.generateKeyPair("RSA_SHA256")
        val ecdsaKKeyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val ecdsaRKeyPair = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")
        val eddsaKeyPair = Crypto.generateKeyPair("EDDSA_ED25519_SHA512")
        val sphincsKeyPair = Crypto.generateKeyPair("SPHINCS-256_SHA512")

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

        val keyPair = Crypto.generateKeyPair("RSA_SHA256")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            keyPair.sign(ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            keyPair.verify(testBytes, ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            keyPair.verify(ByteArray(0), testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        for (i in 0..signedData.size - 1) {
            val b = signedData[i]
            signedData[i] = b.inc()
            try {
                keyPair.verify(signedData, testBytes)
                fail()
            } catch (e: Exception) {
                // expected
            }
            signedData[i] = b.dec()
        }
    }

    @Test
    fun `ECDSA secp256k1 full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            keyPair.sign(ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            keyPair.verify(testBytes, ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            keyPair.verify(ByteArray(0), testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            keyPair.verify(signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `ECDSA secp256r1 full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            keyPair.sign(ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            keyPair.verify(testBytes, ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            keyPair.verify(ByteArray(0), testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            keyPair.verify(signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `EDDSA ed25519 full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("EDDSA_ED25519_SHA512")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            keyPair.sign(ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            keyPair.verify(testBytes, ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            keyPair.verify(ByteArray(0), testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            keyPair.verify(signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `SPHINCS-256 full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("SPHINCS-256_SHA512")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data signing
        try {
            keyPair.sign(ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty source data when verifying
        try {
            keyPair.verify(testBytes, ByteArray(0))
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for empty signed data when verifying
        try {
            keyPair.verify(ByteArray(0), testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)

        // test on malformed signatures (even if they change for 1 bit)
        signedData[0] = signedData[0].inc()
        try {
            keyPair.verify(signedData, testBytes)
            fail()
        } catch (e: Exception) {
            // expected
        }
    }

    // test list of supported algorithms
    @Test
    fun `Check supported algorithms`() {
        val algList: List<String> = Crypto.listSupportedSignatureSchemes()
        val expectedAlgSet = setOf("RSA_SHA256", "ECDSA_SECP256K1_SHA256", "ECDSA_SECP256R1_SHA256", "EDDSA_ED25519_SHA512", "SPHINCS-256_SHA512")
        assertTrue { Sets.symmetricDifference(expectedAlgSet, algList.toSet()).isEmpty(); }
    }

    // Unfortunately, there isn't a standard way to encode/decode keys, so we need to test per case
    @Test
    fun `RSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("RSA_SHA256")
        val (privKey, pubKey) = keyPair

        val keyFactory = KeyFactory.getInstance("RSA", "BC")

        // Encode and decode private key.
        val privKey2 = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = keyFactory.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `ECDSA secp256k1 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val (privKey, pubKey) = keyPair

        val kf = KeyFactory.getInstance("ECDSA", "BC")

        // Encode and decode private key.
        val privKey2 = kf.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = kf.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `ECDSA secp256r1 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")
        val (privKey, pubKey) = keyPair

        val kf = KeyFactory.getInstance("ECDSA", "BC")

        // Encode and decode private key.
        val privKey2 = kf.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = kf.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `EdDSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("EDDSA_ED25519_SHA512")
        val privKey: EdDSAPrivateKey = keyPair.private as EdDSAPrivateKey
        val pubKey: EdDSAPublicKey = keyPair.public as EdDSAPublicKey

        // Encode and decode private key.
        val privKey2 = EdDSAKeyFactory.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = EdDSAKeyFactory.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `SPHINCS-256 encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("SPHINCS-256_SHA512")
        val privKey: BCSphincs256PrivateKey = keyPair.private as BCSphincs256PrivateKey
        val pubKey: BCSphincs256PublicKey = keyPair.public as BCSphincs256PublicKey

        //1st method for encoding/decoding

        val keyFactory = KeyFactory.getInstance("SPHINCS256", "BCPQC")

        // Encode and decode private key.
        val privKey2 = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = keyFactory.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)

        //2nd method for encoding/decoding

        // Encode and decode private key.
        val privKeyInfo: PrivateKeyInfo = PrivateKeyInfo.getInstance(privKey.encoded)
        val decodedPrivKey = BCSphincs256PrivateKey(privKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(decodedPrivKey, privKey)

        // Encode and decode public key.
        val pubKeyInfo: SubjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(pubKey.encoded)
        val extractedPubKey = BCSphincs256PublicKey(pubKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(extractedPubKey, pubKey)
    }

    @Test
    fun `RSA scheme finder by key type`() {
        val keyPairRSA = Crypto.generateKeyPair("RSA_SHA256")
        val (privRSA, pubRSA) = keyPairRSA
        assertEquals(privRSA.algorithm, "RSA")
        assertEquals(pubRSA.algorithm, "RSA")
    }

    @Test
    fun `ECDSA secp256k1 scheme finder by key type`() {
        val keyPairK1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val (privK1, pubK1) = keyPairK1

        // Encode and decode keys as they would be transferred.
        val kf = KeyFactory.getInstance("ECDSA", "BC")
        val privK1Decoded = kf.generatePrivate(PKCS8EncodedKeySpec(privK1.encoded))
        val pubK1Decoded = kf.generatePublic(X509EncodedKeySpec(pubK1.encoded))

        assertEquals(privK1Decoded.algorithm, "ECDSA")
        assertEquals((privK1Decoded as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals(pubK1Decoded.algorithm, "ECDSA")
        assertEquals((pubK1Decoded as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
    }

    @Test
    fun `ECDSA secp256r1 scheme finder by key type`() {
        val keyPairR1 = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")
        val (privR1, pubR1) = keyPairR1
        assertEquals(privR1.algorithm, "ECDSA")
        assertEquals((privR1 as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals(pubR1.algorithm, "ECDSA")
        assertEquals((pubR1 as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
    }

    @Test
    fun `EdDSA scheme finder by key type`() {
        val keyPairEd = Crypto.generateKeyPair("EDDSA_ED25519_SHA512")
        val (privEd, pubEd) = keyPairEd

        assertEquals(privEd.algorithm, "EdDSA")
        assertEquals((privEd as EdDSAKey).params, EdDSANamedCurveTable.getByName("ed25519-sha-512"))
        assertEquals(pubEd.algorithm, "EdDSA")
        assertEquals((pubEd as EdDSAKey).params, EdDSANamedCurveTable.getByName("ed25519-sha-512"))
    }

    @Test
    fun `SPHINCS-256 scheme finder by key type`() {
        val keyPairSP = Crypto.generateKeyPair("SPHINCS-256_SHA512")
        val (privSP, pubSP) = keyPairSP
        assertEquals(privSP.algorithm, "SPHINCS-256")
        assertEquals(pubSP.algorithm, "SPHINCS-256")
    }

    @Test
    fun `Automatic EdDSA key-type detection and decoding`() {
        val keyPairEd = Crypto.generateKeyPair("EDDSA_ED25519_SHA512")
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
        val keyPairK1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
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
        val keyPairR1 = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")
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
        val keyPairRSA = Crypto.generateKeyPair("RSA_SHA256")
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
        val keyPairSP = Crypto.generateKeyPair("SPHINCS-256_SHA512")
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
        val keyPairK1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val privK1 = keyPairK1.private
        val encodedPrivK1 = privK1.encoded
        val decodedPrivK1 = Crypto.decodePrivateKey(encodedPrivK1)

        val keyPairR1 = Crypto.generateKeyPair("ECDSA_SECP256R1_SHA256")
        val privR1 = keyPairR1.private
        val encodedPrivR1 = privR1.encoded
        val decodedPrivR1 = Crypto.decodePrivateKey(encodedPrivR1)

        assertNotEquals(decodedPrivK1, decodedPrivR1)
    }

    @Test
    fun `Decoding Failure on randomdata as key`() {
        val keyPairK1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
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
        val keyPairK1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val privK1 = keyPairK1.private
        val encodedPrivK1 = privK1.encoded

        // fail on malformed key.
        for (i in 0..encodedPrivK1.size - 1) {
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
}
