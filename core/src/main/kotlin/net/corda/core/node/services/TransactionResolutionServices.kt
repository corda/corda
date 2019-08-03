package net.corda.core.node.services

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.componentHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import java.lang.IllegalStateException
import java.security.DigestInputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.X509Certificate

interface TransactionResolutionServices {
    val resolveIdentity: (PublicKey) -> Party?
    val resolveAttachment: (SecureHash) -> Attachment?
    val resolveStateRefAsSerialized: (StateRef) -> SerializedBytes<TransactionState<ContractState>>?
    val resolveNetworkParameters: (SecureHash?) -> NetworkParameters?
}

/**
 * Wrap an instance of [TransactionResolutionServices] adding necessary trust checks
 */
class CheckedTxResolutionService(
        private val wrapped: TransactionResolutionServices,
        val trustRoot: X509Certificate,
        val resolvableBackChain: ResolvableBackChain
): TransactionResolutionServices {

    override val resolveAttachment: (SecureHash) -> Attachment?
        get() = { hash: SecureHash ->
            val attachment = wrapped.resolveAttachment(hash)
            if (attachment != null) {
                val digest = MessageDigest.getInstance("SHA-256")
                val expectedId = DigestInputStream(attachment.open(), digest)
                        .messageDigest
                        .digest()
                if (SecureHash.SHA256(expectedId) != attachment.id) {
                    throw SecurityException("Mismatching attachment")
                }
            }
            attachment
        }

    override val resolveIdentity: (PublicKey) -> Party?
        get() = throw UnsupportedOperationException("Unsupported operation")

    override val resolveNetworkParameters: (SecureHash?) -> NetworkParameters?
        get() = { secureHash: SecureHash? ->
            // TODO: checking hash is not sufficient, it is necessary to validate them against
            // the ledger root identity (network operator key)
            secureHash?.let {
                val result = wrapped.resolveNetworkParameters(it)!!
                if (secureHash != result.serialize().hash) {
                    throw RuntimeException("Failed to verify network parameter resolution")
                }
                result
            }
        }

    override val resolveStateRefAsSerialized: (StateRef) -> SerializedBytes<TransactionState<ContractState>>?
        get() = { stateRef: StateRef ->
            val result = wrapped.resolveStateRefAsSerialized(stateRef)
            if (result != null) {
                /**
                 * Authenticate the serialized [StateAndRef] result against the
                 * mekle tree of the originating transaction.
                 */
                val (tx, id) = stateRef
                val inputFilteredTx = resolvableBackChain.inputTxById[tx]
                        ?: throw IllegalStateException("Missing input transaction")
                val filteredGroup = inputFilteredTx
                        .filteredComponentGroups[ComponentGroupEnum.OUTPUTS_GROUP.ordinal]
                val stateId = filteredGroup.components.indexOf(result)
                check(stateId != -1)
                val hash = componentHash(filteredGroup.nonces[stateId], result)
                if (filteredGroup.partialMerkleTree.leafIndex(hash) != id) {
                    throw GeneralSecurityException("StateRef resolution failed to verify")
                }
            }
            result
        }
}

/**
 * A set of filtered transactions and transactions providing just enough information
 * about the backchain of a [WireTransaction] to resolve it. Marked as serializable
 * for persistence purposes.
 */
@CordaSerializable
data class ResolvableBackChain(
        val inputTxById: Map<SecureHash, FilteredTransaction>
)
