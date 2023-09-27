package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DeleteForDJVM
import net.corda.core.internal.notary.NotaryService
import net.corda.core.internal.services.VerifyingServiceHub
import net.corda.core.node.StatesToRecord
import java.util.concurrent.ExecutorService

// TODO: This should really be called ServiceHubInternal but that name is already taken by net.corda.node.services.api.ServiceHubInternal.
@DeleteForDJVM
interface ServiceHubCoreInternal : VerifyingServiceHub {
    val externalOperationExecutor: ExecutorService

    /**
     * Optional `NotaryService` which will be `null` for all non-Notary nodes.
     */
    val notaryService: NotaryService?

    fun createTransactionsResolver(flow: ResolveTransactionsFlow): TransactionsResolver
}

interface TransactionsResolver {
    @Suspendable
    fun downloadDependencies(batchMode: Boolean)

    @Suspendable
    fun recordDependencies(usedStatesToRecord: StatesToRecord)
}