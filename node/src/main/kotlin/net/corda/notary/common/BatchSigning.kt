package net.corda.notary.common

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import java.security.PublicKey

typealias BatchSigningFunction = (Iterable<SecureHash>, Party?) -> BatchSignature

/** Generates a signature over the bach of [txIds]. */
fun signBatch(
        txIds: Iterable<SecureHash>,
        notaryIdentityKey: PublicKey,
        services: ServiceHub
): BatchSignature {
    val merkleTree = MerkleTree.getMerkleTree(txIds.map { it.sha256() })
    val merkleTreeRoot = merkleTree.hash
    val signableData = SignableData(
            merkleTreeRoot,
            SignatureMetadata(
                    services.myInfo.platformVersion,
                    Crypto.findSignatureScheme(notaryIdentityKey).schemeNumberID
            )
    )
    val sig = services.keyManagementService.sign(signableData, notaryIdentityKey)
    return BatchSignature(sig, merkleTree)
}

/** The outcome of just committing a transaction. */
sealed class InternalResult {
    object Success : InternalResult()
    data class Failure(val error: NotaryError) : InternalResult()
}

data class BatchSignature(
        val rootSignature: TransactionSignature,
        val fullMerkleTree: MerkleTree) {
    /** Extracts a signature with a partial Merkle tree for the specified leaf in the batch signature. */
    fun forParticipant(txId: SecureHash): TransactionSignature {
        return TransactionSignature(
                rootSignature.bytes,
                rootSignature.by,
                rootSignature.signatureMetadata,
                PartialMerkleTree.build(fullMerkleTree, listOf(txId.sha256()))
        )
    }
}