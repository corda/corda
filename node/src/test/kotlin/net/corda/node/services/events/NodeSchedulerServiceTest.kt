package net.corda.node.services.events

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argForWhich
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.timeout
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import junit.framework.Assert.fail
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledActivity
import net.corda.core.contracts.ScheduledStateRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.ServicesForResolution
import net.corda.core.utilities.days
import net.corda.node.internal.configureDatabase
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.doLookup
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.spectator
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

open class NodeSchedulerServiceTestBase {
    protected class Event(time: Instant) {
        val stateRef = rigorousMock<StateRef>()
        val flowLogic = rigorousMock<FlowLogic<*>>()
        val ssr = ScheduledStateRef(stateRef, time)
    }

    protected val mark = Instant.now()!!
    protected val testClock = TestClock(rigorousMock<Clock>().also {
        doReturn(mark).whenever(it).instant()
    })
    protected val flowStarter = rigorousMock<FlowStarter>().also {
        doAnswer {
            val dedupe: DeduplicationHandler = it.getArgument(2)
            dedupe.insideDatabaseTransaction()
            dedupe.afterDatabaseTransaction()
            openFuture<FlowStateMachine<*>>()
        }.whenever(it).startFlow(any<ExternalEvent.ExternalStartFlowEvent<*>>())
    }
    private val flowsDraingMode = rigorousMock<NodePropertiesStore.FlowsDrainingModeOperations>().also {
        doReturn(false).whenever(it).isEnabled()
    }
    protected val nodeProperties = rigorousMock<NodePropertiesStore>().also {
        doReturn(flowsDraingMode).whenever(it).flowsDrainingMode
    }
    protected val flows = mutableMapOf<FlowLogicRef, FlowLogic<*>>()
    protected val flowLogicRefFactory = rigorousMock<FlowLogicRefFactory>().also {
        doLookup(flows).whenever(it).toFlowLogic(any())
    }

    protected val transactionStates = mutableMapOf<StateRef, TransactionState<*>>()
    protected val servicesForResolution = rigorousMock<ServicesForResolution>().also {
        doLookup(transactionStates).whenever(it).loadState(any())
    }

    protected val traces = Collections.synchronizedList(mutableListOf<ScheduledStateRef>())

    @Before
    fun resetTraces() {
        traces.clear()
    }

    protected val log = spectator<Logger>().also {
        doReturn(false).whenever(it).isTraceEnabled
        doAnswer {
            traces += it.getArgument<ScheduledStateRef>(1)
        }.whenever(it).trace(eq(NodeSchedulerService.schedulingAsNextFormat), any<Object>())
    }

    protected fun assertWaitingFor(ssr: ScheduledStateRef) {
        val endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        while (System.currentTimeMillis() < endTime) {
            if (traces.lastOrNull() == ssr) return
        }
        fail("Was expecting to by waiting for $ssr")
    }

    protected fun assertWaitingFor(event: Event) = assertWaitingFor(event.ssr)

    protected fun assertStarted(flowLogic: FlowLogic<*>) {
        // Like in assertWaitingFor, use timeout to make verify wait as we often race the call to startFlow:
        verify(flowStarter, timeout(5000)).startFlow(argForWhich<ExternalEvent.ExternalStartFlowEvent<*>> { this.flowLogic == flowLogic })
    }

    protected fun assertStarted(event: Event) = assertStarted(event.flowLogic)
}

class MockScheduledFlowRepository : ScheduledFlowRepository {
    private val map = ConcurrentHashMap<StateRef, ScheduledStateRef>()

    override fun getLatest(lookahead: Int): List<Pair<StateRef, ScheduledStateRef>> {
        return map.values.sortedBy { it.scheduledAt }.map { Pair(it.ref, it) }
    }

    override fun merge(value: ScheduledStateRef): Boolean {
        var result = false
        if (map.containsKey(value.ref)) {
            result = true
        }
        map[value.ref] = value
        return result
    }

    override fun delete(key: StateRef): Boolean {
        if (map.containsKey(key)) {
            map.remove(key)
            return true
        }
        return false
    }
}

class NodeSchedulerServiceTest : NodeSchedulerServiceTestBase() {
    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })

    @After
    fun closeDatabase() {
        database.close()
    }

    private val scheduler = NodeSchedulerService(
            testClock,
            database,
            flowStarter,
            servicesForResolution,
            flowLogicRefFactory = flowLogicRefFactory,
            nodeProperties = nodeProperties,
            drainingModePollPeriod = Duration.ofSeconds(5),
            log = log,
            schedulerRepo = MockScheduledFlowRepository()
    ).apply { start() }

    @Rule
    @JvmField
    val tearDown = object : TestWatcher() {
        override fun succeeded(description: Description) {
            scheduler.join()
            verifyNoMoreInteractions(flowStarter)
        }
    }

    private fun schedule(time: Instant) = Event(time).apply {
        val logicRef = rigorousMock<FlowLogicRef>()
        transactionStates[stateRef] = rigorousMock<TransactionState<SchedulableState>>().also {
            doReturn(rigorousMock<SchedulableState>().also {
                doReturn(ScheduledActivity(logicRef, time)).whenever(it).nextScheduledActivity(same(stateRef), any())
            }).whenever(it).data
        }
        flows[logicRef] = flowLogic
        database.transaction {
            scheduler.scheduleStateActivity(ssr)
        }
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
        assertWaitingFor(event2)
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
        testClock.advanceBy(1.days)
        assertStarted(eventA)
        assertStarted(eventB)
    }

    @Test
    fun `test activity due in the future and schedule another for same time then unschedule second`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        database.transaction {
            scheduler.unscheduleStateActivity(eventB.stateRef)
        }
        assertWaitingFor(eventA)
        testClock.advanceBy(1.days)
        assertStarted(eventA)
    }

    @Test
    fun `test activity due in the future and schedule another for same time then unschedule original`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        database.transaction {
            scheduler.unscheduleStateActivity(eventA.stateRef)
        }
        assertWaitingFor(eventB)
        testClock.advanceBy(1.days)
        assertStarted(eventB)
    }

    @Test
    fun `test activity due in the future then unschedule`() {
        database.transaction {
            scheduler.unscheduleStateActivity(schedule(mark + 1.days).stateRef)
        }
        testClock.advanceBy(1.days)
    }
}

class NodeSchedulerPersistenceTest : NodeSchedulerServiceTestBase() {
    private val databaseConfig: DatabaseConfig = DatabaseConfig()

    fun createScheduler(db: CordaPersistence): NodeSchedulerService {
        return NodeSchedulerService(
                testClock,
                db,
                flowStarter,
                servicesForResolution,
                flowLogicRefFactory = flowLogicRefFactory,
                nodeProperties = nodeProperties,
                drainingModePollPeriod = Duration.ofSeconds(5),
                log = log).apply { start() }
    }

    fun transactionStateMock(logicRef: FlowLogicRef, time: Instant): TransactionState<*> {
        return rigorousMock<TransactionState<SchedulableState>>().also {
            doReturn(rigorousMock<SchedulableState>().also {
                doReturn(ScheduledActivity(logicRef, time)).whenever(it).nextScheduledActivity(any(), any())
            }).whenever(it).data
        }
    }

    @Test
    fun `test that correct item is returned`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, { null }, { null })
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(SecureHash.randomSHA256(), 0)
            val ssr = ScheduledStateRef(stateRef, mark)
            repo.merge(ssr)

            val output = repo.getLatest(5).firstOrNull()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, ssr)
        }
    }

    @Test
    fun `test that schedule is persisted`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val timeInTheFuture = mark + 1.days
        val stateRef = StateRef(SecureHash.zeroHash, 0)

        configureDatabase(dataSourceProps, databaseConfig, { null }, { null }).use { database ->
            val scheduler = database.transaction {
                createScheduler(database)
            }

            val ssr1 = ScheduledStateRef(stateRef, timeInTheFuture)
            database.transaction {
                scheduler.scheduleStateActivity(ssr1)
            }
            // XXX: For some reason without the commit the db closes without writing the transactions
            database.dataSource.connection.commit()

            // Force the thread to shut down with operations waiting
            scheduler.cancelAndWait()
        }

        val flowLogic = rigorousMock<FlowLogic<*>>()
        val logicRef = rigorousMock<FlowLogicRef>()

        transactionStates[stateRef] = transactionStateMock(logicRef, timeInTheFuture)
        flows[logicRef] = flowLogic

        configureDatabase(dataSourceProps, DatabaseConfig(), { null }, { null }).use { database ->
            val newScheduler = database.transaction {
                createScheduler(database)
            }
            testClock.advanceBy(1.days)
            assertStarted(flowLogic)

            newScheduler.join()
        }
    }

    @Ignore("Temporarily")
    @Test
    fun `test that if schedule is updated then the flow is invoked on the correct schedule`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val timeInTheFuture = mark + 1.days
        val stateRef = StateRef(SecureHash.allOnesHash, 0)

        val ssr1 = ScheduledStateRef(stateRef, mark)
        val ssr2 = ScheduledStateRef(stateRef, timeInTheFuture)
        val logicRef = rigorousMock<FlowLogicRef>()
        val flowLogic = rigorousMock<FlowLogic<*>>()

        configureDatabase(dataSourceProps, databaseConfig, { null }, { null }).use { database ->
            val scheduler = database.transaction {
                createScheduler(database)
            }

            transactionStates[stateRef] = transactionStateMock(logicRef, timeInTheFuture)
            flows[logicRef] = flowLogic

            database.transaction {
                scheduler.scheduleStateActivity(ssr1)
                session.flush()
                scheduler.scheduleStateActivity(ssr2)
            }
            assertWaitingFor(ssr1)
            testClock.advanceBy(1.days)
            assertStarted(flowLogic)

            scheduler.join()
        }
    }
}
