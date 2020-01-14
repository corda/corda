package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.TransactionVerificationException.BrokenTransactionException
import net.corda.core.internal.TransactionVerifierServiceInternal
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.internalFindTrustedAttachmentForClass
import net.corda.core.internal.prepareVerify
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess

class InMemoryTransactionVerifierService(
    @Suppress("UNUSED_PARAMETER") numberOfWorkers: Int,
    private val cordappProvider: CordappProviderInternal,
    private val attachments: AttachmentStorage
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

    private fun computeReplacementAttachmentsFor(ltx: LedgerTransaction, missingClass: String?): Collection<Attachment> {
        val replacements = cordappProvider.fixupAttachments(ltx.attachments)
        return if (replacements.deepEquals(ltx.attachments)) {
            /*
             * We cannot continue unless we have some idea which
             * class is missing from the attachments.
             */
            if (missingClass == null) {
                throw BrokenTransactionException(
                    txId = ltx.id,
                    message = "No fix-up rules provided for broken attachments:${replacements.toPrettyString()}"
                )
            }

            /*
             * The Node's fix-up rules have not been able to adjust the transaction's attachments,
             * so resort to the original mechanism of trying to find an attachment that contains
             * the missing class. (Do you feel lucky, Punk?)
             */
            val extraAttachment = requireNotNull(attachments.internalFindTrustedAttachmentForClass(missingClass)) {
                """Transaction $ltx is incorrectly formed. Most likely it was created during version 3 of Corda
                |when the verification logic was more lenient. Attempted to find local dependency for class: $missingClass,
                |but could not find one.
                |If you wish to verify this transaction, please contact the originator of the transaction and install the
                |provided missing JAR.
                |You can install it using the RPC command: `uploadAttachment` without restarting the node.
                |""".trimMargin()
            }

            replacements.toMutableSet().apply {
                /*
                 * Check our transaction doesn't already contain this extra attachment.
                 * It seems unlikely that we would, but better safe than sorry!
                 */
                if (!add(extraAttachment)) {
                    throw BrokenTransactionException(
                        txId = ltx.id,
                        message = "Unlinkable class $missingClass inside broken attachments:${replacements.toPrettyString()}"
                    )
                }

                log.warn("""Detected that transaction $ltx does not contain all cordapp dependencies.
                    |This may be the result of a bug in a previous version of Corda.
                    |Attempting to verify using the additional trusted dependency: $extraAttachment for class $missingClass.
                    |Please check with the originator that this is a valid transaction.
                    |YOU ARE ONLY SEEING THIS MESSAGE BECAUSE THE CORDAPPS THAT CREATED THIS TRANSACTION ARE BROKEN!
                    |WE HAVE TRIED TO REPAIR THE TRANSACTION AS BEST WE CAN, BUT CANNOT GUARANTEE WE HAVE SUCCEEDED!
                    |PLEASE FIX THE CORDAPPS AND MIGRATE THESE BROKEN TRANSACTIONS AS SOON AS POSSIBLE!
                    |THIS MESSAGE IS **SUPPOSED** TO BE SCARY!!
                    |""".trimMargin()
                )
            }
        } else {
            replacements
        }
    }

    override fun reverifyWithFixups(transaction: LedgerTransaction, missingClass: String?): CordaFuture<*> {
        return openFuture<Unit>().apply {
            capture {
                val replacementAttachments = computeReplacementAttachmentsFor(transaction, missingClass)
                log.warn("Reverifying transaction {} with attachments:{}", transaction.id, replacementAttachments.toPrettyString())
                val verifier = transaction.prepareVerify(replacementAttachments.toList())
                withoutDatabaseAccess {
                    verifier.verify()
                }
            }
        }
    }

    override fun close() {}
}
