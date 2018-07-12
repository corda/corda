package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.TestClock
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class FlowStateMachineComparatorTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private object EmptyFlow : FlowLogic<Unit>() {
        override fun call() {}
    }

    private object DummyExecutor : Executor {
        override fun execute(command: Runnable?) {}
    }

    @Test
    fun `sort order of state machines is as expected`() {
        val scheduler = FiberExecutorScheduler("TestScheduler", DummyExecutor)
        val clock = TestClock(Clock.systemUTC())
        val sm1 = FlowStateMachineImpl<Unit>(StateMachineRunId(UUID.randomUUID()),
                scheduler = scheduler,
                logic = EmptyFlow, creationTime = clock.millis())
        clock.advanceBy(Duration.ofSeconds(1))
        val sm2 = FlowStateMachineImpl<Unit>(StateMachineRunId(UUID.randomUUID()),
                scheduler = scheduler,
                logic = EmptyFlow, creationTime = clock.millis())

        val comparator = FlowStateMachineComparator()
        Assert.assertEquals(-1, comparator.compare(sm1.task as Runnable, sm2.task as Runnable))
        Assert.assertEquals(0, comparator.compare(sm1.task as Runnable, sm1.task as Runnable))
        Assert.assertEquals(1, comparator.compare(sm2.task as Runnable, sm1.task as Runnable))
    }

    @Test
    fun `serialized flow maintains creation time`() {
        val scheduler = FiberExecutorScheduler("TestScheduler", DummyExecutor)
        val clock = TestClock(Clock.systemUTC())
        clock.advanceBy(Duration.ofDays(1)) // Move this away from "now" to check that it's not a coincidence.
        val sm1 = FlowStateMachineImpl<Unit>(StateMachineRunId(UUID.randomUUID()),
                scheduler = scheduler,
                logic = EmptyFlow, creationTime = clock.millis())
        val sm2 = sm1.serialize(context = SerializationDefaults.CHECKPOINT_CONTEXT).deserialize(context = SerializationDefaults.CHECKPOINT_CONTEXT)
        Fiber.unparkDeserialized(sm2, scheduler)

        val comparator = FlowStateMachineComparator()
        Assert.assertEquals(0, comparator.compare(sm1.task as Runnable, sm2.task as Runnable))
    }

    private class BlockerFlow : FlowLogic<Unit>() {
        val barrier = CountDownLatch(1)

        override fun call() {
            barrier.await()
        }
    }

    private class AddToListFlow(val list: MutableList<Long>) : FlowLogic<Unit>() {
        override fun call() {
            list += stateMachine.creationTime
        }
    }

    private class TestFlowStateMachine(override val creationTime: Long, override val logic: FlowLogic<Unit>, scheduler: FiberScheduler) : FlowStateMachine<Unit>, Fiber<Unit>(scheduler) {
        @Suspendable
        @Throws(InterruptedException::class)
        override fun run() {
            logic.stateMachine = this
            return logic.call()
        }

        override fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>, maySkipCheckpoint: Boolean): SUSPENDRETURN {
            throw NotImplementedError()
        }

        override fun initiateFlow(party: Party): FlowSession {
            throw NotImplementedError()
        }

        override fun checkFlowPermission(permissionName: String, extraAuditData: Map<String, String>) {
            throw NotImplementedError()
        }

        override fun recordAuditEvent(eventType: String, comment: String, extraAuditData: Map<String, String>) {
            throw NotImplementedError()
        }

        override fun <SUBFLOWRETURN> subFlow(subFlow: FlowLogic<SUBFLOWRETURN>): SUBFLOWRETURN {
            throw NotImplementedError()
        }

        override fun flowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot? {
            throw NotImplementedError()
        }

        override fun persistFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>) {
            throw NotImplementedError()
        }

        override val serviceHub: ServiceHub
            get() = throw NotImplementedError()
        override val logger: Logger
            get() = throw NotImplementedError()
        override val id: StateMachineRunId
            get() = throw NotImplementedError()
        override val resultFuture: CordaFuture<Unit>
            get() = throw NotImplementedError()
        override val context: InvocationContext
            get() = throw NotImplementedError()
        override val ourIdentity: Party
            get() = throw NotImplementedError()
        override val ourSenderUUID: String?
            get() = throw NotImplementedError()
    }

    @Test
    fun `test executor`() {
        val executor = MultiThreadedStateMachineExecutor(1)
        val scheduler = FiberExecutorScheduler("TestScheduler", executor)
        val clock = TestClock(Clock.systemUTC())
        val blockerLogic = BlockerFlow()
        val list: MutableList<Long> = Collections.synchronizedList(arrayListOf())
        val blocker = TestFlowStateMachine(scheduler = scheduler,
                logic = blockerLogic, creationTime = clock.millis())
        val sm1 = TestFlowStateMachine(scheduler = scheduler,
                logic = AddToListFlow(list), creationTime = clock.millis())
        clock.advanceBy(Duration.ofSeconds(1))
        val sm2 = TestFlowStateMachine(scheduler = scheduler,
                logic = AddToListFlow(list), creationTime = clock.millis())
        blocker.start()
        sm2.start()
        sm1.start()
        blockerLogic.barrier.countDown()
        Thread.sleep(1000)
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.HOURS)
        assertEquals(2, list.size)
        assertEquals(sm1.creationTime, list[0])
        assertEquals(sm2.creationTime, list[1])
    }
}