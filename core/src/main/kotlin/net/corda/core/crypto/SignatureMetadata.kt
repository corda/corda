package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * SignatureMeta is required to add extra meta-data to a transaction's signature.
 * It currently supports platformVersion only, but it can be extended to support a universal digital
 * signature model enabling partial signatures and attaching extra information, such as a user's timestamp or other
 * application-specific fields.
 *
 * @property platformVersion current DLT version.
 * @property schemeNumberID number id of the signature scheme used based on signer's key-pair, see [SignatureScheme.schemeNumberID].
 * @param timestamp an [Instant] that represents the time the signer claims the signature took place. If not provided, then the current UTC time [Instant.now] in signer's node is used.
 */
@CordaSerializable
data class SignatureMetadata(val platformVersion: Int, val schemeNumberID: Int, val timestamp: Instant = Instant.now())
