package net.corda.core.messaging

import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.CordaSerializable
import rx.Observable

/**
 * [FlowHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 *
 * @property id The started state machine's ID.
 * @property returnValue A [CordaFuture] of the flow's return value.
 */
interface FlowHandle<A> : AutoCloseable {
    val id: StateMachineRunId
    val returnValue: CordaFuture<A>

    /**
     * Use this function for flows whose returnValue is not going to be used, so as to free up server resources.
     */
    override fun close()
}

/**
 * [FlowProgressHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 *
 * @property progress The stream of progress tracker events.
 */
interface FlowProgressHandle<A> : FlowHandle<A> {
    val progress: Observable<String>

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
data class FlowProgressHandleImpl<A>(
        override val id: StateMachineRunId,
        override val returnValue: CordaFuture<A>,
        override val progress: Observable<String>) : FlowProgressHandle<A> {

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