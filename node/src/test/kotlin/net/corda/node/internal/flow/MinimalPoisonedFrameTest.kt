package net.corda.node.internal.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import kotlin.test.Test

class MinimalPoisonedFrameTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var node: StartedMockNode

    @Before
    fun setUp() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        cordappsForAllNodes = listOf(
                                enclosedCordapp()
                                //TestCordapp.findCordapp("net.corda.node.internal.flow")
                        )
                )
        )
        node = mockNet.createPartyNode()
        mockNet.startNodes()
    }

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test
    fun valueBelow2Pow50_flowCompletes() {
        val safe = (1L shl 50) - 1          // top 14 bits = 0
        assertEquals(16 * safe, run(safe))
    }

    @Test
    fun valueAbove2Pow50_flowNeverCompletes() {
        val poison = 1L shl 50              // top 14 bits = 1
        assertEquals(16 * poison, run(poison))
        // Before quasar fix in 0.9.3_r3
        //assertFailsWith<TimeoutException> { run(poison) }
    }

    private fun run(value: Long): Long {
        //Debug.getGlobalFlightRecorder().clear()
        val future = node.startFlow(MinimalParentFlow(value))
        mockNet.runNetwork()
        try {
            return future.getOrThrow(Duration.ofSeconds(30))
        } finally {
            //Debug.getGlobalFlightRecorder().dump(System.out)
        }
    }

    class MinimalParentFlow(private val value: Long) : FlowLogic<Long>() {
        @Suspendable
        override fun call(): Long = subFlow(MinimalSubFlow(value))   // LeaveSubFlow for MinimalSubFlow fires in here
    }

    class MinimalSubFlow(private val value: Long) : FlowLogic<Long>() {
        @Suspendable
        override fun call(): Long {
            val p0 = value;
            val p1 = value;
            val p2 = value;
            val p3 = value
            val p4 = value;
            val p5 = value;
            val p6 = value;
            val p7 = value
            val p8 = value;
            val p9 = value;
            val p10 = value;
            val p11 = value
            val p12 = value;
            val p13 = value;
            val p14 = value;
            val p15 = value
            sleep(Duration.ZERO)                 // checkpoint; the sixteen longs are saved into the fiber stack here
            return p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14 + p15
        }
    }
}

