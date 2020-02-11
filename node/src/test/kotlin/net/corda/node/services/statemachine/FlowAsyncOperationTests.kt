package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class FlowAsyncOperationTests {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    @Before
    fun setup() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()),
                notarySpecs = emptyList()
        )
        aliceNode = mockNet.createNode()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `operation errors are propagated correctly`() {
        val flow = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                await(ErroredExecute())
            }
        }

        assertFailsWith<ExecutionException> { aliceNode.services.startFlow(flow).resultFuture.get() }
    }

    private class ErroredExecute : FlowExternalAsyncOperation<Unit> {
        override fun execute(deduplicationId: String): CompletableFuture<Unit> {
            throw Exception()
        }
    }

    @Test(timeout=300_000)
	fun `operation result errors are propagated correctly`() {
        val flow = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                await(ErroredResult())
            }
        }

        assertFailsWith<SpecialException> { aliceNode.services.startFlow(flow).resultFuture.getOrThrow() }
    }

    @Test(timeout=300_000)
	fun `operation result errors are propagated correctly, and can be caught by the flow`() {
        val flow = object : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                try {
                    await(ErroredResult())
                } catch (e: SpecialException) {
                    // Suppress
                }
            }
        }

        aliceNode.services.startFlow(flow).resultFuture.get()
    }

    private class ErroredResult : FlowExternalAsyncOperation<Unit> {
        override fun execute(deduplicationId: String): CompletableFuture<Unit> {
            val future = CompletableFuture<Unit>()
            future.completeExceptionally(SpecialException())
            return future
        }
    }

    private class SpecialException : Exception()

    @Test(timeout = 30_000)
    fun `flows waiting on an async operation do not block the thread`() {
        // Kick off 10 flows that submit a task to the service and wait until completion
        val numFlows = 10
        val futures = (1..10).map {
            aliceNode.services.startFlow(TestFlowWithAsyncAction(false)).resultFuture
        }
        // Make sure all flows submitted a task to the service and are awaiting completion
        val service = aliceNode.services.cordaService(WorkerService::class.java)
        while (service.pendingCount != numFlows) Thread.sleep(100)
        // Complete all pending tasks. If async operations aren't handled as expected, and one of the previous flows is
        // actually blocking the thread, the following flow will deadlock and the test won't finish.
        aliceNode.services.startFlow(TestFlowWithAsyncAction(true)).resultFuture.get()
        // Make sure all waiting flows completed successfully
        futures.transpose().get()
    }

    private class TestFlowWithAsyncAction(val completeAllTasks: Boolean) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val scv = serviceHub.cordaService(WorkerService::class.java)
            await(WorkerServiceTask(completeAllTasks, scv))
        }
    }

    private class WorkerServiceTask(val completeAllTasks: Boolean, val service: WorkerService) : FlowExternalAsyncOperation<Unit> {
        override fun execute(deduplicationId: String): CompletableFuture<Unit> {
            return service.performTask(completeAllTasks)
        }
    }

    /** A dummy worker service that queues up tasks and allows clearing the entire task backlog. */
    @CordaService
    class WorkerService(val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
        private val pendingTasks = ConcurrentLinkedQueue<CompletableFuture<Unit>>()
        val pendingCount: Int get() = pendingTasks.count()

        fun performTask(completeAllTasks: Boolean): CompletableFuture<Unit> {
            val taskFuture = CompletableFuture<Unit>()
            pendingTasks.add(taskFuture)
            if (completeAllTasks) {
                synchronized(this) {
                    while (!pendingTasks.isEmpty()) {
                        val fut = pendingTasks.poll()!!
                        fut.complete(Unit)
                    }
                }
            }
            return taskFuture
        }
    }
}

