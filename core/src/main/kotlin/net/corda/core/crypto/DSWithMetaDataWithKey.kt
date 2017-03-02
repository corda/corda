package net.corda.core.crypto

import net.corda.core.serialization.opaque
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * A wrapper around a digital signature accompanied with metadata see [MetaData.WithKey] and [DigitalSignature].
 * For this Digital Signature type signaning works as follows: s = sign(clearData||MetaData.hashBytes).
 * Currently, the DLT's protocol supports [MetaData.Full] objects that contain both clearData(Merkle root) and the signer's public key.
 * It is recommended that [DSWithMetaDataFull] is used instead. This is only for potential future usage.
 */
@Deprecated("This class is currently not supported from DLT and should be avoided.", ReplaceWith("DSWithMetaDataFull"), DeprecationLevel.WARNING)
open class DSWithMetaDataWithKey(val signatureData: ByteArray, val metaDataWithKey: MetaData.WithKey) : DigitalSignature(signatureData) {
    /**
     * Function to auto-verify a [MetaData.WithKey] object's signature. [MetaData.WithKey] does not contain clearData.
     * @param clearData the data/message that was signed (usually the Merkle root).
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify(clearData: ByteArray): Boolean = Crypto.doVerify(metaDataWithKey.publicKey, signatureData, clearData.plus(metaDataWithKey.hashBytes()))

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
    fun verify(clearData: ByteArray, publicKey: PublicKey): Boolean {
        if (publicKey != metaDataWithKey.publicKey) throw IllegalArgumentException ("MetaData's publicKey: ${metaDataWithKey.publicKey.toBase58String()} does not match the input clearData: ${publicKey.toBase58String()}")
        return Crypto.doVerify(publicKey, signatureData, clearData.plus(metaDataWithKey.hashBytes()))
    }
}
