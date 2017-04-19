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
}

/**
 * [FlowProgressHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 *
 * @property progress The stream of progress tracker events.
 */
interface FlowProgressHandle<A> : FlowHandle<A> {
    val progress: Observable<String>
}
