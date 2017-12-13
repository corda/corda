package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import com.google.common.base.Stopwatch
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.internal.config.User
import net.corda.testing.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.performance.div
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.InternalDriverDSL
import net.corda.testing.node.internal.performance.startPublishingFixedRateInjector
import net.corda.testing.node.internal.performance.startReporter
import net.corda.testing.node.internal.performance.startTightLoopInjector
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.TimeUnit

private fun checkQuasarAgent() {
    if (!(ManagementFactory.getRuntimeMXBean().inputArguments.any { it.contains("quasar") })) {
        throw IllegalStateException("No quasar agent")
    }
}

@Ignore("Run these locally")
class NodePerformanceTests : IntegrationTest() {
     companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(*DUMMY_NOTARY_NAME.toDatabaseSchemaNames("_0", "_1", "_2").toTypedArray(),
                DUMMY_BANK_A_NAME.toDatabaseSchemaName())
    }

    @StartableByRPC
    class EmptyFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
        }
    }

    private data class FlowMeasurementResult(
            val flowPerSecond: Double,
            val averageMs: Double
    )

    @Before
    fun before() {
        checkQuasarAgent()
    }

    @Test
    fun `empty flow per second`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<EmptyFlow>())))).get()

            a.rpcClientToNode().use("A", "A") { connection ->
                val timings = Collections.synchronizedList(ArrayList<Long>())
                val N = 10000
                val overallTiming = Stopwatch.createStarted().apply {
                    startTightLoopInjector(
                            parallelism = 8,
                            numberOfInjections = N,
                            queueBound = 50
                    ) {
                        val timing = Stopwatch.createStarted().apply {
                            connection.proxy.startFlow(::EmptyFlow).returnValue.getOrThrow()
                        }.stop().elapsed(TimeUnit.MICROSECONDS)
                        timings.add(timing)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                println(
                        FlowMeasurementResult(
                                flowPerSecond = N / (overallTiming * 0.000001),
                                averageMs = timings.average() * 0.001
                        )
                )
            }
        }
    }

    @Test
    fun `empty flow rate`() {
        driver(startNodesInProcess = true) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<EmptyFlow>())))).get()
            a as NodeHandle.InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, a.node.services.monitoringService.metrics)
            a.rpcClientToNode().use("A", "A") { connection ->
                startPublishingFixedRateInjector(metricRegistry, 1, 5.minutes, 2000L / TimeUnit.SECONDS) {
                    connection.proxy.startFlow(::EmptyFlow).returnValue.get()
                }
            }
        }
    }

    @Test
    fun `issue flow rate`() {
        driver(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance")) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<CashIssueFlow>())))).get()
            a as NodeHandle.InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, a.node.services.monitoringService.metrics)
            a.rpcClientToNode().use("A", "A") { connection ->
                startPublishingFixedRateInjector(metricRegistry, 1, 5.minutes, 2000L / TimeUnit.SECONDS) {
                    connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), ALICE).returnValue.get()
                }
            }
        }
    }

    @Test
    fun `self pay rate`() {
        val user = User("A", "A", setOf(startFlow<CashIssueFlow>(), startFlow<CashPaymentFlow>()))
        driver(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, rpcUsers = listOf(user))),
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("net.corda.finance"),
                portAllocation = PortAllocation.Incremental(20000)
        ) {
            val notary = defaultNotaryNode.getOrThrow() as NodeHandle.InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, notary.node.services.monitoringService.metrics)
            notary.rpcClientToNode().use("A", "A") { connection ->
                println("ISSUING")
                val doneFutures = (1..100).toList().map {
                    connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue
                }.toList()
                doneFutures.transpose().get()
                println("STARTING PAYMENT")
                startPublishingFixedRateInjector(metricRegistry, 8, 5.minutes, 5L / TimeUnit.SECONDS) {
                    connection.proxy.startFlow(::CashPaymentFlow, 1.DOLLARS, defaultNotaryIdentity).returnValue.get()
                }
            }
        }
    }

    @Test
    fun `single pay`() {
        val user = User("A", "A", setOf(startFlow<CashIssueFlow>(), startFlow<CashPaymentFlow>()))
        driver(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, rpcUsers = listOf(user))),
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("net.corda.finance"),
                portAllocation = PortAllocation.Incremental(20000)
        ) {
            val notary = defaultNotaryNode.getOrThrow() as NodeHandle.InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, notary.node.services.monitoringService.metrics)
            notary.rpcClientToNode().use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
                connection.proxy.startFlow(::CashPaymentFlow, 1.DOLLARS, defaultNotaryIdentity).returnValue.getOrThrow()
            }
        }
    }
}