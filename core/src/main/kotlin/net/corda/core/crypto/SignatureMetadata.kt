package net.corda.core.crypto

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.serialization.CordaSerializable

/**
 * SignatureMeta is required to add extra meta-data to a transaction's signature.
 * It currently supports platformVersion only, but it can be extended to support a universal digital
 * signature model enabling partial signatures and attaching extra information, such as a user's timestamp or other
 * application-specific fields.
 *
 * @param platformVersion current DLT version.
 * @param schemeNumberID number id of the signature scheme used based on signer's key-pair, see [SignatureScheme.schemeNumberID].
 * @param proofChain a list of proofs proving that the newest key is authorised to sign transactions on behalf of the previous keys.
 */
@CordaSerializable
data class SignatureMetadata(
        val platformVersion: Int,
        val schemeNumberID: Int,
        val proofChain: KeyRotationProofChain? = null
) {

    constructor(
            platformVersion: Int,
            schemeNumberID: Int
    ) : this(
            platformVersion,
            schemeNumberID,
            null
    )
}
