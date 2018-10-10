package net.corda.nodeapi.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineUpdate
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit

/**
 * Returns a [DataFeed] of the number of pending flows. The [Observable] for the updates will complete the moment all pending flows will have terminated.
 */
fun CordaRPCOps.pendingFlowsCount(): DataFeed<Int, Pair<Int, Int>> {

    val updates = PublishSubject.create<Pair<Int, Int>>()
    val initialPendingFlowsCount = stateMachinesFeed().let {
        var completedFlowsCount = 0
        var pendingFlowsCount = it.snapshot.size
        it.updates.observeOn(Schedulers.io()).subscribe({ update ->
            when (update) {
                is StateMachineUpdate.Added -> {
                    pendingFlowsCount++
                    updates.onNext(completedFlowsCount to pendingFlowsCount)
                }
                is StateMachineUpdate.Removed -> {
                    completedFlowsCount++
                    updates.onNext(completedFlowsCount to pendingFlowsCount)
                    if (completedFlowsCount == pendingFlowsCount) {
                        updates.onCompleted()
                    }
                }
            }
        }, updates::onError)
        if (pendingFlowsCount == 0) {
            updates.onCompleted()
        }
        pendingFlowsCount
    }
    return DataFeed(initialPendingFlowsCount, updates)
}

/**
 * Returns an [Observable] that will complete when the node will have cancelled the draining shutdown hook.
 *
 * @param interval the value of the polling interval, default is 5.
 * @param unit the time unit of the polling interval, default is [TimeUnit.SECONDS].
 */
fun CordaRPCOps.hasCancelledDrainingShutdown(interval: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): Observable<Unit> {

    return Observable.interval(interval, unit).map { isWaitingForShutdown() }.takeFirst { waiting -> waiting == false }.map { Unit }
}