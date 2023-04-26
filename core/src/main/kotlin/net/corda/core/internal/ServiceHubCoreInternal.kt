package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowTransactionMetadata
import net.corda.core.internal.notary.NotaryService
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.transactions.SignedTransaction
import java.util.concurrent.ExecutorService

// TODO: This should really be called ServiceHubInternal but that name is already taken by net.corda.node.services.api.ServiceHubInternal.
@DeleteForDJVM
interface ServiceHubCoreInternal : ServiceHub {

    val externalOperationExecutor: ExecutorService

    val attachmentTrustCalculator: AttachmentTrustCalculator

    /**
     * Optional `NotaryService` which will be `null` for all non-Notary nodes.
     */
    val notaryService: NotaryService?

    fun createTransactionsResolver(flow: ResolveTransactionsFlow): TransactionsResolver

    val attachmentsClassLoaderCache: AttachmentsClassLoaderCache

    /**
     * Stores [SignedTransaction] and participant signatures without the notary signature in the local transaction storage.
     * Optionally add finality flow recovery metadata.
     * This is expected to be run within a database transaction.
     *
     * @param txn The transaction to record.
     */
    fun recordUnnotarisedTransaction(txn: SignedTransaction, metadata: FlowTransactionMetadata?= null)

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
}

interface TransactionsResolver {
    @Suspendable
    fun downloadDependencies(batchMode: Boolean)

    @Suspendable
    fun recordDependencies(usedStatesToRecord: StatesToRecord)
}