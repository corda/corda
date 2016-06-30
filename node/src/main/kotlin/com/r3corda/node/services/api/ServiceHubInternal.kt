package com.r3corda.node.services.api

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRefFactory

abstract class ServiceHubInternal : ServiceHub {
    abstract val monitoringService: MonitoringService
    abstract val protocolLogicRefFactory: ProtocolLogicRefFactory

    /**
     * Given a list of [SignedTransaction]s, writes them to the given storage for validated transactions and then
     * sends them to the wallet for further processing. This is intended for implementations to call from
     * [recordTransactions].
     *
     * @param txs The transactions to record
     */
    internal fun recordTransactionsInternal(writableStorageService: TxWritableStorageService, txs: Iterable<SignedTransaction>) {
        txs.forEach { writableStorageService.validatedTransactions.addTransaction(it) }
        walletService.notifyAll(txs.map { it.tx })
    }

    /**
     * TODO: borrowing this method from service manager work in another branch.  It's required to avoid circular dependency
     *       between SMM and the scheduler.  That particular problem should also be resolved by the service manager work
     *       itself, at which point this method would not be needed (by the scheduler)
     */
    abstract fun <T> startProtocol(loggerName: String, logic: ProtocolLogic<T>): ListenableFuture<T>
}