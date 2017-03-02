package net.corda.core.crypto

import net.corda.core.serialization.opaque
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * A wrapper around a digital signature accompanied with metadata that contains clearData, see [MetaData.WithClearData] and [DigitalSignature].
 * The signature protocol works as follows: s = sign(MetaData.hashBytes).
 */
open class DSWithMetaDataWithClearData(val signatureData: ByteArray, val metaDataWithClearData: MetaData.WithClearData) : DigitalSignature(signatureData) {
    /**
     * Function to auto-verify a [MetaData.WithClearData] object's signature. Note that [MetaData.WithClearData] does not contain the signer's public key.
     * @param publicKey the signer's public key.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class, IllegalArgumentException::class)
    fun verify(publicKey: PublicKey): Boolean = Crypto.doVerify(publicKey, signatureData, metaDataWithClearData.hashBytes())
}
