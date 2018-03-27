package net.corda.core.crypto

import net.corda.annotations.serialization.CordaSerializable
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

/**
 * A wrapper over the signature output accompanied by signer's public key and signature metadata.
 * This is similar to [DigitalSignature.WithKey], but targeted to DLT transaction (or block of transactions) signatures.
 * @property bytes actual bytes of the cryptographic signature.
 * @property by [PublicKey] of the signer.
 * @property signatureMetadata attached [SignatureMetadata] for this signature.
 * @property partialMerkleTree required when multi-transaction signing is utilised.
 */
@CordaSerializable
class TransactionSignature(bytes: ByteArray, val by: PublicKey, val signatureMetadata: SignatureMetadata, val partialMerkleTree: PartialMerkleTree?) : DigitalSignature(bytes) {
    /**
     * Construct a [TransactionSignature] with [partialMerkleTree] set to null.
     * This is the recommended constructor when signing over a single transaction.
     * */
    constructor(bytes: ByteArray, by: PublicKey, signatureMetadata: SignatureMetadata) : this(bytes, by, signatureMetadata, null)

    /**
     * Function to verify a [SignableData] object's signature.
     * Note that [SignableData] contains the id of the transaction and extra metadata, such as DLT's platform version.
     * A non-null [partialMerkleTree] implies multi-transaction signing and the signature is over the root of this tree.
     *
     * @param txId transaction's id (Merkle root), which along with [signatureMetadata] will be used to construct the [SignableData] object to be signed.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData algorithm is unable to process the input data provided, etc.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key or if any of the clear or signature data is empty.
     */
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun verify(txId: SecureHash) = Crypto.doVerify(txId, this)

    /**
     * Utility to simplify the act of verifying a signature. In comparison to [verify] doesn't throw an
     * exception, making it more suitable where a boolean is required, but normally you should use the function
     * which throws, as it avoids the risk of failing to test the result.
     *
     * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
     * signature).
     * @throws SignatureException if the signature is invalid (i.e. damaged).
     * @return whether the signature is correct for this key.
     */
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun isValid(txId: SecureHash) = Crypto.isValid(txId, this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionSignature) return false

        return (Arrays.equals(bytes, other.bytes)
                && by == other.by
                && signatureMetadata == other.signatureMetadata)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + by.hashCode()
        result = 31 * result + signatureMetadata.hashCode()
        return result
    }
}
