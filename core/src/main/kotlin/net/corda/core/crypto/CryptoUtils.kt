package net.corda.core.crypto

import net.corda.core.utilities.SgxSupport
import java.security.*

/**
 * Helper function for signing.
 * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
 * @return the digital signature (in [ByteArray]) on the input message.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
fun PrivateKey.sign(clearData: ByteArray): ByteArray = Crypto.doSign(this, clearData)

/**
 * Helper function for signing.
 * @param metaDataFull tha attached MetaData object.
 * @return a [DSWithMetaDataFull] object.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PrivateKey.sign(metaData: MetaData): TransactionSignature = Crypto.doSign(this, metaData)

/**
 * Helper function to sign with a key pair.
 * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
 * @return the digital signature (in [ByteArray]) on the input message.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(IllegalArgumentException::class, InvalidKeyException::class, SignatureException::class)
fun KeyPair.sign(clearData: ByteArray): ByteArray = Crypto.doSign(this.private, clearData)

/**
 * Helper function to verify a signature.
 * @param signatureData the signature on a message.
 * @param clearData the clear data/message that was signed (usually the Merkle root).
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PublicKey.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this, signatureData, clearData)

/**
 * Helper function to verify a metadata attached signature. It is noted that the transactionSignature contains
 * signatureData and a [MetaData] object that contains the signer's public key and the transaction's Merkle root.
 * @param transactionSignature a [TransactionSignature] object that .
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun PublicKey.verify(transactionSignature: TransactionSignature): Boolean {
    return Crypto.doVerify(this, transactionSignature)
}

/**
 * Helper function for the signers to verify their own signature.
 * @param signature the signature on a message.
 * @param clearData the clear data/message that was signed (usually the Merkle root).
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
 */
@Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
fun KeyPair.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this.public, signatureData, clearData)

/**
 * Generate a securely random [ByteArray] of requested number of bytes. Usually used for seeds, nonces and keys.
 * @param numOfBytes how many random bytes to output.
 * @return a random [ByteArray].
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong SecureRandom implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@Throws(NoSuchAlgorithmException::class)
fun safeRandomBytes(numOfBytes: Int): ByteArray {
    return newSecureRandom().generateSeed(numOfBytes)
}

/**
 * This is a hack added because during deserialisation when no-param constructors are called sometimes default values
 * generate random numbers, which fail in SGX.
 * TODO remove this once deserialisation is figured out.
 */
private class DummySecureRandomSpi : SecureRandomSpi() {
    override fun engineSetSeed(bytes: ByteArray?) {
        Exception("DummySecureRandomSpi.engineSetSeed called").printStackTrace(System.out)
    }

    override fun engineNextBytes(bytes: ByteArray?) {
        Exception("DummySecureRandomSpi.engineNextBytes called").printStackTrace(System.out)
        bytes?.fill(0)
    }

    override fun engineGenerateSeed(numberOfBytes: Int): ByteArray {
        Exception("DummySecureRandomSpi.engineGenerateSeed called").printStackTrace(System.out)
        return ByteArray(numberOfBytes)
    }
}
object DummySecureRandom : SecureRandom(DummySecureRandomSpi(), null)

private val _newSecureRandom: () -> SecureRandom by lazy {
    when {
        SgxSupport.isInsideEnclave -> { { DummySecureRandom } }
        System.getProperty("os.name") == "Linux" -> { { SecureRandom.getInstance("NativePRNGNonBlocking") } }
        else -> { { SecureRandom.getInstanceStrong() } }
    }
}

/**
 * Get an instance of [SecureRandom] to avoid blocking, due to waiting for additional entropy, when possible.
 * In this version, the NativePRNGNonBlocking is exclusively used on Linux OS to utilize dev/urandom because in high traffic
 * /dev/random may wait for a certain amount of "noise" to be generated on the host machine before returning a result.
 *
 * On Solaris, Linux, and OS X, if the entropy gathering device in java.security is set to file:/dev/urandom
 * or file:/dev/random, then NativePRNG is preferred to SHA1PRNG. Otherwise, SHA1PRNG is preferred.
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SecureRandomImp">SecureRandom Implementation</a>.
 *
 * If both dev/random and dev/urandom are available, then dev/random is only preferred over dev/urandom during VM boot
 * where it may be possible that OS didn't yet collect enough entropy to fill the randomness pool for the 1st time.
 * @see <a href="http://www.2uo.de/myths-about-urandom/">Myths about urandom</a> for a more descriptive explanation on /dev/random Vs /dev/urandom.
 * TODO: check default settings per OS and random/urandom availability.
 * @return a [SecureRandom] object.
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong SecureRandom implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@Throws(NoSuchAlgorithmException::class)
fun newSecureRandom() = _newSecureRandom()
