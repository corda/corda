package net.corda.client.jfx.model

import javafx.beans.value.ObservableValue
import net.corda.client.jfx.utils.distinctBy
import net.corda.client.jfx.utils.lift
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.recordInSequence
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

/**
 * [PartiallyResolvedTransaction] holds a [SignedTransaction] that has zero or more inputs resolved. The intent is
 * to prepare clients for cases where an input can only be resolved in the future/cannot be resolved at all (for example
 * because of permissioning)
 */
data class PartiallyResolvedTransaction(
        val transaction: SignedTransaction,
        val inputs: List<ObservableValue<InputResolution>>,
        val outputs: List<ObservableValue<out OutputResolution>>) {
    val id = transaction.id

    sealed class InputResolution {
        abstract val stateRef: StateRef

        data class Unresolved(override val stateRef: StateRef) : InputResolution()
        data class Resolved(val stateAndRef: StateAndRef<ContractState>) : InputResolution() {
            override val stateRef: StateRef get() = stateAndRef.ref
        }
    }

    sealed class OutputResolution {
        abstract val stateRef: StateRef

        data class Unresolved(override val stateRef: StateRef) : OutputResolution()
        data class Resolved(val stateAndRef: StateAndRef<ContractState>) : OutputResolution() {
            override val stateRef: StateRef get() = stateAndRef.ref
        }
    }

    companion object {
        fun fromSignedTransaction(
                transaction: SignedTransaction,
                inputTransactions: Map<StateRef, SignedTransaction?>
        ): PartiallyResolvedTransaction {
            return PartiallyResolvedTransaction(
                    transaction = transaction,
                    inputs = transaction.inputs.map { stateRef ->
                        val tx = inputTransactions[stateRef]
                        if (tx == null) {
                            InputResolution.Unresolved(stateRef)
                        } else {
                            InputResolution.Resolved(tx.coreTransaction.outRef(stateRef.index))
                        }.lift()
                    },
                    outputs = if (transaction.coreTransaction is WireTransaction) {
                        transaction.tx.outRefsOfType<ContractState>().map {
                            OutputResolution.Resolved(it).lift()
                        }
                    } else {
                        // Transaction will have the same number of outputs as inputs
                        val outputCount = transaction.coreTransaction.inputs.size
                        val stateRefs = (0 until outputCount).map { StateRef(transaction.id, it) }
                        stateRefs.map { stateRef ->
                            val tx = inputTransactions[stateRef]
                            if (tx == null) {
                                OutputResolution.Unresolved(stateRef)
                            } else {
                                OutputResolution.Resolved(tx.coreTransaction.outRef(stateRef.index))
                            }.lift()
                        }
                    })
        }
    }
}

/**
 * This model provides an observable list of transactions and what state machines/flows recorded them
 */
class TransactionDataModel {
    private val transactions by observable(NodeMonitorModel::transactions)
    private val collectedTransactions = transactions.recordInSequence().distinctBy { it.id }
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    @Suppress("DEPRECATION")
    val partiallyResolvedTransactions = collectedTransactions.map {
        PartiallyResolvedTransaction.fromSignedTransaction(it,
                it.inputs.map { stateRef ->
                    stateRef to rpcProxy.value!!.cordaRPCOps.internalFindVerifiedTransaction(stateRef.txhash)
                }.toMap())
    }
}
