/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal.performance


import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.base.Stopwatch
import net.corda.core.concurrent.CordaFuture
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.performance.Rate
import net.corda.testing.node.internal.ShutdownManager
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

private val log = LoggerFactory.getLogger("TightLoopInjector")
fun startTightLoopInjector(
        parallelism: Int,
        numberOfInjections: Int,
        queueBound: Int,
        work: () -> Unit
) {
    ShutdownManager.run {
        val executor = Executors.newFixedThreadPool(parallelism)
        registerShutdown { executor.shutdown() }
        val remainingLatch = CountDownLatch(numberOfInjections)
        val queuedCount = AtomicInteger(0)
        val lock = ReentrantLock()
        val canQueueAgain = lock.newCondition()
        val injector = thread(name = "injector") {
            val leftToSubmit = AtomicInteger(numberOfInjections)
            while (true) {
                if (leftToSubmit.getAndDecrement() == 0) break
                executor.submit {
                    try {
                        work()
                    } catch (exception: Exception) {
                        log.error("Error while executing injection", exception)
                    }
                    if (queuedCount.decrementAndGet() < queueBound / 2) {
                        lock.withLock {
                            canQueueAgain.signal()
                        }
                    }
                    remainingLatch.countDown()
                }
                if (queuedCount.incrementAndGet() > queueBound) {
                    lock.withLock {
                        canQueueAgain.await()
                    }
                }
            }
        }
        registerShutdown { injector.interrupt() }
        remainingLatch.await()
        injector.join()
    }
}

fun startPublishingFixedRateInjector(
        metricRegistry: MetricRegistry,
        parallelism: Int,
        overallDuration: Duration,
        injectionRate: Rate,
        workBound: Int,
        queueSizeMetricName: String = "QueueSize",
        workDurationMetricName: String = "WorkDuration",
        work: () -> CordaFuture<*>
) {
    val workSemaphore = Semaphore(0)
    val workBoundSemaphore = Semaphore(workBound)
    metricRegistry.register(queueSizeMetricName, Gauge { workSemaphore.availablePermits() })
    val workDurationTimer = metricRegistry.timer(workDurationMetricName)
    ShutdownManager.run {
        val executor = Executors.newSingleThreadScheduledExecutor()
        registerShutdown { executor.shutdown() }
        val workExecutor = Executors.newFixedThreadPool(parallelism)
        registerShutdown { workExecutor.shutdown() }
        for (i in 1..parallelism) {
            workExecutor.submit {
                try {
                    while (true) {
                        workSemaphore.acquire()
                        workBoundSemaphore.acquire()
                        workDurationTimer.time {
                            work().getOrThrow()
                        }
                        workBoundSemaphore.release()
                    }
                } catch (throwable: Throwable) {
                    throwable.printStackTrace()
                }
            }
        }
        val injector = executor.scheduleAtFixedRate(
                {
                    workSemaphore.release((injectionRate * TimeUnit.SECONDS).toInt())
                },
                0,
                1,
                TimeUnit.SECONDS
        )
        registerShutdown {
            injector.cancel(true)
        }
        Thread.sleep(overallDuration.toMillis())
    }
}
