package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.internal.TransactionVerifierServiceInternal
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.prepareReverify
import net.corda.core.internal.prepareVerify
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess

class InMemoryTransactionVerifierService(
    @Suppress("UNUSED_PARAMETER") numberOfWorkers: Int,
    private val cordappProvider: CordappProviderInternal
) : SingletonSerializeAsToken(), TransactionVerifierService, TransactionVerifierServiceInternal, AutoCloseable {
    companion object {
        private val SEPARATOR = System.lineSeparator() + "-> "
        private val log = contextLogger()
    }

    override fun verify(transaction: LedgerTransaction): CordaFuture<Unit> = verify(transaction, emptyList())

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

    override fun reverifyWithFixups(transaction: LedgerTransaction): CordaFuture<*> {
        return openFuture<Unit>().apply {
            capture {
                val replacementAttachments = cordappProvider.fixupAttachments(transaction.attachments).toList()
                log.warn("Reverifying transaction {} with attachments:{}", transaction.id, replacementAttachments.joinToString(
                    separator = SEPARATOR, prefix = SEPARATOR, postfix = System.lineSeparator()) { attachment ->
                    attachment.id.toString()
                })

                val verifier = transaction.prepareReverify(replacementAttachments)
                withoutDatabaseAccess {
                    verifier.verify()
                }
            }
        }
    }

    override fun close() {}
}
