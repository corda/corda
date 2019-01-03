package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.internal.TransactionVerifierServiceInternal
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.prepareVerify
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import java.util.concurrent.Executors

class InMemoryTransactionVerifierService(numberOfWorkers: Int) : SingletonSerializeAsToken(), TransactionVerifierService, TransactionVerifierServiceInternal, AutoCloseable {
    private val workerPool = Executors.newFixedThreadPool(numberOfWorkers)
    override fun verify(transaction: LedgerTransaction): CordaFuture<Unit> = this.verify(transaction, emptyList())

    override fun verify(transaction: LedgerTransaction, extraAttachments: List<Attachment>): CordaFuture<Unit> {
        val verifier = transaction.prepareVerify(extraAttachments)
        return workerPool.fork {
            verifier.verify()
        }
    }

    override fun close() = workerPool.shutdown()
}
