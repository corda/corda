package net.corda.core.messaging

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.flows.StateMachineRunId
import rx.Observable

/**
 * [FlowHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 *
 * @property id The started state machine's ID.
 * @property returnValue A [ListenableFuture] of the flow's return value.
 */
interface FlowHandle<A> : AutoCloseable {
    val id: StateMachineRunId
    val returnValue: ListenableFuture<A>

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
