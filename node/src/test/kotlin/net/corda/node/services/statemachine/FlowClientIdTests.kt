package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.concurrent.Semaphore
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlowWithClientId
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.lang.IllegalStateException
import java.sql.SQLTransientConnectionException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowClientIdTests {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork(
            cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, FINANCE_CONTRACTS_CORDAPP),
            servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )

        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))

    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        ResultFlow.hook = null
        ResultFlow.suspendableHook = null
        SingleThreadedStateMachineManager.beforeClientIDCheck = null
        SingleThreadedStateMachineManager.onClientIDNotFound = null
        SingleThreadedStateMachineManager.onCallingStartFlowInternal = null
        SingleThreadedStateMachineManager.onStartFlowInternalThrewAndAboutToRemove = null
    }

    @Test
    fun `no new flow starts if the client id provided pre exists`() {
        var counter = 0
        ResultFlow.hook = { counter++ }
        val clientId = UUID.randomUUID().toString()
        aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        Assert.assertEquals(1, counter)
    }

    @Test
    fun `flow's result is retrievable after flow's lifetime, when flow is started with a client id - different parameters are ignored`() {
        val clientId = UUID.randomUUID().toString()
        val handle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        val clientId0 = handle0.clientId
        val flowId0 = handle0.id
        val result0 = handle0.resultFuture.getOrThrow()

        val handle1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(10))
        val clientId1 = handle1.clientId
        val flowId1 = handle1.id
        val result1 = handle1.resultFuture.getOrThrow()

        Assert.assertEquals(clientId0, clientId1)
        Assert.assertEquals(flowId0, flowId1)
        Assert.assertEquals(result0, result1)
    }

    @Test
    fun `flow's result is available if reconnect after flow had retried from previous checkpoint, when flow is started with a client id`() {
        var firstRun = true
        ResultFlow.hook = {
            if (firstRun) {
                firstRun = false
                throw SQLTransientConnectionException("connection is not available")
            }
        }

        val clientId = UUID.randomUUID().toString()
        val result0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        val result1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        Assert.assertEquals(result0, result1)
    }

    @Test
    fun `flow's result is available if reconnect during flow's retrying from previous checkpoint, when flow is started with a client id`() {
        var firstRun = true
        val waitForSecondRequest = Semaphore(0)
        val waitUntilFlowHasRetried = Semaphore(0)
        ResultFlow.suspendableHook = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                if (firstRun) {
                    firstRun = false
                    throw SQLTransientConnectionException("connection is not available")
                } else {
                    waitUntilFlowHasRetried.release()
                    waitForSecondRequest.acquire()
                }
            }
        }

        var result1 = 0
        val clientId = UUID.randomUUID().toString()
        val handle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        waitUntilFlowHasRetried.acquire()
        val t = thread { result1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow() }

        Thread.sleep(1000)
        waitForSecondRequest.release()
        val result0 = handle0.resultFuture.getOrThrow()
        t.join()
        Assert.assertEquals(result0, result1)
    }

    @Ignore // this is to be unignored upon implementing CORDA-3681
    @Test
    fun `flow's exception is available after flow's lifetime if flow is started with a client id`() {
        ResultFlow.hook = { throw IllegalStateException() }
        val clientId = UUID.randomUUID().toString()

        assertFailsWith<IllegalStateException> {
            aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        }

        assertFailsWith<IllegalStateException> {
            aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
        }
    }

    @Test
    fun `flow's client id mapping gets removed upon request`() {
        val clientId = UUID.randomUUID().toString()
        var counter = 0
        ResultFlow.hook = { counter++ }
        val flowHandle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        flowHandle0.resultFuture.getOrThrow(20.seconds)
        val removed = aliceNode.smm.removeClientId(clientId)
        // On new request with clientId, after the same clientId was removed, a brand new flow will start with that clientId
        val flowHandle1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        flowHandle1.resultFuture.getOrThrow(20.seconds)

        assertTrue(removed)
        Assert.assertNotEquals(flowHandle0.id, flowHandle1.id)
        Assert.assertEquals(flowHandle0.clientId, flowHandle1.clientId)
        Assert.assertEquals(2, counter)
    }

    @Test
    fun `flow's client id mapping can only get removed once the flow gets removed`() {
        val clientId = UUID.randomUUID().toString()
        var tries = 0
        val maxTries = 10
        var failedRemovals = 0
        val semaphore = Semaphore(0)
        ResultFlow.suspendableHook = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                semaphore.acquire()
            }
        }
        val flowHandle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))

        var removed = false
        while (!removed) {
            removed = aliceNode.smm.removeClientId(clientId)
            if (!removed) ++failedRemovals
            ++tries
            if (tries >= maxTries) {
                semaphore.release()
                flowHandle0.resultFuture.getOrThrow(20.seconds)
            }
        }

        assertTrue(removed)
        Assert.assertEquals(maxTries, failedRemovals)
    }

    @Test
    fun `only one flow starts upon concurrent requests with the same client id`() {
        val requests = 2
        val counter = AtomicInteger(0)
        val resultsCounter = AtomicInteger(0)
        ResultFlow.hook = { counter.incrementAndGet() }
        //(aliceNode.smm as SingleThreadedStateMachineManager).concurrentRequests = true

        val clientId = UUID.randomUUID().toString()
        val threads = arrayOfNulls<Thread>(requests)
        for (i in 0 until requests) {
            threads[i] = Thread {
                val result = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5)).resultFuture.getOrThrow()
                resultsCounter.addAndGet(result)
            }
        }

        val semaphore = Semaphore(0)
        val allThreadsBlocked = Semaphore(0)
        SingleThreadedStateMachineManager.onClientIDNotFound = {
            // Make all threads wait after client id not found on clientIdsToFlowIds
            allThreadsBlocked.release()
            semaphore.acquire()
        }

        val beforeCount = AtomicInteger(0)
        SingleThreadedStateMachineManager.beforeClientIDCheck = {
            // Make all threads wait after client id not found on clientIdsToFlowIds
            beforeCount.incrementAndGet()
        }

        for (i in 0 until requests) {
            threads[i]!!.start()
        }

        allThreadsBlocked.acquire()
        for (i in 0 until requests) {
            semaphore.release()
        }

        for (thread in threads) {
            thread!!.join()
        }
        Assert.assertEquals(1, counter.get())
        Assert.assertEquals(2, beforeCount.get())
        Assert.assertEquals(10, resultsCounter.get())
    }


    @Test
    fun `on node start -running- flows with client id are hook-able`() {
        val clientId = UUID.randomUUID().toString()
        var noSecondFlowWasSpawned = 0
        var firstRun = true
        var firstFiber: Fiber<out Any?>? = null
        val flowIsRunning = Semaphore(0)
        val waitUntilFlowIsRunning = Semaphore(0)

        ResultFlow.suspendableHook = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                if (firstRun) {
                    firstFiber = Fiber.currentFiber()
                    firstRun = false
                }

                waitUntilFlowIsRunning.release()
                try {
                    flowIsRunning.acquire() // make flow wait here to impersonate a running flow
                } catch (e: InterruptedException) {
                    flowIsRunning.release()
                    throw e
                }

                noSecondFlowWasSpawned++
            }
        }

        val flowHandle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        waitUntilFlowIsRunning.acquire()
        aliceNode.internals.acceptableLiveFiberCountOnStop = 1
        val aliceNode = mockNet.restartNode(aliceNode)
        // Blow up the first fiber running our flow as it is leaked here, on normal node shutdown that fiber should be gone
        firstFiber!!.interrupt()

        waitUntilFlowIsRunning.acquire()
        // Re-hook a running flow
        val flowHandle1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        flowIsRunning.release()

        Assert.assertEquals(flowHandle0.id, flowHandle1.id)
        Assert.assertEquals(clientId, flowHandle1.clientId)
        Assert.assertEquals(5, flowHandle1.resultFuture.getOrThrow(20.seconds))
        Assert.assertEquals(1, noSecondFlowWasSpawned)
    }

    @Test
    fun `on node restart -paused- flows with client id are hook-able`() {
        val clientId = UUID.randomUUID().toString()
        var noSecondFlowWasSpawned = 0
        var firstRun = true
        var firstFiber: Fiber<out Any?>? = null
        val flowIsRunning = Semaphore(0)
        val waitUntilFlowIsRunning = Semaphore(0)

        ResultFlow.suspendableHook = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                if (firstRun) {
                    firstFiber = Fiber.currentFiber()
                    firstRun = false
                }

                waitUntilFlowIsRunning.release()
                try {
                    flowIsRunning.acquire() // make flow wait here to impersonate a running flow
                } catch (e: InterruptedException) {
                    flowIsRunning.release()
                    throw e
                }

                noSecondFlowWasSpawned++
            }
        }

        val flowHandle0 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        waitUntilFlowIsRunning.acquire()
        aliceNode.internals.acceptableLiveFiberCountOnStop = 1
        // Pause the flow on node restart
        val aliceNode = mockNet.restartNode(aliceNode,
            InternalMockNodeParameters(
                configOverrides = {
                    doReturn(StateMachineManager.StartMode.Safe).whenever(it).smmStartMode
                }
            ))
        // Blow up the first fiber running our flow as it is leaked here, on normal node shutdown that fiber should be gone
        firstFiber!!.interrupt()

        // Re-hook a paused flow
        val flowHandle1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))

        Assert.assertEquals(flowHandle0.id, flowHandle1.id)
        Assert.assertEquals(clientId, flowHandle1.clientId)
        aliceNode.smm.unPauseFlow(flowHandle1.id)
        Assert.assertEquals(5, flowHandle1.resultFuture.getOrThrow(20.seconds))
        Assert.assertEquals(1, noSecondFlowWasSpawned)
    }

    @Test
    fun `On 'startFlowInternal' throwing subsequent request with same client id does not get de-duplicated and starts a new flow`() {
        val clientId = UUID.randomUUID().toString()
        var firstRequest = true
        SingleThreadedStateMachineManager.onCallingStartFlowInternal = {
            if (firstRequest) {
                firstRequest = false
                throw IllegalStateException("Yet another one")
            }
        }
        var counter = 0
        ResultFlow.hook = { counter++ }

        assertFailsWith<IllegalStateException> {
            aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        }

        val flowHandle1 = aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        flowHandle1.resultFuture.getOrThrow(20.seconds)

        assertEquals(clientId, flowHandle1.clientId)
        assertEquals(1, counter)
    }

    @Test
    fun `On 'startFlowInternal' throwing subsequent request with same client hits the time window in which previous request was about to remove the client id mapping, will not start a new flow but will not hang either`() {
        val clientId = UUID.randomUUID().toString()
        var firstRequest = true
        SingleThreadedStateMachineManager.onCallingStartFlowInternal = {
            if (firstRequest) {
                firstRequest = false
                throw IllegalStateException("Yet another one")
            }
        }

        val wait = Semaphore(0)
        val waitForFirstRequest = Semaphore(0)
        SingleThreadedStateMachineManager.onStartFlowInternalThrewAndAboutToRemove = {
            waitForFirstRequest.release()
            wait.acquire()
            Thread.sleep(10000)
        }
        var counter = 0
        ResultFlow.hook = { counter++ }

        thread {
            assertFailsWith<IllegalStateException> {
                aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
            }
        }

        waitForFirstRequest.acquire()
        wait.release()
        assertFailsWith<IllegalStateException> {
            aliceNode.services.startFlowWithClientId(clientId, ResultFlow(5))
        }

        assertEquals(0, counter)
    }
}

internal class ResultFlow<A>(private val result: A): FlowLogic<A>() {
    companion object {
        var hook: (() -> Unit)? = null
        var suspendableHook: FlowLogic<Unit>? = null
    }

    @Suspendable
    override fun call(): A {
        hook?.invoke()
        suspendableHook?.let { subFlow(it) }
        return result
    }
}