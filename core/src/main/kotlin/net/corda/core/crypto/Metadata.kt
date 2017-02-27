package net.corda.core.crypto

import java.security.PublicKey
import java.time.Instant

/**
 * A [MetaData] object contains extra information attached to digital signatures.
 * Metadata is used for a unified digital signature model enabling full, partial, fully or partially blind and metadata attached signatures (such as an attached timestamp).
 * The generic signature protocol works as follows: s = Sign(MerkleRoot||Metadata)||Metadata, where || is byte concatenation.
 * Thus, the protocol states that signers should concatenate the message to be signed
 * (Merkle root) with the bytes of a MetaData object, so verification is always feasible.
 * When signatureType is set to FULL_MERKLE or FULL_CLEAR or BLIND, then visibleInputs and signedInputs can be ignored.
 * Note: We could omit signatureType as it can always be defined by combining visibleInputs and singnedInputs,
 * but it helps to speed up the process when FULL_MERKLE is used, and thus we can bypass the extra Boolean Arrays.
 *
 * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
 * @param versionID DLT's version.
 * @param signatureType type of the signature, see [SignatureType] (e.g. FULL_MERKLE, FULL_CLEAR, PARTIAL, BLIND, PARTIAL_AND_BLIND).
 * @param timestamp the signature's timestamp.
 * @param publicKey the signer's public key.
 * @param visibleInputs for partially/fully blind signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the transaction were visible when the signature was calculated.
 * @param signedInputs for partial signatures. We use Merkle tree boolean index flags (from left to right)
 * indicating what parts of the Merkle tree are actually signed.
 */

data class MetaData(
        val schemeCodeName: String,
        val versionID: String,
        val signatureType: SignatureType = SignatureType.FULL_MERKLE,
        val timestamp: Instant?,
        val publicKey: PublicKey?,
        val visibleInputs: BooleanArray?,
        val signedInputs: BooleanArray?
)
