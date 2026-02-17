package net.corda.core.crypto

import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.serialization.CordaSerializable

/**
 * SignatureMeta is required to add extra meta-data to a transaction's signature.
 * It currently supports platformVersion only, but it can be extended to support a universal digital
 * signature model enabling partial signatures and attaching extra information, such as a user's timestamp or other
 * application-specific fields.
 *
 * @param platformVersion current DLT version.
 * @param schemeNumberID number id of the signature scheme used based on signer's key-pair, see [SignatureScheme.schemeNumberID].
 * @param crossProviderKeyRotationProof a list of signatures proving that the newest key is authorised to sign transactions on behalf of the previous keys.
 */
@CordaSerializable
data class SignatureMetadata(
        val platformVersion: Int,
        val schemeNumberID: Int,
        val crossProviderKeyRotationProof: List<KeyRotationProof> // This should probably be a proofChain: net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
) {

    constructor(
            platformVersion: Int,
            schemeNumberID: Int
    ) : this(
            platformVersion,
            schemeNumberID,
            emptyList()
    )
}
