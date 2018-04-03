package net.corda.core.crypto

import net.corda.annotations.serialization.Serializable

/**
 * A [SignableData] object is the packet actually signed.
 * It works as a wrapper over transaction id and signature metadata.
 * Note that when multi-transaction signing (signing a block of transactions) is used, the root of the Merkle tree
 * (having transaction IDs as leaves) is actually signed and thus [txId] refers to this root and not a specific transaction.
 *
 * @param txId transaction's id or root of multi-transaction Merkle tree in case of multi-transaction signing.
 * @param signatureMetadata meta data required.
 */
@Serializable
data class SignableData(val txId: SecureHash, val signatureMetadata: SignatureMetadata)
