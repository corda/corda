package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.opaque
import net.corda.core.serialization.serialize
import java.security.PublicKey
import java.time.Instant
import java.util.*

/**
 * A [MetaData] object adds extra information to a transaction. MetaData is used to support a universal
 * digital signature model enabling full, partial, fully or partially blind and metaData attached signatures,
 * (such as an attached timestamp). A MetaData object contains both the merkle root of the transaction and the signer's public key.
 * When signatureType is set to FULL, then visibleInputs and signedInputs can be ignored.
 * Note: We could omit signatureType as it can always be defined by combining visibleInputs and signedInputs,
 * but it helps to speed up the process when FULL is used, and thus we can bypass the extra check on boolean arrays.
 *
 * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
 * @param versionID DLT's version.
 * @param signatureType type of the signature, see [SignatureType] (e.g. FULL, PARTIAL, BLIND, PARTIAL_AND_BLIND).
 * @param timestamp the signature's timestamp as provided by the signer.
 * @param visibleInputs for partially/fully blind signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the transaction were visible when the signature was calculated.
 * @param signedInputs for partial signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the Merkle tree are actually signed.
 * @param merkleRoot the Merkle root of the transaction.
 * @param publicKey the signer's public key.
 */
@CordaSerializable
open class MetaData(
        val schemeCodeName: String,
        val versionID: String,
        val signatureType: SignatureType = SignatureType.FULL,
        val timestamp: Instant?,
        val visibleInputs: BitSet?,
        val signedInputs: BitSet?,
        val merkleRoot: ByteArray,
        val publicKey: PublicKey) {

    fun bytes() = this.serialize().bytes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as MetaData

        if (schemeCodeName != other.schemeCodeName) return false
        if (versionID != other.versionID) return false
        if (signatureType != other.signatureType) return false
        if (timestamp != other.timestamp) return false
        if (visibleInputs != other.visibleInputs) return false
        if (signedInputs != other.signedInputs) return false
        if (merkleRoot.opaque() != other.merkleRoot.opaque()) return false
        if (publicKey != other.publicKey) return false
        return true
    }

    override fun hashCode(): Int {
        var result = schemeCodeName.hashCode()
        result = 31 * result + versionID.hashCode()
        result = 31 * result + signatureType.hashCode()
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + (visibleInputs?.hashCode() ?: 0)
        result = 31 * result + (signedInputs?.hashCode() ?: 0)
        result = 31 * result + Arrays.hashCode(merkleRoot)
        result = 31 * result + publicKey.hashCode()
        return result
    }
}

