package net.corda.core.messaging

import net.corda.core.DoNotImplement
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.CordaSerializable
import rx.Observable

/**
 * [FlowHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 */
@DoNotImplement
@CordaSerializable
interface FlowHandle<A> : AutoCloseable {
    /**
     * The started state machine's ID.
     */
    val id: StateMachineRunId

    /**
     * A [CordaFuture] of the flow's return value.
     */
    val returnValue: CordaFuture<A>

    /**
     * Use this function for flows whose returnValue is not going to be used, so as to free up server resources.
     */
    override fun close()
}

/**
 * [FlowProgressHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 */
interface FlowProgressHandle<A> : FlowHandle<A> {
    /**
     * The stream of progress tracker events.
     */
    val progress: Observable<String>

    /**
     * [DataFeed] of current step in the steps tree, see [ProgressTracker]
     */
    val stepsTreeIndexFeed: DataFeed<Int, Int>?

    /**
     * [DataFeed] of current steps tree, see [ProgressTracker]
     */
    val stepsTreeFeed: DataFeed<List<Pair<Int, String>>, List<Pair<Int, String>>>?

    /**
     * Use this function for flows whose returnValue and progress are not going to be used or tracked, so as to free up
     * server resources.
     * Note that it won't really close if one subscribes on progress [Observable], but then forgets to unsubscribe.
     */
    override fun close()
}


@CordaSerializable
data class FlowHandleImpl<A>(
        override val id: StateMachineRunId,
        override val returnValue: CordaFuture<A>) : FlowHandle<A> {

    // Remember to add @Throws to FlowHandle.close() if this throws an exception.
    override fun close() {
        returnValue.cancel(false)
    }
}

@CordaSerializable
data class FlowProgressHandleImpl<A> @JvmOverloads constructor(
        override val id: StateMachineRunId,
        override val returnValue: CordaFuture<A>,
        override val progress: Observable<String>,
        override val stepsTreeIndexFeed: DataFeed<Int, Int>? = null,
        override val stepsTreeFeed: DataFeed<List<Pair<Int, String>>, List<Pair<Int, String>>>? = null) : FlowProgressHandle<A> {

    // For API compatibility
    fun copy(id: StateMachineRunId, returnValue: CordaFuture<A>, progress: Observable<String>): FlowProgressHandleImpl<A> {
        return copy(id = id, returnValue = returnValue, progress = progress, stepsTreeFeed = null, stepsTreeIndexFeed = null)
    }

    // Remember to add @Throws to FlowProgressHandle.close() if this throws an exception.
    override fun close() {
        try {
            progress.subscribe({}, {}).unsubscribe()
        } catch (e: Exception) {
            // Swallow any other exceptions as well.
        }
        returnValue.cancel(false)
    }
}