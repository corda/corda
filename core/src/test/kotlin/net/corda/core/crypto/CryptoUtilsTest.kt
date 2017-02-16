package net.corda.core.crypto

import com.google.common.collect.Sets
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.junit.Test
import java.security.Security
import java.util.*
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
    fun `generate key pairs`() {

        // testing supported algorithms
        val rsaKeyPair = Crypto.genKeyPair("RSA")
        val ecdsaKeyPair = Crypto.genKeyPair("ECDSA")
        val eddsaKeyPair = Crypto.genKeyPair("EdDSA")
        val sphincsKeyPair = Crypto.genKeyPair("SPHINCS-256")

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
            val wrongKeyPair = Crypto.genKeyPair("WRONG_ALG")
            fail()
        } catch (e: CryptoException) {
            // expected.
        }
    }

    // full process tests

    @Test
    fun `RSA full process keygen-sign-verify`() {

        val keyPair = Crypto.genKeyPair("RSA")

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
        assertTrue(verificationZeros)
    }

    @Test
    fun `ECDSA full process keygen-sign-verify`() {

        val keyPair = Crypto.genKeyPair("ECDSA")

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
        assertTrue(verificationZeros)
    }

    @Test
    fun `EDDSA full process keygen-sign-verify`() {

        val keyPair = Crypto.genKeyPair("EdDSA")

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
        assertTrue(verificationZeros)
    }

    @Test
    fun `SPHINCS full process keygen-sign-verify`() {

        val keyPair = Crypto.genKeyPair("SPHINCS-256")

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
        assertTrue(verificationZeros)
    }

    // test list of supported algorithms
    @Test
    fun `check supported algorithms`() {
        val algList : List<String> = SignatureAlgorithmManager.listOfSupportedAlgorithms()
        val expectedAlgSet = setOf<String>("RSA","ECDSA", "EdDSA", "SPHINCS-256")
        assertTrue { Sets.symmetricDifference(expectedAlgSet,algList.toSet()).isEmpty(); }
    }

}
