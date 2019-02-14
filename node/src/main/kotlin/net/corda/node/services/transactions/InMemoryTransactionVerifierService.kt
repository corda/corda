package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.internal.TransactionVerifierServiceInternal
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.prepareVerify
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess

class InMemoryTransactionVerifierService(numberOfWorkers: Int) : SingletonSerializeAsToken(), TransactionVerifierService, TransactionVerifierServiceInternal, AutoCloseable {
    override fun verify(transaction: LedgerTransaction): CordaFuture<Unit> = this.verify(transaction, emptyList())

    override fun verify(transaction: LedgerTransaction, extraAttachments: List<Attachment>): CordaFuture<Unit> {
        return openFuture<Unit>().apply {
            capture {
                val verifier = transaction.prepareVerify(extraAttachments)
                withoutDatabaseAccess {
                    verifier.verify()
                }
            }
        }
    }

    override fun close() {}
}
