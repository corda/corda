package net.corda.core.crypto

import java.security.InvalidKeyException
import java.security.SignatureException

/**
 * A wrapper around a digital signature accompanied with metadata, see [MetaData.Full] and [DigitalSignature].
 * The signature protocol works as follows: s = sign(MetaData.hashBytes).
 */
open class TransactionSignature(val signatureData: ByteArray, val metaData: MetaData) : DigitalSignature(signatureData) {
    /**
     * Function to auto-verify a [MetaData] object's signature.
     * Note that [MetaData] contains both public key and merkle root of the transaction.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify(): Boolean = Crypto.doVerify(metaData.publicKey, signatureData, metaData.bytes())
}
