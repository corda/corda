package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable

/**
 * Supported Signature types:
 * <p><ul>
 * <li>FULL = signature covers whole transaction, by the convention that signing the Merkle root, it is equivalent to signing all parts of the transaction.
 * <li>PARTIAL = signature covers only a part of the transaction, see [MetaData].
 * <li>BLIND = when an entity blindly signs without having full knowledge on the content, see [MetaData].
 * <li>PARTIAL_AND_BLIND = combined PARTIAL and BLIND in the same time.
 * </ul>
 */
@CordaSerializable
enum class SignatureType {
    FULL, PARTIAL, BLIND, PARTIAL_AND_BLIND
}
