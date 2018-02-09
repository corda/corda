package net.corda.node.services.events

import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockito_kotlin.*
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.StateLoader
import net.corda.core.utilities.days
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.testing.internal.doLookup
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.TestClock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant

class NodeSchedulerServiceTest {
    private val mark = Instant.now()
    private val testClock = TestClock(rigorousMock<Clock>().also {
        doReturn(mark).whenever(it).instant()
    })
    private val database = rigorousMock<CordaPersistence>().also {
        doAnswer {
            val block: DatabaseTransaction.() -> Any? = uncheckedCast(it.arguments[0])
            rigorousMock<DatabaseTransaction>().block()
        }.whenever(it).transaction(any())
    }
    private val flowStarter = rigorousMock<FlowStarter>().also {
        doReturn(openFuture<FlowStateMachine<*>>()).whenever(it).startFlow(any<FlowLogic<*>>(), any())
    }
    private val flowsDraingMode = rigorousMock<NodePropertiesStore.FlowsDrainingModeOperations>().also {
        doReturn(false).whenever(it).isEnabled()
    }
    private val nodeProperties = rigorousMock<NodePropertiesStore>().also {
        doReturn(flowsDraingMode).whenever(it).flowsDrainingMode
    }
    private val transactionStates = mutableMapOf<StateRef, TransactionState<*>>()
    private val stateLoader = rigorousMock<StateLoader>().also {
        doLookup(transactionStates).whenever(it).loadState(any())
    }
    private val flows = mutableMapOf<FlowLogicRef, FlowLogic<*>>()
    private val flowLogicRefFactory = rigorousMock<FlowLogicRefFactory>().also {
        doLookup(flows).whenever(it).toFlowLogic(any())
    }
    private val log = rigorousMock<Logger>().also {
        doReturn(false).whenever(it).isTraceEnabled
        doNothing().whenever(it).trace(any(), any<Any>())
    }
    private val scheduler = NodeSchedulerService(
            testClock,
            database,
            flowStarter,
            stateLoader,
            serverThread = MoreExecutors.directExecutor(),
            flowLogicRefFactory = flowLogicRefFactory,
            nodeProperties = nodeProperties,
            drainingModePollPeriod = Duration.ofSeconds(5),
            log = log,
            scheduledStates = mutableMapOf()).apply { start() }
    @Rule
    @JvmField
    val tearDown = object : TestWatcher() {
        override fun succeeded(description: Description) {
            scheduler.join()
            verifyNoMoreInteractions(flowStarter)
        }
    }

    private class Event(time: Instant) {
        val stateRef = rigorousMock<StateRef>()
        val flowLogic = rigorousMock<FlowLogic<*>>()
        val ssr = ScheduledStateRef(stateRef, time)
    }

    private fun schedule(time: Instant) = Event(time).apply {
        val logicRef = rigorousMock<FlowLogicRef>()
        transactionStates[stateRef] = rigorousMock<TransactionState<SchedulableState>>().also {
            doReturn(rigorousMock<SchedulableState>().also {
                doReturn(ScheduledActivity(logicRef, time)).whenever(it).nextScheduledActivity(same(stateRef)!!, any())
            }).whenever(it).data
        }
        flows[logicRef] = flowLogic
        scheduler.scheduleStateActivity(ssr)
    }

    private fun assertWaitingFor(event: Event, total: Int = 1) {
        // The timeout is to make verify wait, which is necessary as we're racing the NSS thread i.e. we often get here just before the trace:
        verify(log, timeout(5000).times(total)).trace(NodeSchedulerService.schedulingAsNextFormat, event.ssr)
    }

    private fun assertStarted(event: Event) {
        // Like in assertWaitingFor, use timeout to make verify wait as we often race the call to startFlow:
        verify(flowStarter, timeout(5000)).startFlow(same(event.flowLogic)!!, any())
    }

    @Test
    fun `test activity due now`() {
        assertStarted(schedule(mark))
    }

    @Test
    fun `test activity due in the past`() {
        assertStarted(schedule(mark - 1.days))
    }

    @Test
    fun `test activity due in the future`() {
        val event = schedule(mark + 1.days)
        assertWaitingFor(event)
        testClock.advanceBy(1.days)
        assertStarted(event)
    }

    @Test
    fun `test activity due in the future and schedule another earlier`() {
        val event2 = schedule(mark + 2.days)
        val event1 = schedule(mark + 1.days)
        assertWaitingFor(event1)
        testClock.advanceBy(1.days)
        assertStarted(event1)
        assertWaitingFor(event2, 2)
        testClock.advanceBy(1.days)
        assertStarted(event2)
    }

    @Test
    fun `test activity due in the future and schedule another later`() {
        val event1 = schedule(mark + 1.days)
        val event2 = schedule(mark + 2.days)
        assertWaitingFor(event1)
        testClock.advanceBy(1.days)
        assertStarted(event1)
        assertWaitingFor(event2)
        testClock.advanceBy(1.days)
        assertStarted(event2)
    }

    @Test
    fun `test activity due in the future and schedule another for same time`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        assertWaitingFor(eventA)
        testClock.advanceBy(1.days)
        assertStarted(eventA)
        assertStarted(eventB)
    }

    @Test
    fun `test activity due in the future and schedule another for same time then unschedule second`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        scheduler.unscheduleStateActivity(eventB.stateRef)
        assertWaitingFor(eventA)
        testClock.advanceBy(1.days)
        assertStarted(eventA)
    }

    @Test
    fun `test activity due in the future and schedule another for same time then unschedule original`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        scheduler.unscheduleStateActivity(eventA.stateRef)
        assertWaitingFor(eventB)
        testClock.advanceBy(1.days)
        assertStarted(eventB)
    }

    @Test
    fun `test activity due in the future then unschedule`() {
        scheduler.unscheduleStateActivity(schedule(mark + 1.days).stateRef)
        testClock.advanceBy(1.days)
    }
}