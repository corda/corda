package net.corda.node.services

/**
 * A service for encrypting data. This abstraction does not mandate any security properties except the same service instance will be
 * able to decrypt ciphertext encrypted by it. Further security properties are defined by the implementations. This includes the encryption
 * protocol used.
 */
interface EncryptionService {
    /**
     * Encrypt the given [plaintext]. The encryption key used is dependent on the implementation. The returned ciphertext can be decrypted
     * using [decrypt].
     *
     * An optional public [additionalData] bytes can also be provided which will be authenticated (thus tamperproof) alongside the
     * ciphertext but not encrypted. It will be incorporated into the returned bytes in an implementation dependent fashion.
     */
    fun encrypt(plaintext: ByteArray, additionalData: ByteArray? = null): ByteArray

    /**
     * Decrypt ciphertext that was encrypted using [encrypt] and return the original plaintext plus the additional data authenticated (if
     * present). The service will select the correct encryption key to use.
     */
    fun decrypt(ciphertext: ByteArray): PlaintextAndAAD

    /**
     * Extracts the (unauthenticated) additional data, if present, from the given [ciphertext]. This is the public data that would have been
     * given at encryption time.
     *
     * Note, this method does not verify if the data was tampered with, and hence is unauthenticated. To have it authenticated requires
     * calling [decrypt]. This is still useful however, as it doesn't require the encryption key, and so a third-party can view the
     * additional data without needing access to the key.
     */
    fun extractUnauthenticatedAdditionalData(ciphertext: ByteArray): ByteArray?


    /**
     * Represents the decrypted plaintext and the optional authenticated additional data bytes.
     */
    class PlaintextAndAAD(val plaintext: ByteArray, val authenticatedAdditionalData: ByteArray?) {
        operator fun component1() = plaintext
        operator fun component2() = authenticatedAdditionalData
    }
}
