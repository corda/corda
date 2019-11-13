package net.corda.core.internal;

import net.corda.core.crypto.Crypto;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.junit.Test;
import sun.security.util.BitArray;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509Key;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * JDK11 upgrade: rewritten in Java to gain access to private internal JDK classes via module directives (not available to Kotlin compiler):
 * import sun.security.util.BitArray;
 * import sun.security.util.ObjectIdentifier;
 * import sun.security.x509.AlgorithmId;
 * import sun.security.x509.X509Key;
 */
public class X509EdDSAEngineTest {

    private static long SEED = 20170920L;
    private static int TEST_DATA_SIZE = 2000;

    // offset into an EdDSA header indicating where the key header and actual key start
    // in the underlying byte array
    private static int keyHeaderStart = 9;
    private static int keyStart = 12;

    private X509Key toX509Key(EdDSAPublicKey publicKey) throws IOException, InvalidKeyException {
        byte[] internals = publicKey.getEncoded();

        // key size in the header includes the count unused bits at the end of the key
        // [keyHeaderStart + 2] but NOT the key header ID [keyHeaderStart] so the
        // actual length of the key blob is size - 1
        int keySize = (internals[keyHeaderStart + 1]) - 1;

        byte[] key = new byte[keySize];
        System.arraycopy(internals, keyStart, key, 0, keySize);

        // 1.3.101.102 is the EdDSA OID
        return new TestX509Key(new AlgorithmId(new ObjectIdentifier("1.3.101.112")), new BitArray(keySize * 8, key));
    }

    class TestX509Key extends X509Key {
        TestX509Key(AlgorithmId algorithmId, BitArray key) throws InvalidKeyException {
            this.algid = algorithmId;
            this.setKey(key);
            this.encode();
        }
    }

    /**
     * Put the X509EdDSA engine through basic tests to verify that the functions are hooked up correctly.
     */
    @Test
    public void SignAndVerify() throws InvalidKeyException, SignatureException {
        X509EdDSAEngine engine = new X509EdDSAEngine();
        KeyPair keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED));
        EdDSAPublicKey publicKey = (EdDSAPublicKey) keyPair.getPublic();
        byte[] randomBytes = new byte[TEST_DATA_SIZE];
        new Random(SEED).nextBytes(randomBytes);
        engine.initSign(keyPair.getPrivate());
        engine.update(randomBytes[0]);
        engine.update(randomBytes, 1, randomBytes.length - 1);

        // Now verify the signature
        byte[] signature = engine.sign();

        engine.initVerify(publicKey);
        engine.update(randomBytes);
        assertTrue(engine.verify(signature));
    }

    /**
     * Verify that signing with an X509Key wrapped EdDSA key works.
     */
    @Test
    public void SignAndVerifyWithX509Key() throws InvalidKeyException, SignatureException, IOException {
        X509EdDSAEngine engine = new X509EdDSAEngine();
        KeyPair keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED + 1));
        X509Key publicKey = toX509Key((EdDSAPublicKey) keyPair.getPublic());
        byte[] randomBytes = new byte[TEST_DATA_SIZE];
        new Random(SEED + 1).nextBytes(randomBytes);
        engine.initSign(keyPair.getPrivate());
        engine.update(randomBytes[0]);
        engine.update(randomBytes, 1, randomBytes.length - 1);

        // Now verify the signature
        byte[] signature = engine.sign();

        engine.initVerify(publicKey);
        engine.update(randomBytes);
        assertTrue(engine.verify(signature));
    }

    /**
     * Verify that signing with an X509Key wrapped EdDSA key succeeds when using the underlying EdDSAEngine.
     */
    @Test
    public void SignAndVerifyWithX509KeyAndOldEngineFails() throws InvalidKeyException, SignatureException, IOException {
        X509EdDSAEngine engine = new X509EdDSAEngine();
        KeyPair keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(SEED + 1));
        X509Key publicKey = toX509Key((EdDSAPublicKey) keyPair.getPublic());
        byte[] randomBytes = new byte[TEST_DATA_SIZE];
        new Random(SEED + 1).nextBytes(randomBytes);
        engine.initSign(keyPair.getPrivate());
        engine.update(randomBytes[0]);
        engine.update(randomBytes, 1, randomBytes.length - 1);

        // Now verify the signature
        byte[] signature = engine.sign();
        engine.initVerify(publicKey);
        engine.update(randomBytes);
        engine.verify(signature);
    }

    /** Verify will fail if the input public key cannot be converted to EdDSA public key. */
    @Test(expected = InvalidKeyException.class)
    public void verifyWithNonSupportedKeyTypeFails() throws InvalidKeyException {
        EdDSAEngine engine = new EdDSAEngine();
        KeyPair keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256K1_SHA256, BigInteger.valueOf(SEED));
        engine.initVerify(keyPair.getPublic());
    }
}
