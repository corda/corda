package net.corda.coretests.node

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.MetricRegistry
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.getOrThrow
import com.codahale.metrics.Gauge
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceHubMetricsTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var nodeA: TestStartedNode

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
    fun `no metrics on stack can checkpoint`() {
        val result = nodeA.services.startFlow(TestFlow(false, "Result")).resultFuture.getOrThrow()
        val metric = nodeA.internals.metricRegistry.gauges["TestFlow.TestMetric"]

        assertNotNull(result)
        assertNotNull(metric)
        assertEquals("Result", result)
        assertEquals("Result", metric.value)
    }

    @Test(timeout=300_000)
    fun `metrics on stack cannot checkpoint`() {
        val exception = assertFailsWith<com.esotericsoftware.kryo.KryoException> {
            nodeA.services.startFlow(TestFlow(true, "Result")).resultFuture.getOrThrow()
        }
        assertTrue(exception.message!!.contains("com.codahale.metrics.MetricRegistry"))

    }

    @StartableByRPC
    @InitiatingFlow
    class TestFlow(private val getMetricsRegistryDirectly: Boolean, private val metric : String) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            registerMetricFromFlow(metric)
            val registry: MetricRegistry?
            if(getMetricsRegistryDirectly) {
                registry = serviceHub.getMetricsRegistry(MetricRegistry::class.java)
                registry.gauges["TestFlow.TestMetric"]?.value as String
            }
            sleep(Duration.ZERO)
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