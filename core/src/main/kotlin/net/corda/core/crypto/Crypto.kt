package net.corda.core.crypto

import net.corda.core.crypto.SignatureAlgorithmManager.findAlgorithm
import java.security.*

object Crypto {
    /**
     * Generate a securely random [ByteArray] of requested number of bytes. Usually used for seeds, nonces and keys.
     * In this version, the NativePRNGNonBlocking is exclusively used on Linux OS to utilize dev/urandom because in high traffic
     * /dev/random may wait for a certain amount of "noise" to be generated on the host machine before returning a result.
     *
     * On Solaris, Linux, and OS X, if the entropy gathering device in java.security is set to file:/dev/urandom
     * or file:/dev/random, then NativePRNG is preferred to SHA1PRNG. Otherwise, SHA1PRNG is preferred.
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SecureRandomImp">SecureRandom Implementation</a>.
     *
     * If both dev/random and dev/urandom are available, then dev/random is only preferred over dev/urandom during VM boot
     * where it may be possible that OS didn't yet collect enough entropy to fill the randomness pool for the 1st time.
     * TODO: check default settings per OS and random/urandom availability.
     *
     * @param numOfBytes how many random bytes to output.
     * @return a random [ByteArray].
     */
    fun saferand(numOfBytes: Int): ByteArray {
        val bytes = ByteArray(numOfBytes)
        if (System.getProperty("os.name") == "Linux") {
            SecureRandom.getInstance("NativePRNGNonBlocking").nextBytes(bytes)
        } else {
            SecureRandom.getInstanceStrong().nextBytes(bytes)
        }
        return ByteArray(numOfBytes)
    }

    fun safeRandom(): SecureRandom {
        if (System.getProperty("os.name") == "Linux") {
            return SecureRandom.getInstance("NativePRNGNonBlocking")
        } else {
            return SecureRandom.getInstanceStrong()
        }
    }

    /**
     * Utility to simplify the act of generating keys.
     * Normally, we don't expect other errors here assuming that key generation parameters for every supported algorithm have been unit-tested.
     *
     */
    @Throws(CryptoException::class)
    fun genKeyPair(algorithmName: String): KeyPair {
        return findAlgorithm(algorithmName)?.keyPairGenerator?.generateKeyPair() ?: throw CryptoException("Unsupported algorithm during key generation for String: $algorithmName")
    }

    /**
     * Generic way to sign [ByteArray] messages with a [PrivateKey]. Strategy on the actual signing algorithm is based
     * on the [PrivateKey] type. This class has similarities to the [sign] but it does not attach the public key information.
     *
     * @param privKey the signer's [PrivateKey].
     * @param bytesToSign the message to be signed in [ByteArray] form.
     * @return the digital signature on the input message.
     * @throws CryptoException if privKey algorithm-type is not supported.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @Throws(CryptoException::class, InvalidKeyException::class, SignatureException::class)
    fun doSign(privKey: PrivateKey, bytesToSign: ByteArray): ByteArray {
        val sig: Signature = findAlgorithm(privKey)?.sig ?: throw CryptoException("Unsupported key/algorithm during signing (keyalg: ${privKey.algorithm}, privKey: ${privKey})")
        sig.initSign(privKey)
        sig.update(bytesToSign)
        return sig.sign()
    }

    /**
     * Generic way to sign [ByteArray] messages with a [PrivateKey]. Strategy on the actual signing algorithm is based
     * on the [PrivateKey] type. This class has similarities to the [sign] but it does not attach the public key information.
     *
     * @param bytesToSign the message to be signed in [ByteArray] form.
     * @param key the signer's [PrivateKey].
     * @return the digital signature on the input message.
     */

    /** Utility to simplify the act of verifying a signature
     *
     * @throws CryptoException if privKey algorithm-type is not supported.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signature object is not initialized properly,
     * the passed-in signature is improperly encoded or of the wrong type,
     * if this signature algorithm is unable to process the input data provided, etc.
     */
    @Throws(CryptoException::class)
    fun doVerify(pubKey: PublicKey, signature: ByteArray, clearData: ByteArray): Boolean {
        val sig: Signature = findAlgorithm(pubKey)?.sig ?: throw CryptoException("Unsupported key/algorithm during verification (keyalg: ${pubKey.algorithm}, privKey: ${pubKey})")
        sig.initVerify(pubKey)
        sig.update(clearData)
        return sig.verify(signature)
    }
}