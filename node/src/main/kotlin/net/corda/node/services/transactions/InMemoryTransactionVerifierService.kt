package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.TransactionVerificationException.BrokenTransactionException
import net.corda.core.internal.TransactionVerifierServiceInternal
import net.corda.core.internal.concurrent.openFuture
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

        fun Collection<*>.deepEquals(other: Collection<*>): Boolean {
            return size == other.size && containsAll(other) && other.containsAll(this)
        }

        fun Collection<Attachment>.toPrettyString(): String {
            return joinToString(separator = SEPARATOR, prefix = SEPARATOR, postfix = System.lineSeparator()) { attachment ->
                attachment.id.toString()
            }
        }
    }

    override fun verify(transaction: LedgerTransaction): CordaFuture<*> {
        return openFuture<Unit>().apply {
            capture {
                val verifier = transaction.prepareVerify(transaction.attachments)
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
                if (replacementAttachments.deepEquals(transaction.attachments)) {
                    throw BrokenTransactionException(
                        txId = transaction.id,
                        message = "No fix-up rules provided for broken attachments:${replacementAttachments.toPrettyString()}"
                    )
                }

                log.warn("Reverifying transaction {} with attachments:{}", transaction.id, replacementAttachments.toPrettyString())
                val verifier = transaction.prepareVerify(replacementAttachments)
                withoutDatabaseAccess {
                    verifier.verify()
                }
            }
        }
    }

    override fun close() {}
}
