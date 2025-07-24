package net.corda.coretests.node

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.getOrThrow
import com.codahale.metrics.Gauge
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServiceHubMetricsTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var nodeA: TestStartedNode

    interface ExternalLatch {
        val latch: CountDownLatch
    }

    object Latch1 : ExternalLatch {
        override val latch = CountDownLatch(0)
    }
    object Latch2 : ExternalLatch {
        override val latch = CountDownLatch(1)
    }
    @Before
    fun start() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()),
                networkSendManuallyPumped = false,
                threadPerNode = true)

        nodeA = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))

        mockNet.startNodes()
    }

    @After
    fun cleanup() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
    fun `Can register metrics from a flow`() {
        val result = nodeA.services.startFlow(TestFlow(Latch1, "Result")).resultFuture.getOrThrow()
        val metric = nodeA.internals.metricRegistry.gauges["TestFlow.TestMetric"]

        assertNotNull(result)
        assertNotNull(metric)
        assertEquals("Result", result)
        assertEquals("Result", metric.value)
    }

    @Test(timeout=300_000)
    fun `Can checkpoint`() {
        nodeA.services.startFlow(TestFlow(Latch2, "Result2"))
        nodeA = mockNet.restartNode(nodeA, InternalMockNodeParameters(legalName = ALICE_NAME))
        Latch2.latch.countDown()


        eventuallyAssert {
            val metric = nodeA.internals.metricRegistry.gauges["TestFlow.TestMetric"]
            assertNotNull(metric)
            assertEquals("Result2", metric.value)
        }
    }

    class ExternalOperation(val externalLatch: ExternalLatch) : FlowExternalAsyncOperation<Unit> {
        override fun execute(deduplicationId: String): CompletableFuture<Unit> {
            return externalLatch.latch.asCompletableFuture()
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class TestFlow(private val externalLatch: ExternalLatch, private val metric : String) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            await(ExternalOperation(externalLatch))// Wait for the latch to be released
            registerMetricFromFlow(metric)
            return getMetricFromFlow()
        }

        private fun registerMetricFromFlow(value: String) {
            serviceHub.getMetricsRegistry(MetricRegistry::class.java).register(
                MetricRegistry.name("TestFlow", "TestMetric"),
                Gauge { value }
            )
        }

        private fun getMetricFromFlow():String {
            return serviceHub.getMetricsRegistry(MetricRegistry::class.java).gauges["TestFlow.TestMetric"]?.value as String
        }
    }
}

private fun eventuallyAssert(
        timeout: Duration = Duration.ofSeconds(30),
        pollInterval: Duration = Duration.ofMillis(100),
        assertions: () -> Unit,
) {
    val deadline = Instant.now().plus(timeout)
    var lastError: Throwable? = null

    while (Instant.now().isBefore(deadline)) {
        try {
            assertions()
            return  // Success
        } catch (e: Throwable) {
            lastError = e
            Thread.sleep(pollInterval.toMillis())
        }
    }

    // If we get here, we've timed out - throw the last error
    throw AssertionError("Assertions failed after ${timeout.seconds} seconds", lastError)
}
fun CountDownLatch.asCompletableFuture(): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    Thread {
        try {
            this.await()
            future.complete(Unit)
        } catch (e: InterruptedException) {
            future.completeExceptionally(e)
            Thread.currentThread().interrupt()
        }
    }.start()
    return future
}
