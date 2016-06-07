package com.r3corda.core.node

import com.r3corda.core.contracts.*
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.node.services.*
import java.time.Clock

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 *
 * Any services exposed to protocols (public view) need to implement [SerializeAsToken] or similar to avoid their internal
 * state from being serialized in checkpoints.
 */
interface ServiceHub {
    val walletService: WalletService
    val keyManagementService: KeyManagementService
    val identityService: IdentityService
    val storageService: StorageService
    val networkService: MessagingService
    val networkMapCache: NetworkMapCache
    val clock: Clock

    /**
     * Given a [LedgerTransaction], looks up all its dependencies in the local database, uses the identity service to map
     * the [SignedTransaction]s the DB gives back into [LedgerTransaction]s, and then runs the smart contracts for the
     * transaction. If no exception is thrown, the transaction is valid.
     */
    fun verifyTransaction(ltx: LedgerTransaction) {
        val dependencies = ltx.inputs.map {
            storageService.validatedTransactions.getTransaction(it.txhash) ?: throw TransactionResolutionException(it.txhash)
        }
        val ltxns = dependencies.map { it.verifyToLedgerTransaction(identityService, storageService.attachments) }
        TransactionGroup(setOf(ltx), ltxns.toSet()).verify()
    }

    /**
     * Given a list of [SignedTransaction]s, writes them to the local storage for validated transactions and then
     * sends them to the wallet for further processing.
     *
     * TODO: Need to come up with a way for preventing transactions being written other than by this method.
     *
     * @param txs The transactions to record
     */
    fun recordTransactions(txs: List<SignedTransaction>) {
        txs.forEach { storageService.validatedTransactions.addTransaction(it) }
        walletService.notifyAll(txs.map { it.tx })
    }

    /**
     * Given a [StateRef] loads the referenced transaction and looks up the specified output [ContractState]
     *
     * @throws TransactionResolutionException if the [StateRef] points to a non-existent transaction
     */
    fun loadState(stateRef: StateRef): ContractState {
        val definingTx = storageService.validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return definingTx.tx.outputs[stateRef.index]
    }
}