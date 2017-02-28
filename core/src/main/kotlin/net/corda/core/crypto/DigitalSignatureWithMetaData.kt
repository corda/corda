package net.corda.core.crypto

import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * A wrapper around a digital signature accompanied with metadata see [MetaData] and [DigitalSignature].
 * Note that the generic signature protocol works as follows: s = sign(MerkleRoot.bytes||MetaData.hashBytes),
 * where || is byte concatenation, see [MetaData].
 */
open class DigitalSignatureWithMetaData(val signatureData: ByteArray, val metaData: MetaData) : DigitalSignature(signatureData) {
    /**
     * Function to verify a signature. If a [PublicKey] is defined in metaData.
     * @param clearData the data/message that was signed (actual data or Merkle root).
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify(clearData: ByteArray) {
        if (metaData.publicKey != null) {
            verify(clearData, metaData.publicKey)
        } else throw InvalidKeyException("Verification failed. No public key is provided to metaData! " +
                "Please use verify(clearData: ByteArray, publicKey: PublicKey) instead.")
    }

    /**
     * Function to verify a signature. This is usually called when [PublicKey] is not already included in Metadata.
     * @param clearData the data/message that was signed (actual data or Merkle root).
     * @param publicKey the signer's public key.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    @Override
    fun verify(clearData: ByteArray, publicKey: PublicKey) {
        Crypto.doVerify(publicKey, signatureData, clearData.plus(metaData.hashBytes()))
    }
}
