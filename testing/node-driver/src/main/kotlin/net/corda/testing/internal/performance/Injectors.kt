package net.corda.testing.internal.performance

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.base.Stopwatch
import net.corda.testing.internal.ShutdownManager
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
                    work()
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
        queueSizeMetricName: String = "QueueSize",
        workDurationMetricName: String = "WorkDuration",
        work: () -> Unit
) {
    val workSemaphore = Semaphore(0)
    metricRegistry.register(queueSizeMetricName, Gauge { workSemaphore.availablePermits() })
    val workDurationTimer = metricRegistry.timer(workDurationMetricName)
    ShutdownManager.run {
        val executor = Executors.newSingleThreadScheduledExecutor()
        registerShutdown { executor.shutdown() }
        val workExecutor = Executors.newFixedThreadPool(parallelism)
        registerShutdown { workExecutor.shutdown() }
        val timings = Collections.synchronizedList(ArrayList<Long>())
        for (i in 1..parallelism) {
            workExecutor.submit {
                try {
                    while (true) {
                        workSemaphore.acquire()
                        workDurationTimer.time {
                            timings.add(
                                    Stopwatch.createStarted().apply {
                                        work()
                                    }.stop().elapsed(TimeUnit.MICROSECONDS)
                            )
                        }
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

