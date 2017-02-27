package net.corda.core.crypto;

import java.security.SecureRandom;

public class DeterministicSecureRandom extends SecureRandom {

    /** SHA256 PRNG implementation. */
    private SHA256PRNG secureRandomSpi;

    private String algorithm;

    // Seed Generator.
    private static volatile DeterministicSecureRandom seedGenerator = null;

    /**
     * Constructs a secure random number generator (RNG) implementing the default random number algorithm SHA256.
     * The DeterministicSecureRandom instance is seeded with the specified seed bytes.
     * This PRNG should be used for deterministic key derivation, thus a constructor without an input seed is not implemented.
     * @param seed the seed.
     */
    public DeterministicSecureRandom(byte seed[]) {
        secureRandomSpi = new SHA256PRNG();
        algorithm = "SHA256PRNG";
        this.secureRandomSpi.engineSetSeed(seed);
    }

    /**
     * Reseeds this random object. The given seed supplements, rather than
     * replaces, the existing seed. Thus, repeated calls are guaranteed
     * never to reduce randomness.
     *
     * @param seed the seed.
     * @see #getSeed
     */
    synchronized public void setSeed(byte[] seed) {
        secureRandomSpi.engineSetSeed(seed);
    }

    /**
     * Reseeds this random object, using the eight bytes contained
     * in the given {@code long seed}. The given seed supplements,
     * rather than replaces, the existing seed. Thus, repeated calls
     * are guaranteed never to reduce randomness.
     * <p>
     * <p>This method is defined for compatibility with
     * {@code java.util.Random}.
     *
     * @param seed the seed.
     * @see #getSeed
     */
    @Override
    public void setSeed(long seed) {
        /*
         * Ignore call from super constructor (as well as any other calls
         * unfortunate enough to be passing 0).  It's critical that we
         * ignore call from superclass constructor, as digest has not
         * yet been initialized at that point.
         */
        if (seed != 0) {
            secureRandomSpi.engineSetSeed(longToByteArray(seed));
        }
    }

    /**
     * Generates a user-specified number of random bytes.
     * <p>
     * <p> If a call to {@code setSeed} had not occurred previously,
     * the first call to this method forces this SecureRandom object
     * to seed itself.  This self-seeding will not occur if
     * {@code setSeed} was previously called.
     *
     * @param bytes the array to be filled in with random bytes.
     */
    @Override
    public void nextBytes(byte[] bytes) {
        secureRandomSpi.engineNextBytes(bytes);
    }

    /**
     * Returns the given number of seed bytes, computed using the seed
     * generation algorithm that this class uses to seed itself.  This
     * call may be used to seed other random number generators.
     * <p>
     * <p>This method is only included for backwards compatibility.
     * The caller is encouraged to use one of the alternative
     * {@code getInstance} methods of SecureRandom to obtain a SecureRandom object, and
     * then call the {@code generateSeed} method to obtain seed bytes
     * from that object.
     *
     * @param numBytes the number of seed bytes to generate.
     * @return the seed bytes.
     * @see #setSeed
     */
    public static byte[] getSeed(int numBytes) {
        if (seedGenerator == null) {
            seedGenerator = new DeterministicSecureRandom(new SHA256PRNG().engineGenerateSeed(numBytes));
        }
        return seedGenerator.generateSeed(numBytes);
    }

    /**
     * Returns the given number of seed bytes, computed using the seed
     * generation algorithm that this class uses to seed itself.  This
     * call may be used to seed other random number generators.
     *
     * @param numBytes the number of seed bytes to generate.
     * @return the seed bytes.
     */
    public byte[] generateSeed(int numBytes) {
        return secureRandomSpi.engineGenerateSeed(numBytes);
    }

    /**
     * Helper function to convert a long into a byte array (least significant
     * byte first).
     */
    private static byte[] longToByteArray(long l) {
        byte[] retVal = new byte[8];

        for (int i = 0; i < 8; i++) {
            retVal[i] = (byte) l;
            l >>= 8;
        }

        return retVal;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    // Declare serialVersionUID to be compatible with JDK1.1
    static final long serialVersionUID = 4940670115562189L;
}
