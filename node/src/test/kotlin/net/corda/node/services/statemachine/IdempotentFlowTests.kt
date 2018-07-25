package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.IdempotentFlow
import net.corda.core.internal.TimedFlow
import net.corda.core.internal.packageName
import net.corda.core.utilities.seconds
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.testing.node.internal.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class IdempotentFlowTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var nodeA: TestStartedNode
    private lateinit var nodeB: TestStartedNode

    companion object {
        val executionCounter = AtomicInteger(0)
        val subFlowExecutionCounter = AtomicInteger(0)
        val suspendedOnce = AtomicBoolean(false)
    }

    @Before
    fun start() {
        mockNet = InternalMockNetwork(threadPerNode = true, cordappsForAllNodes = cordappsForPackages(this.javaClass.packageName))
        nodeA = mockNet.createNode(InternalMockNodeParameters(
                legalName = CordaX500Name("Alice", "AliceCorp", "GB"),
                configOverrides = {
                    conf: NodeConfiguration ->
                    val retryConfig = FlowTimeoutConfiguration(1.seconds, 3, 1.0)
                    doReturn(retryConfig).whenever(conf).flowTimeout
                }
        ))
        nodeB = mockNet.createNode()
        mockNet.startNodes()
        executionCounter.set(0)
        subFlowExecutionCounter.set(0)
        suspendedOnce.set(false)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `restarting idempotent flow does not replay any part of its parent flow`() {
        nodeA.services.startFlow(SideEffectFlow()).resultFuture.get()
        assertEquals(1, executionCounter.get())
        assertEquals(2, subFlowExecutionCounter.get())
    }

    @InitiatingFlow
    private class SideEffectFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            executionCounter.incrementAndGet() // This shouldn't be replayed when the TimedSubFlow restarts.
            subFlow(TimedSubflow()) // Checkpoint should be taken before invoking the sub-flow.
        }
    }

    private class TimedSubflow : FlowLogic<Unit>(), TimedFlow {
        @Suspendable
        override fun call() {
            subFlowExecutionCounter.incrementAndGet() // No checkpoint should be taken before invoking IdempotentSubFlow,
                                                      // so this should be replayed when TimedSubFlow restarts.
            subFlow(IdempotentSubFlow()) // Checkpoint shouldn't be taken before invoking the sub-flow.
        }
    }

    private class IdempotentSubFlow : FlowLogic<Unit>(), IdempotentFlow {
        @Suspendable
        override fun call() {
            if (!IdempotentFlowTests.suspendedOnce.getAndSet(true))
                waitForLedgerCommit(SecureHash.zeroHash)
        }
    }
}