package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable

/**
 * A transaction with the minimal amount of information required to compute the unique transaction [id], and
 * resolve a [FullTransaction]. This type of transaction, wrapped in [SignedTransaction], gets transferred across the
 * wire and recorded to storage.
 */
@CordaSerializable
abstract class CoreTransaction : BaseTransaction() {
    /** The inputs of this transaction, containing state references only. **/
    abstract override val inputs: List<StateRef>
    /** The reference inputs of this transaction, containing the state references only. **/
    abstract override val references: List<StateRef>
    /**
     * Hash of the network parameters that were in force when the transaction was notarised. Null means, that the transaction
     * was created on older version of Corda (before 4), resolution will default to initial parameters.
     */
    abstract val networkParametersHash: SecureHash?
}

/** A transaction with fully resolved components, such as input states. */
abstract class FullTransaction : BaseTransaction() {
    abstract override val inputs: List<StateAndRef<ContractState>>
    abstract override val references: List<StateAndRef<ContractState>>
    /**
     * Network parameters that were in force when this transaction was created. Resolved from the hash of network parameters on the corresponding
     * wire transaction.
     */
    abstract val networkParameters: NetworkParameters?

    override fun checkBaseInvariants() {
        super.checkBaseInvariants()
        checkInputsAndReferencesHaveSameNotary()
    }

    private fun checkInputsAndReferencesHaveSameNotary() {
        if (inputs.isEmpty() && references.isEmpty()) return
        val notaries = (inputs + references).map { it.state.notary }.toHashSet()
        check(notaries.size == 1) { "All inputs and reference inputs must point to the same notary" }
        check(notaries.single() == notary) { "The specified notary must be the one specified by all inputs and input references" }
    }

    /** Make sure the assigned notary is part of the network parameter whitelist. */
    protected fun checkNotaryWhitelisted() {
        notary?.let { notaryParty ->
            // Network parameters will never be null if the transaction is resolved from a CoreTransaction rather than constructed directly.
            networkParameters?.let { parameters ->
                val notaryWhitelist = parameters.notaries.map { it.identity }
                check(notaryParty in notaryWhitelist) {
                    "Notary ($notaryParty:${notaryParty.owningKey.toStringShort()}) specified by the transaction is not on the network parameter whitelist: [${notaryWhitelist.joinToString()}]"
                }
            }
        }
    }
}