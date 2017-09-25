package net.corda.client.jfx.model

import javafx.beans.value.ObservableValue
import javafx.collections.ObservableMap
import net.corda.client.jfx.utils.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.SignedTransaction
import org.fxmisc.easybind.EasyBind

/**
 * [PartiallyResolvedTransaction] holds a [SignedTransaction] that has zero or more inputs resolved. The intent is
 * to prepare clients for cases where an input can only be resolved in the future/cannot be resolved at all (for example
 * because of permissioning)
 */
data class PartiallyResolvedTransaction(
        val transaction: SignedTransaction,
        val inputs: List<ObservableValue<InputResolution>>) {
    val id = transaction.id

    sealed class InputResolution {
        abstract val stateRef: StateRef

        data class Unresolved(override val stateRef: StateRef) : InputResolution()
        data class Resolved(val stateAndRef: StateAndRef<ContractState>) : InputResolution() {
            override val stateRef: StateRef get() = stateAndRef.ref
        }
    }

    companion object {
        fun fromSignedTransaction(
                transaction: SignedTransaction,
                transactions: ObservableMap<SecureHash, SignedTransaction>
        ) = PartiallyResolvedTransaction(
                transaction = transaction,
                inputs = transaction.tx.inputs.map { stateRef ->
                    EasyBind.map(transactions.getObservableValue(stateRef.txhash)) {
                        if (it == null) {
                            InputResolution.Unresolved(stateRef)
                        } else {
                            InputResolution.Resolved(it.tx.outRef(stateRef.index))
                        }
                    }
                }
        )
    }
}

/**
 * This model provides an observable list of transactions and what state machines/flows recorded them
 */
class TransactionDataModel {
    private val transactions by observable(NodeMonitorModel::transactions)
    private val collectedTransactions = transactions.recordInSequence()
    private val transactionMap = transactions.recordAsAssociation(SignedTransaction::id)

    val partiallyResolvedTransactions = collectedTransactions.map {
        PartiallyResolvedTransaction.fromSignedTransaction(it, transactionMap)
    }
}
