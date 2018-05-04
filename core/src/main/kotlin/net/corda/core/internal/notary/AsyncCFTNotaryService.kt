/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal.notary

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.Party
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.notary.AsyncUniquenessProvider.Result
import net.corda.core.serialization.CordaSerializable

/** Base notary implementation for a notary that supports asynchronous calls from a flow. */
abstract class AsyncCFTNotaryService : TrustedAuthorityNotaryService() {
    override val uniquenessProvider: UniquenessProvider get() = asyncUniquenessProvider
    /** A uniqueness provider that supports asynchronous commits. */
    protected abstract val asyncUniquenessProvider: AsyncUniquenessProvider

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
            timeWindow: TimeWindow?
    ): CordaFuture<Result> {
        return asyncUniquenessProvider.commitAsync(states, txId, callerIdentity, requestSignature, timeWindow)
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
            val timeWindow: TimeWindow?) : FlowAsyncOperation<Result> {
        override fun execute(): CordaFuture<Result> {
            return service.commitAsync(inputs, txId, caller, requestSignature, timeWindow)
        }
    }
}

