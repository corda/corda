package net.corda.client.jfx.model

import javafx.beans.value.ObservableValue
import net.corda.client.jfx.utils.distinctBy
import net.corda.client.jfx.utils.lift
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.recordInSequence
import net.corda.core.contracts.*
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.eagerDeserialise
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.math.BigInteger.ZERO

private class Unknown : Contract {
    override fun verify(tx: LedgerTransaction) = throw UnsupportedOperationException()

    object State : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }
}

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
        private val DUMMY_NOTARY = Party(CordaX500Name("Dummy Notary", "Nowhere", "ZZ"), entropyToKeyPair(ZERO).public)

        fun fromSignedTransaction(
                transaction: SignedTransaction,
                inputTransactions: Map<StateRef, SignedTransaction?>
        ): PartiallyResolvedTransaction {
            /**
             * Forcibly deserialize our transaction outputs up-front.
             * Replace any [TransactionState] objects that fail to
             * deserialize with a dummy transaction state that uses
             * the transaction's notary.
             */
            val unknownTransactionState = TransactionState(
                data = Unknown.State,
                contract = Unknown::class.java.name,
                notary = transaction.notary ?: DUMMY_NOTARY
            )
            transaction.coreTransaction.outputs.eagerDeserialise { _, _ -> unknownTransactionState }
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
                    stateRef to rpcProxy.value!!.internalFindVerifiedTransaction(stateRef.txhash)
                }.toMap())
    }
}
