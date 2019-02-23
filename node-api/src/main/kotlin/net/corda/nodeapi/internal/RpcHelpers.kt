package net.corda.nodeapi.internal

import net.corda.core.messaging.CordaRPCOps
import rx.Observable
import java.util.concurrent.TimeUnit

/**
 * Returns an [Observable] that will complete when the node will have cancelled the draining shutdown hook.
 *
 * @param interval the value of the polling interval, default is 5.
 * @param unit the time unit of the polling interval, default is [TimeUnit.SECONDS].
 */
fun CordaRPCOps.hasCancelledDrainingShutdown(interval: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): Observable<Unit> {

    return Observable.interval(interval, unit).map { isWaitingForShutdown() }.takeFirst { waiting -> waiting == false }.map { Unit }
}