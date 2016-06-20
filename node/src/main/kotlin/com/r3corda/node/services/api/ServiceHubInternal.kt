package com.r3corda.node.services.api

import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.TxWritableStorageService

abstract class ServiceHubInternal : ServiceHub {
    abstract val monitoringService: MonitoringService

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
}