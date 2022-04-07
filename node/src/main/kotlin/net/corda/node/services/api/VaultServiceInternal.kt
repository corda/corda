package net.corda.node.services.api

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction

interface VaultServiceInternal : VaultService {
    fun start()

    /**
     * Splits the provided [txns] into batches of [WireTransaction] and [NotaryChangeWireTransaction].
     * This is required because the batches get aggregated into single updates, and we want to be able to
     * indicate whether an update consists entirely of regular or notary change transactions, which may require
     * different processing logic.
     */
    fun notifyAll(statesToRecord: StatesToRecord, txns: Iterable<CoreTransaction>, previouslySeenTxns: Iterable<CoreTransaction> = emptyList())

    /**
     * Same as notifyAll but with a single transaction.
     * This does not allow for passing transactions that have already been seen by the node, as this API is only used in testing.
     */
    fun notify(statesToRecord: StatesToRecord, tx: CoreTransaction) = notifyAll(statesToRecord, listOf(tx))

    /**
     * Persist [inputs] and [refs] for which we were a participant but may not have observed the full transaction.
     */
    fun notify(inputs: Set<StateAndRef<ContractState>>, refs: Set<StateAndRef<ContractState>>)
}
