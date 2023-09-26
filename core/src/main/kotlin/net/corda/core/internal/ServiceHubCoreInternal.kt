package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.TransactionMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.services.VerifyingServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import java.util.concurrent.ExecutorService

// TODO: This should really be called ServiceHubInternal but that name is already taken by net.corda.node.services.api.ServiceHubInternal.
interface ServiceHubCoreInternal : VerifyingServiceHub {
    val externalOperationExecutor: ExecutorService

    /**
     * Optional `NotaryService` which will be `null` for all non-Notary nodes.
     */
    val notaryService: NotaryService?

    fun createTransactionsResolver(flow: ResolveTransactionsFlow): TransactionsResolver

    /**
     * Stores [SignedTransaction] and participant signatures without the notary signature in the local transaction storage,
     * inclusive of flow recovery metadata.
     * This is expected to be run within a database transaction.
     *
     * @param txn The transaction to record.
     */
    fun recordUnnotarisedTransaction(txn: SignedTransaction)

    /**
     * Removes transaction from data store.
     * This is expected to be run within a database transaction.
     *
     * @param id of transaction to remove.
     */
    fun removeUnnotarisedTransaction(id: SecureHash)

    /**
     * Stores [SignedTransaction] with extra signatures in the local transaction storage
     *
     * @param sigs The signatures to add to the transaction.
     * @param txn The transactions to record.
     * @param statesToRecord how the vault should treat the output states of the transaction.
     */
    fun finalizeTransactionWithExtraSignatures(txn: SignedTransaction, sigs: Collection<TransactionSignature>, statesToRecord: StatesToRecord)

    /**
     * Records a [SignedTransaction] as VERIFIED with flow recovery metadata.
     *
     * @param txn The transaction to record.
     * @param statesToRecord how the vault should treat the output states of the transaction.
     */
    fun finalizeTransaction(txn: SignedTransaction, statesToRecord: StatesToRecord)

    /**
     * Records Sender [TransactionMetadata] for a given txnId.
     *
     * @param txnId The SecureHash of a transaction.
     * @param txnMetadata The recovery metadata associated with a transaction.
     * @return encrypted distribution list (hashed peers -> StatesToRecord values).
     */
    fun recordSenderTransactionRecoveryMetadata(txnId: SecureHash, txnMetadata: TransactionMetadata): ByteArray?

    /**
     * Records Received [TransactionMetadata] for a given txnId.
     *
     * @param txnId The SecureHash of a transaction.
     * @param sender The sender of the transaction.
     * @param txnMetadata The recovery metadata associated with a transaction.
     */
    fun recordReceiverTransactionRecoveryMetadata(txnId: SecureHash,
                                                  sender: CordaX500Name,
                                                  txnMetadata: TransactionMetadata)
}

interface TransactionsResolver {
    @Suspendable
    fun downloadDependencies(batchMode: Boolean)

    @Suspendable
    fun recordDependencies(usedStatesToRecord: StatesToRecord)
}
