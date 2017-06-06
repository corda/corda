package net.corda.node

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.GBP
import net.corda.core.failure
import net.corda.core.future
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.success
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.CashFlowCommand
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StabilityTest {
    // TODO : Make these configurable.
    private val memoryThreshold = 0.95
    private val iteration = 1000
    private val startingFrequency = 8
    private val statsUpdateRate = 1000L

    @Test
    fun `node stress test`() {
        var frequency = startingFrequency
        while (true) {
            // Start nodes for each test.
            val reachedMemoryThreshold = driver {
                val notary = startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type))).get()
                val node1 = startNode(X500Name("CN=Node1"), advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.GBP")))).get()
                val node2 = startNode(X500Name("CN=Node2"), advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.GBP")))).get()

                // Pre-issue bunch of cash.
                startStabilityTest("Node1 Issue", node1.rpc, 100, 100, CashFlowCommand.IssueCash(Amount(100_000, GBP), OpaqueBytes(ByteArray(1, { 0.toByte() })), node1.nodeInfo.legalIdentity, notary.nodeInfo.notaryIdentity)).get()
                startStabilityTest("Node2 Issue", node2.rpc, 100, 100, CashFlowCommand.IssueCash(Amount(100_000, GBP), OpaqueBytes(ByteArray(1, { 0.toByte() })), node2.nodeInfo.legalIdentity, notary.nodeInfo.notaryIdentity)).get()

                // TODO : output the stats to a file?
                val node1Test = startStabilityTest("Node1 Pay Node2", node1.rpc, iteration, frequency, CashFlowCommand.PayCash(Amount(1, GBP), node2.nodeInfo.legalIdentity)).get()
                println(node1Test)
                val node2Test = startStabilityTest("Node2 Pay Node1", node2.rpc, iteration, frequency, CashFlowCommand.PayCash(Amount(1, GBP), node1.nodeInfo.legalIdentity)).get()
                println(node2Test)
                frequency++
                node1Test.reachedMemoryThreshold && node1Test.reachedMemoryThreshold
            }
            if (reachedMemoryThreshold) {
                break
            }
        }
    }

    private fun startStabilityTest(testName: String, rpc: CordaRPCOps, iteration: Int, frequency: Int, cashFlowCommand: CashFlowCommand): ListenableFuture<StabilityTestReport> {
        val counter = Counter(AtomicInteger(), AtomicInteger(), AtomicInteger())
        val startTime = System.currentTimeMillis()

        val executor = Executors.newScheduledThreadPool(20)
        executor.scheduleAtFixedRate({
            if (counter.started.get() < iteration) {
                cashFlowCommand.startFlow(rpc).returnValue.success { counter.success.incrementAndGet() }.failure { counter.failed.incrementAndGet() }
                counter.started.incrementAndGet()
            }
        }, 0, 1 / frequency.toLong(), TimeUnit.SECONDS)

        return future {
            val stats = mutableListOf<StabilityTestStat>()
            while ((!executor.isShutdown && counter.success.get() + counter.failed.get() < iteration) || (executor.isShutdown && counter.success.get() + counter.failed.get() < counter.started.get())) {
                val nodeStatus = rpc.nodeStatus()
                stats.add(StabilityTestStat(nodeStatus.stateMachineCount, nodeStatus.freeMemory, nodeStatus.totalMemory, counter.started.get(), counter.success.get(), counter.failed.get()))
                if (counter.started.get() < iteration && !executor.isShutdown && nodeStatus.freeMemory < nodeStatus.totalMemory * (1 - memoryThreshold)) {
                    println("Reached memory threshold, stopping the test.")
                    executor.shutdown()
                    println("Waiting for State machines to finish.")
                }
                Thread.sleep(statsUpdateRate)
            }
            val nodeStatus = rpc.nodeStatus()
            stats.add(StabilityTestStat(nodeStatus.stateMachineCount, nodeStatus.freeMemory, nodeStatus.totalMemory, counter.started.get(), counter.success.get(), counter.failed.get()))
            StabilityTestReport(testName, startTime, System.currentTimeMillis(), frequency, iteration, stats)
        }
    }

    private data class StabilityTestReport(val testName: String, val startedTime: Long, val endTime: Long, val frequency: Int, val iteration: Int, val testStats: List<StabilityTestStat>) {
        val reachedMemoryThreshold = testStats.last().startedFlows < iteration
        val duration = (endTime - startedTime) / 1000
        override fun toString(): String {
            return "testName: $testName\tduration: ${duration}s\tfrequency: $frequency flow/s\titeration: $iteration\tcompleted: ${testStats.last().startedFlows}\tTPS: ${testStats.last().startedFlows / duration}\n" +
                    "State Machine\tFree Memory\tTotal Memory\tStarted Flow\tSucceed Flow\tFailed Flow\n" +
                    testStats.joinToString("\n")
        }
    }

    private data class StabilityTestStat(val stateMachineCount: Int, val freeMemory: Long, val totalMemory: Long, val startedFlows: Int, val succeededFlow: Int, val failedFlow: Int) {
        override fun toString(): String {
            return "$stateMachineCount\t$freeMemory\t$totalMemory\t$startedFlows\t$succeededFlow\t$failedFlow"
        }
    }

    private data class Counter(val started: AtomicInteger, val success: AtomicInteger, val failed: AtomicInteger)
}
