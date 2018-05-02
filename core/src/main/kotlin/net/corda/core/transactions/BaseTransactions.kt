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
    companion object {
        /**
         * A conservative limit until we get rid of issues with keeping collections of [SignedTransaction]s in memory.
         */
        const val maxTransactionDependencies = 300

        internal fun checkMaxTransactionDependencies(inputRefs: List<StateRef>) {
            check(inputRefs.map { it.txhash }.distinct().size <= maxTransactionDependencies) { "Transaction with ${inputRefs.size} transaction dependencies from inputs exceeded maximum count of $maxTransactionDependencies." }
        }
    }

    /** The inputs of this transaction, containing state references only **/
    abstract override val inputs: List<StateRef>

    override fun checkBaseInvariants() {
        super.checkBaseInvariants()
        checkMaxTransactionDependencies(inputs)
    }
}

/** A transaction with fully resolved components, such as input states. */
abstract class FullTransaction : BaseTransaction() {
    abstract override val inputs: List<StateAndRef<ContractState>>

    override fun checkBaseInvariants() {
        super.checkBaseInvariants()
        checkInputsHaveSameNotary()
        CoreTransaction.checkMaxTransactionDependencies(inputs.map { it.ref })
    }

    private fun checkInputsHaveSameNotary() {
        if (inputs.isEmpty()) return
        val inputNotaries = inputs.map { it.state.notary }.toHashSet()
        check(inputNotaries.size == 1) { "All inputs must point to the same notary" }
        check(inputNotaries.single() == notary) { "The specified notary must be the one specified by all inputs" }
    }
}