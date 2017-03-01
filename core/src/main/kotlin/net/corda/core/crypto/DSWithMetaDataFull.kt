package net.corda.core.crypto

import net.corda.core.serialization.opaque
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * A wrapper around a digital signature accompanied with metadata, see [MetaData.Full] and [DigitalSignature].
 * The signature protocol works as follows: s = sign(MetaData.hashBytes).
 */
open class DSWithMetaDataFull(val signatureData: ByteArray, val metaDataFull: MetaData.Full) : DigitalSignature(signatureData) {
    /**
     * Function to auto-verify a [MetaData.Full] object's signature. [MetaData.Full] contains both public key and clearData.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify() {
        Crypto.doVerify(metaDataFull.publicKey, signatureData, metaDataFull.hashBytes())
    }

    /**
     * Function to verify a signature. This is usually called when [PublicKey] is not already included in Metadata.
     * @param clearData the data/message that was signed (usually the Merkle root).
     * @param publicKey the signer's public key.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify(clearData: ByteArray, publicKey: PublicKey) {
        if (clearData.opaque() != metaDataFull.clearData.opaque()) throw IllegalArgumentException ("MetaData's clearData (Merkle root): ${metaDataFull.clearData.opaque()} does not match the input clearData: ${clearData.opaque()}")
        if (publicKey != metaDataFull.publicKey) throw IllegalArgumentException ("MetaData's publicKey: ${metaDataFull.publicKey.toBase58String()} does not match the input clearData: ${publicKey.toBase58String()}")
        Crypto.doVerify(publicKey, signatureData, metaDataFull.hashBytes())
    }
}
