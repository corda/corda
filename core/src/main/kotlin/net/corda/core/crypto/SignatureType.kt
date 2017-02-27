package net.corda.core.crypto

/**
 * Supported Signature types:
 * <p><ul>
 * <li>FULL_CLEAR = conventional signing of clear bytes. Required for compatibility with normal non-Merkle root signatures.
 * <li>FULL_MERKLE = signature covers whole transaction, by the convention that signing the Merkle root, it is equivalent to signing all parts of the transaction.
 * <li>PARTIAL = signature covers only a part of the transaction, see [Metadata].
 * <li>BLIND = when an entity blindly signs without having full knowledge on the content.
 * <li>PARTIAL_AND_BLIND = combined PARTIAL and BLIND in the same time.
 * </ul>
 */
enum class SignatureType {
    FULL_CLEAR, FULL_MERKLE, PARTIAL, BLIND, PARTIAL_AND_BLIND
}