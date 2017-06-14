package net.corda.client.rpc

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.Gauge
import com.codahale.metrics.JmxReporter
import com.codahale.metrics.MetricRegistry
import com.google.common.base.Stopwatch
import net.corda.client.rpc.internal.RPCClientConfiguration
import net.corda.core.messaging.RPCOps
import net.corda.core.minutes
import net.corda.core.seconds
import net.corda.core.utilities.Rate
import net.corda.core.utilities.div
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.testing.RPCDriverExposedDSLInterface
import net.corda.testing.driver.ShutdownManager
import net.corda.testing.measure
import net.corda.testing.rpcDriver
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.management.ObjectName
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Ignore("Only use this locally for profiling")
@RunWith(Parameterized::class)
class RPCPerformanceTests : AbstractRPCTest() {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Mode = {0}")
        fun modes() = modes(RPCTestMode.Netty)
    }
    private interface TestOps : RPCOps {
        fun simpleReply(input: ByteArray, sizeOfReply: Int): ByteArray
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 0
        override fun simpleReply(input: ByteArray, sizeOfReply: Int): ByteArray {
            return ByteArray(sizeOfReply)
        }
    }

    private fun RPCDriverExposedDSLInterface.testProxy(
            clientConfiguration: RPCClientConfiguration,
            serverConfiguration: RPCServerConfiguration
    ): TestProxy<TestOps> {
        return testProxy<TestOps>(
                TestOpsImpl(),
                clientConfiguration = clientConfiguration,
                serverConfiguration = serverConfiguration
        )
    }

    private fun warmup() {
        rpcDriver {
            val proxy = testProxy(
                    RPCClientConfiguration.default,
                    RPCServerConfiguration.default
            )
            val executor = Executors.newFixedThreadPool(4)
            val N = 10000
            val latch = CountDownLatch(N)
            for (i in 1 .. N) {
                executor.submit {
                    proxy.ops.simpleReply(ByteArray(1024), 1024)
                    latch.countDown()
                }
            }
            latch.await()
        }
    }

    data class SimpleRPCResult(
            val requestPerSecond: Double,
            val averageIndividualMs: Double,
            val Mbps: Double
    )

    @Test
    fun `measure Megabytes per second for simple RPCs`() {
        warmup()
        val inputOutputSizes = listOf(1024, 4096, 100 * 1024)
        val overallTraffic = 512 * 1024 * 1024L
        measure(inputOutputSizes, (1..5)) { inputOutputSize, _ ->
            rpcDriver {
                val proxy = testProxy(
                        RPCClientConfiguration.default.copy(
                                cacheConcurrencyLevel = 16,
                                observationExecutorPoolSize = 2,
                                producerPoolBound = 2
                        ),
                        RPCServerConfiguration.default.copy(
                                rpcThreadPoolSize = 8,
                                consumerPoolSize = 2,
                                producerPoolBound = 8
                        )
                )

                val numberOfRequests = overallTraffic / (2 * inputOutputSize)
                val timings = Collections.synchronizedList(ArrayList<Long>())
                val executor = Executors.newFixedThreadPool(8)
                val totalElapsed = Stopwatch.createStarted().apply {
                    startInjectorWithBoundedQueue(
                            executor = executor,
                            numberOfInjections = numberOfRequests.toInt(),
                            queueBound = 100
                    ) {
                        val elapsed = Stopwatch.createStarted().apply {
                            proxy.ops.simpleReply(ByteArray(inputOutputSize), inputOutputSize)
                        }.stop().elapsed(TimeUnit.MICROSECONDS)
                        timings.add(elapsed)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                executor.shutdownNow()
                SimpleRPCResult(
                        requestPerSecond = 1000000.0 * numberOfRequests.toDouble() / totalElapsed.toDouble(),
                        averageIndividualMs = timings.average() / 1000.0,
                        Mbps = (overallTraffic.toDouble() / totalElapsed.toDouble()) * (1000000.0 / (1024.0 * 1024.0))
                )
            }
        }.forEach(::println)
    }

    /**
     * Runs 20k RPCs per second for two minutes and publishes relevant stats to JMX.
     */
    @Test
    fun `consumption rate`() {
        rpcDriver {
            val metricRegistry = startReporter()
            val proxy = testProxy(
                    RPCClientConfiguration.default.copy(
                            reapInterval = 1.seconds,
                            cacheConcurrencyLevel = 16,
                            producerPoolBound = 8
                    ),
                    RPCServerConfiguration.default.copy(
                            rpcThreadPoolSize = 8,
                            consumerPoolSize = 1,
                            producerPoolBound = 8
                    )
            )
            measurePerformancePublishMetrics(
                    metricRegistry = metricRegistry,
                    parallelism = 8,
                    overallDuration = 5.minutes,
                    injectionRate = 20000L / TimeUnit.SECONDS,
                    queueSizeMetricName = "$mode.QueueSize",
                    workDurationMetricName = "$mode.WorkDuration",
                    shutdownManager = this.shutdownManager,
                    work = {
                        proxy.ops.simpleReply(ByteArray(4096), 4096)
                    }
            )
        }
    }

    data class BigMessagesResult(
            val Mbps: Double
    )
    @Test
    fun `big messages`() {
        warmup()
        measure(listOf(1)) { clientParallelism -> // TODO this hangs with more parallelism
            rpcDriver {
                val proxy = testProxy(
                        RPCClientConfiguration.default,
                        RPCServerConfiguration.default.copy(
                                consumerPoolSize = 1
                        )
                )
                val executor = Executors.newFixedThreadPool(clientParallelism)
                val numberOfMessages = 1000
                val bigSize = 10_000_000
                val elapsed = Stopwatch.createStarted().apply {
                    startInjectorWithBoundedQueue(
                            executor = executor,
                            numberOfInjections = numberOfMessages,
                            queueBound = 4
                    ) {
                        proxy.ops.simpleReply(ByteArray(bigSize), 0)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                executor.shutdownNow()
                BigMessagesResult(
                        Mbps = bigSize.toDouble() * numberOfMessages.toDouble() / elapsed * (1000000.0 / (1024.0 * 1024.0))
                )
            }
        }.forEach(::println)
    }
}

fun measurePerformancePublishMetrics(
        metricRegistry: MetricRegistry,
        parallelism: Int,
        overallDuration: Duration,
        injectionRate: Rate,
        queueSizeMetricName: String,
        workDurationMetricName: String,
        shutdownManager: ShutdownManager,
        work: () -> Unit
) {
    val workSemaphore = Semaphore(0)
    metricRegistry.register(queueSizeMetricName, Gauge { workSemaphore.availablePermits() })
    val workDurationTimer = metricRegistry.timer(workDurationMetricName)
    val executor = Executors.newSingleThreadScheduledExecutor()
    val workExecutor = Executors.newFixedThreadPool(parallelism)
    val timings = Collections.synchronizedList(ArrayList<Long>())
    for (i in 1 .. parallelism) {
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
    shutdownManager.registerShutdown {
        injector.cancel(true)
        workExecutor.shutdownNow()
        executor.shutdownNow()
        workExecutor.awaitTermination(1, TimeUnit.SECONDS)
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }
    Thread.sleep(overallDuration.toMillis())
}

fun startInjectorWithBoundedQueue(
        executor: ExecutorService,
        numberOfInjections: Int,
        queueBound: Int,
        work: () -> Unit
) {
    val remainingLatch = CountDownLatch(numberOfInjections)
    val queuedCount = AtomicInteger(0)
    val lock = ReentrantLock()
    val canQueueAgain = lock.newCondition()
    val injectorShutdown = AtomicBoolean(false)
    val injector = thread(name = "injector") {
        while (true) {
            if (injectorShutdown.get()) break
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
    remainingLatch.await()
    injectorShutdown.set(true)
    injector.join()
}

fun RPCDriverExposedDSLInterface.startReporter(): MetricRegistry {
    val metricRegistry = MetricRegistry()
    val jmxReporter = thread {
        JmxReporter.
                forRegistry(metricRegistry).
                inDomain("net.corda").
                createsObjectNamesWith { _, domain, name ->
                    // Make the JMX hierarchy a bit better organised.
                    val category = name.substringBefore('.')
                    val subName = name.substringAfter('.', "")
                    if (subName == "")
                        ObjectName("$domain:name=$category")
                    else
                        ObjectName("$domain:type=$category,name=$subName")
                }.
                build().
                start()
    }
    val consoleReporter = thread {
        ConsoleReporter.forRegistry(metricRegistry).build().start(1, TimeUnit.SECONDS)
    }
    shutdownManager.registerShutdown {
        jmxReporter.interrupt()
        consoleReporter.interrupt()
        jmxReporter.join()
        consoleReporter.join()
    }
    return metricRegistry
}
