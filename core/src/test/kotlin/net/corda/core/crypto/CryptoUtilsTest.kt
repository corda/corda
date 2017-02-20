package net.corda.core.crypto

import com.google.common.collect.Sets

import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.junit.Test
import java.security.KeyFactory
import java.security.Security
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import java.security.spec.PKCS8EncodedKeySpec
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import java.security.PublicKey


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
    fun `generate key pairs`() {

        // testing supported algorithms
        val rsaKeyPair = Crypto.generateKeyPair("RSA")
        val ecdsaKeyPair = Crypto.generateKeyPair("ECDSA")
        val eddsaKeyPair = Crypto.generateKeyPair("EdDSA")
        val sphincsKeyPair = Crypto.generateKeyPair("SPHINCS-256")

        // not null private keys
        assertNotNull(rsaKeyPair.private);
        assertNotNull(ecdsaKeyPair.private);
        assertNotNull(eddsaKeyPair.private);
        assertNotNull(sphincsKeyPair.private);

        // not null public keys
        assertNotNull(rsaKeyPair.public);
        assertNotNull(ecdsaKeyPair.public);
        assertNotNull(eddsaKeyPair.public);
        assertNotNull(sphincsKeyPair.public);

        // fail on unsupported algorithm
        try {
            val wrongKeyPair = Crypto.generateKeyPair("WRONG_ALG")
            fail()
        } catch (e: CryptoException) {
            // expected.
        }
    }

    // full process tests

    @Test
    fun `RSA full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("RSA")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data
        val signedDataEmpty = keyPair.sign(ByteArray(0))
        val verificationEmpty = keyPair.verify(signedDataEmpty, ByteArray(0))
        assertTrue(verificationEmpty)

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        val bytes = Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)
    }

    @Test
    fun `ECDSA full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("ECDSA")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data
        val signedDataEmpty = keyPair.sign(ByteArray(0))
        val verificationEmpty = keyPair.verify(signedDataEmpty, ByteArray(0))
        assertTrue(verificationEmpty)

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        val bytes = Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)
    }

    @Test
    fun `EDDSA full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("EdDSA")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data
        val signedDataEmpty = keyPair.sign(ByteArray(0))
        val verificationEmpty = keyPair.verify(signedDataEmpty, ByteArray(0))
        assertTrue(verificationEmpty)

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        val bytes = Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)
    }

    @Test
    fun `SPHINCS full process keygen-sign-verify`() {

        val keyPair = Crypto.generateKeyPair("SPHINCS-256")

        // test for some data
        val signedData = keyPair.sign(testBytes)
        val verification = keyPair.verify(signedData, testBytes)
        assertTrue(verification)

        // test for empty data
        val signedDataEmpty = keyPair.sign(ByteArray(0))
        val verificationEmpty = keyPair.verify(signedDataEmpty, ByteArray(0))
        assertTrue(verificationEmpty)

        // test for zero bytes data
        val signedDataZeros = keyPair.sign(ByteArray(100))
        val verificationZeros = keyPair.verify(signedDataZeros, ByteArray(100))
        assertTrue(verificationZeros)

        // test for 1MB of data (I successfully tested it locally for 1GB as well)
        val MBbyte = ByteArray(1000000) // 1.000.000
        val bytes = Random().nextBytes(MBbyte)
        val signedDataBig = keyPair.sign(MBbyte)
        val verificationBig = keyPair.verify(signedDataBig, MBbyte)
        assertTrue(verificationBig)
    }

    // test list of supported algorithms
    @Test
    fun `check supported algorithms`() {
        val algList : List<String> = SignatureAlgorithmManager.listSupportedAlgorithms()
        val expectedAlgSet = setOf<String>("RSA","ECDSA", "EdDSA", "SPHINCS-256")
        assertTrue { Sets.symmetricDifference(expectedAlgSet,algList.toSet()).isEmpty(); }
    }

    // Unfortunately, there isn't a standard way to encode/decode keys, so we need to test per case

    @Test
    fun `Sphincs encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("SPHINCS-256")
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
        val privKeyInfo : PrivateKeyInfo = PrivateKeyInfo.getInstance(privKey.encoded)
        val decodedPrivKey = BCSphincs256PrivateKey(privKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(decodedPrivKey, privKey)

        // Encode and decode public key.
        val pubKeyInfo : SubjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(pubKey.encoded)
        val extractedPubKey = BCSphincs256PublicKey(pubKeyInfo)
        // Check that decoded private key is equal to the initial one.
        assertEquals(extractedPubKey, pubKey)
    }

    @Test
    fun `RSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("RSA")
        val privKey = keyPair.private
        val pubKey = keyPair.public


        val keyFactory = KeyFactory.getInstance("RSA", "BC")

        // Encode and decode private key.
        val privKey2 = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKey.encoded))
        assertEquals(privKey2, privKey)

        // Encode and decode public key.
        val pubKey2 = keyFactory.generatePublic(X509EncodedKeySpec(pubKey.encoded))
        assertEquals(pubKey2, pubKey)
    }

    @Test
    fun `ECDSA encode decode keys - required for serialization`() {
        // Generate key pair.
        val keyPair = Crypto.generateKeyPair("ECDSA")
        val privKey: PrivateKey = keyPair.private
        val pubKey: PublicKey = keyPair.public

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
        val keyPair = Crypto.generateKeyPair("EdDSA")
        val privKey: EdDSAPrivateKey = keyPair.private as EdDSAPrivateKey
        val pubKey: EdDSAPublicKey = keyPair.public as EdDSAPublicKey

        // Encode and decode private key.
        val seed = privKey.seed
        val decodedPrivKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, ed25519Curve))
        // Check that decoded private key is equal to the initial one.
        assertEquals (decodedPrivKey, privKey)

        // Encode and decode public key.
        val Abyte = pubKey.abyte
        val extractedPubKey = EdDSAPublicKey(EdDSAPublicKeySpec(Abyte, ed25519Curve))
        // Check that decoded private key is equal to the initial one.
        assertEquals (extractedPubKey, pubKey)
    }

}
