package net.corda.core.crypto;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandomSpi;
import java.util.Random;

/**
 * SHA256PRNG based on default SHA1PRNG implementation (Secure Random SPI SHA1PRNG)
 */
public class SHA256PRNG extends SecureRandomSpi implements Serializable {
    MessageDigest digest;
    byte seed[];
    byte data[];
    int seedpos;
    int datapos;
    private boolean seeded = true; // set to true when we seed this

    public SHA256PRNG() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsae) {
            throw new InternalError("no SHA-256 implementation found");
        }

        seed = new byte[32];
        seedpos = 0;
        data = new byte[64];
        datapos = 32;  // try to force hashing a first block
    }

    public void engineSetSeed(byte[] seed) {
        for (int i = 0; i < seed.length; i++)
            this.seed[seedpos++ % 32] ^= seed[i];
        seedpos %= 32;
    }

    public void engineNextBytes(byte[] bytes) {
        ensureIsSeeded();
        int loc = 0;
        while (loc < bytes.length) {
            int copy = Math.min(bytes.length - loc, 32 - datapos);

            if (copy > 0) {
                System.arraycopy(data, datapos, bytes, loc, copy);
                datapos += copy;
                loc += copy;
            } else {
                // No data ready for copying, so refill our buffer.
                System.arraycopy(seed, 0, data, 32, 32);
                byte[] digestdata = digest.digest(data);
                System.arraycopy(digestdata, 0, data, 0, 32);
                datapos = 0;
            }
        }
    }

    public byte[] engineGenerateSeed(int numBytes) {
        byte tmp[] = new byte[numBytes];

        engineNextBytes(tmp);
        return tmp;
    }

    private void ensureIsSeeded() {
        if (!seeded) {
            new Random(0L).nextBytes(seed);
            System.out.println("Data digestion:" + data);
            byte[] digestdata = digest.digest(data);
            System.arraycopy(digestdata, 0, data, 0, 32);
            seeded = true;
        }
    }

}
