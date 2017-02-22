package net.corda.core.crypto

import java.security.*

/**
 * Helper function for signing.
 * @param bytesToSign the data/message to be signed in [ByteArray] form.
 * @return the digital signature (in [ByteArray]) on the input message.
 * @throws Exception if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(Exception::class, InvalidKeyException::class, SignatureException::class)
fun PrivateKey.sign(bytesToSign: ByteArray): ByteArray = Crypto.doSign(this, bytesToSign)

/**
 * Helper function to sign with a key pair.
 * @param bytesToSign the data/message to be signed in [ByteArray] form.
 * @return the digital signature (in [ByteArray]) on the input message.
 * @throws Exception if the signature scheme is not supported for this private key.
 * @throws InvalidKeyException if the private key is invalid.
 * @throws SignatureException if signing is not possible due to malformed data or private key.
 */
@Throws(Exception::class, InvalidKeyException::class, SignatureException::class)
fun KeyPair.sign(bytesToSign: ByteArray): ByteArray = Crypto.doSign(this.private, bytesToSign)

/**
 * Helper function to verify a signature.
 * @param signatureData the signature on a message.
 * @param clearData the clear data/message that was signed.
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws Exception if verification is not possible.
 */
@Throws(Exception::class, InvalidKeyException::class, SignatureException::class)
fun PublicKey.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this, signatureData, clearData)

/**
 * Helper function for the signers to verify their own signature.
 * @param signature the signature on a message.
 * @param clearData the clear data/message that was signed.
 * @throws InvalidKeyException if the key is invalid.
 * @throws SignatureException if this signatureData object is not initialized properly,
 * the passed-in signatureData is improperly encoded or of the wrong type,
 * if this signatureData algorithm is unable to process the input data provided, etc.
 * @throws Exception if verification is not possible.
 */
@Throws(Exception::class, InvalidKeyException::class, SignatureException::class)
fun KeyPair.verify(signatureData: ByteArray, clearData: ByteArray): Boolean = Crypto.doVerify(this.public, signatureData, clearData)
