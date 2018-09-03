package net.corda.node.services.transactions

import net.corda.core.internal.concurrent.fork
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import java.util.concurrent.Executors

class InMemoryTransactionVerifierService(numberOfWorkers: Int) : SingletonSerializeAsToken(), TransactionVerifierService, AutoCloseable {
    private val workerPool = Executors.newFixedThreadPool(numberOfWorkers)
    override fun verify(transaction: LedgerTransaction) = workerPool.fork(transaction::verify)
    override fun close() = workerPool.shutdown()
}
