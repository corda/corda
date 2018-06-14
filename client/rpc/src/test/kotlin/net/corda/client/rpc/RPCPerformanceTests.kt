/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import com.google.common.base.Stopwatch
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.internal.performance.div
import net.corda.testing.node.internal.performance.startPublishingFixedRateInjector
import net.corda.testing.node.internal.performance.startReporter
import net.corda.testing.node.internal.performance.startTightLoopInjector
import net.corda.testing.node.internal.rpcDriver
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Ignore("Only use this locally for profiling")
@RunWith(Parameterized::class)
class RPCPerformanceTests : AbstractRPCTest() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Mode = {0}")
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

    private fun RPCDriverDSL.testProxy(
            clientConfiguration: CordaRPCClientConfiguration,
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
                    CordaRPCClientConfiguration.DEFAULT,
                    RPCServerConfiguration.DEFAULT
            )
            val executor = Executors.newFixedThreadPool(4)
            val N = 10000
            val latch = CountDownLatch(N)
            for (i in 1..N) {
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
                        CordaRPCClientConfiguration.DEFAULT.copy(
                                observationExecutorPoolSize = 2
                        ),
                        RPCServerConfiguration.DEFAULT.copy(
                                rpcThreadPoolSize = 8
                        )
                )

                val numberOfRequests = overallTraffic / (2 * inputOutputSize)
                val timings = Collections.synchronizedList(ArrayList<Long>())
                val totalElapsed = Stopwatch.createStarted().apply {
                    startTightLoopInjector(
                            parallelism = 8,
                            numberOfInjections = numberOfRequests.toInt(),
                            queueBound = 100
                    ) {
                        val elapsed = Stopwatch.createStarted().apply {
                            proxy.ops.simpleReply(ByteArray(inputOutputSize), inputOutputSize)
                        }.stop().elapsed(TimeUnit.MICROSECONDS)
                        timings.add(elapsed)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
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
            val metricRegistry = startReporter(shutdownManager)
            val proxy = testProxy(
                    CordaRPCClientConfiguration.DEFAULT.copy(
                            reapInterval = 1.seconds
                    ),
                    RPCServerConfiguration.DEFAULT.copy(
                            rpcThreadPoolSize = 8
                    )
            )
            startPublishingFixedRateInjector(
                    metricRegistry = metricRegistry,
                    parallelism = 8,
                    overallDuration = 5.minutes,
                    injectionRate = 20000L / TimeUnit.SECONDS,
                    workBound = 50,
                    queueSizeMetricName = "$mode.QueueSize",
                    workDurationMetricName = "$mode.WorkDuration",
                    work = {
                        doneFuture(proxy.ops.simpleReply(ByteArray(4096), 4096))
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
        measure(listOf(1)) { clientParallelism ->
            // TODO this hangs with more parallelism
            rpcDriver {
                val proxy = testProxy(
                        CordaRPCClientConfiguration.DEFAULT,
                        RPCServerConfiguration.DEFAULT
                )
                val numberOfMessages = 1000
                val bigSize = 10_000_000
                val elapsed = Stopwatch.createStarted().apply {
                    startTightLoopInjector(
                            parallelism = clientParallelism,
                            numberOfInjections = numberOfMessages,
                            queueBound = 4
                    ) {
                        proxy.ops.simpleReply(ByteArray(bigSize), 0)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                BigMessagesResult(
                        Mbps = bigSize.toDouble() * numberOfMessages.toDouble() / elapsed * (1000000.0 / (1024.0 * 1024.0))
                )
            }
        }.forEach(::println)
    }
}
