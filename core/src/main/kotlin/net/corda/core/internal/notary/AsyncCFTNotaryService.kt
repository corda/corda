package net.corda.core.internal.notary

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.Party
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.executeAsync
import net.corda.core.internal.notary.AsyncUniquenessProvider.Result
import net.corda.core.serialization.CordaSerializable

/** Base notary implementation for a notary that supports asynchronous calls from a flow. */
abstract class AsyncCFTNotaryService : TrustedAuthorityNotaryService() {
    override val uniquenessProvider: UniquenessProvider get() = asyncUniquenessProvider
    /** A uniqueness provider that supports asynchronous commits. */
    protected abstract val asyncUniquenessProvider: AsyncUniquenessProvider

    /**
     * @throws NotaryInternalException if any of the states have been consumed by a different transaction.
     */
    @Suspendable
    override fun commitInputStates(
            inputs: List<StateRef>,
            txId: SecureHash,
            caller: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ) {
        // TODO: Log the request here. Benchmarking shows that logging is expensive and we might get better performance
        // when we concurrently log requests here as part of the flows, instead of logging sequentially in the
        // `UniquenessProvider`.
        val result = FlowLogic.currentTopLevel!!.executeAsync(AsyncCFTNotaryService.CommitOperation(this, inputs, txId, caller, requestSignature, timeWindow, references))
        if (result is Result.Failure) throw NotaryInternalException(result.error)
    }

    /**
     * Commits the provided input states asynchronously.
     *
     * If a consumed state conflict is reported by the [asyncUniquenessProvider], but it is caused by the same
     * transaction – the transaction is getting notarised twice – a success response will be returned.
     */
    private fun commitAsync(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ): CordaFuture<Result> {
        return asyncUniquenessProvider.commitAsync(states, txId, callerIdentity, requestSignature, timeWindow, references)
    }

    /**
     * Required for the flow to be able to suspend until the commit is complete.
     * This object will be included in the flow checkpoint.
     */
    @CordaSerializable
    class CommitOperation(
            val service: AsyncCFTNotaryService,
            val inputs: List<StateRef>,
            val txId: SecureHash,
            val caller: Party,
            val requestSignature: NotarisationRequestSignature,
            val timeWindow: TimeWindow?,
            val references: List<StateRef>
    ): FlowAsyncOperation<Result> {
        override fun execute(): CordaFuture<Result> {
            return service.commitAsync(inputs, txId, caller, requestSignature, timeWindow, references)
        }
    }
}

