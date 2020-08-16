package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.internal.notary.NotaryService
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
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
}

interface TransactionsResolver {
    @Suspendable
    fun downloadDependencies(batchMode: Boolean)

    fun recordDependencies(usedStatesToRecord: StatesToRecord)
}