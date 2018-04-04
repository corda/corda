package net.corda.core.transactions

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.serialization.CordaSerializable

/**
 * A transaction with the minimal amount of information required to compute the unique transaction [id], and
 * resolve a [FullTransaction]. This type of transaction, wrapped in [SignedTransaction], gets transferred across the
 * wire and recorded to storage.
 */
@CordaSerializable
abstract class CoreTransaction : BaseTransaction() {
    /** The inputs of this transaction, containing state references only **/
    abstract override val inputs: List<StateRef>
    abstract override val references: List<StateRef>
}

/** A transaction with fully resolved components, such as input states. */
abstract class FullTransaction : BaseTransaction() {
    abstract override val inputs: List<StateAndRef<ContractState>>
    abstract override val references: List<StateAndRef<ContractState>>

    override fun checkBaseInvariants() {
        super.checkBaseInvariants()
        checkInputsAndReferencesHaveSameNotary()
    }

    private fun checkInputsAndReferencesHaveSameNotary() {
        if (inputs.isEmpty() && references.isEmpty()) return
        val notaries = (inputs + references).map { it.state.notary }.toHashSet()
        check(notaries.size == 1) { "All inputs and references must point to the same notary" }
        check(notaries.single() == notary) { "The specified notary must be the one specified by all inputs and references" }
    }
}