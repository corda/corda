package net.corda.core.internal.services

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.BaseTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction

/**
 * Abstraction specifying the operations required to resolve a [TransactionState] from a [StateRef]. Note, this is a subset of the
 * operations required to resolve a [LedgerTransaction], which is handled by [VerificationSupport].
 *
 * @see loadState
 * @see loadStates
 */
interface StateResolutionSupport {
    val appClassLoader: ClassLoader

    fun getSignedTransaction(id: SecureHash): SignedTransaction?

    fun getNetworkParameters(id: SecureHash?): NetworkParameters?

    fun getAttachment(id: SecureHash): Attachment?

    fun loadState(stateRef: StateRef): TransactionState<*> = toBaseTransaction(stateRef.txhash).outputs[stateRef.index]

    fun <T : ContractState, C : MutableCollection<StateAndRef<T>>> loadStates(input: Iterable<StateRef>, output: C): C {
        val baseTxs = HashMap<SecureHash, BaseTransaction>()
        return input.mapTo(output) { stateRef ->
            val baseTx = baseTxs.computeIfAbsent(stateRef.txhash, ::toBaseTransaction)
            StateAndRef(uncheckedCast(baseTx.outputs[stateRef.index]), stateRef)
        }
    }

    fun getRequiredSignedTransaction(id: SecureHash): SignedTransaction {
        return getSignedTransaction(id) ?: throw TransactionResolutionException(id)
    }

    private fun toBaseTransaction(txhash: SecureHash): BaseTransaction = BaseTransaction.resolve(getRequiredSignedTransaction(txhash), this)
}
