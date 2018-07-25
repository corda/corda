/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("ConcurrencyUtils")
package net.corda.core.concurrent

import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.utilities.getOrThrow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Invoke [getOrThrow] and pass the value/throwable to success/failure respectively. */
fun <V, W> Future<V>.match(success: (V) -> W, failure: (Throwable) -> W): W {
    val value = try {
        getOrThrow()
    } catch (t: Throwable) {
        return failure(t)
    }
    return success(value)
}

/**
 * As soon as a given future becomes done, the handler is invoked with that future as its argument.
 * The result of the handler is copied into the result future, and the handler isn't invoked again.
 * If a given future errors after the result future is done, the error is automatically logged.
 */
fun <V, W> firstOf(vararg futures: CordaFuture<out V>, handler: (CordaFuture<out V>) -> W) = firstOf(futures, defaultLog, handler)

private val defaultLog = LoggerFactory.getLogger("net.corda.core.concurrent")
@VisibleForTesting
internal const val shortCircuitedTaskFailedMessage = "Short-circuited task failed:"

internal fun <V, W> firstOf(futures: Array<out CordaFuture<out V>>, log: Logger, handler: (CordaFuture<out V>) -> W): CordaFuture<W> {
    val resultFuture = openFuture<W>()
    val winnerChosen = AtomicBoolean()
    futures.forEach {
        it.then {
            when {
                winnerChosen.compareAndSet(false, true) -> resultFuture.capture { handler(it) }
                it.isCancelled -> {
                    // Do nothing.
                }
                else -> it.match({}, { log.error(shortCircuitedTaskFailedMessage, it) })
            }
        }
    }
    return resultFuture
}
