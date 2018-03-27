package net.corda.node.services.events

import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockito_kotlin.*
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServicesForResolution
import net.corda.core.utilities.days
import net.corda.node.internal.configureDatabase
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.testing.internal.doLookup
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        doReturn(openFuture<FlowStateMachine<*>>()).whenever(it).startFlow(any<FlowLogic<*>>(), any())
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
    protected val log = rigorousMock<Logger>().also {
        doReturn(false).whenever(it).isTraceEnabled
        doNothing().whenever(it).trace(any(), any<Any>())
        doNothing().whenever(it).info(any())
        doNothing().whenever(it).error(any(), any<Throwable>())
    }

    protected fun assertWaitingFor(ssr: ScheduledStateRef, total: Int = 1) {
        // The timeout is to make verify wait, which is necessary as we're racing the NSS thread i.e. we often get here just before the trace:
        verify(log, timeout(5000).times(total)).trace(NodeSchedulerService.schedulingAsNextFormat, ssr)
    }

    protected fun assertWaitingFor(event: Event, total: Int = 1) = assertWaitingFor(event.ssr, total)

    protected fun assertStarted(flowLogic: FlowLogic<*>) {
        // Like in assertWaitingFor, use timeout to make verify wait as we often race the call to startFlow:
        verify(flowStarter, timeout(5000)).startFlow(same(flowLogic)!!, any())
    }

    protected fun assertStarted(event: Event) = assertStarted(event.flowLogic)
}

class MockScheduledFlowRepository : ScheduledFlowRepository {
    private val map = HashMap<StateRef, ScheduledStateRef>()

    override fun getLatest(): Pair<StateRef, ScheduledStateRef>? {
        val latest = map.values.sortedBy { it.scheduledAt }.firstOrNull()

        return if (latest == null) {
            null
        } else {
            Pair(latest.ref, latest)
        }
    }

    override fun merge(value: ScheduledStateRef) {
        map.put(value.ref, value)
    }

    override fun delete(key: StateRef) {
        map.remove(key)
    }
}

class NodeSchedulerServiceTest : NodeSchedulerServiceTestBase() {
    private val database = rigorousMock<CordaPersistence>().also {
        doAnswer {
            val block: DatabaseTransaction.() -> Any? = uncheckedCast(it.arguments[0])
            rigorousMock<DatabaseTransaction>().block()
        }.whenever(it).transaction(any())
    }

    private val scheduler = NodeSchedulerService(
            testClock,
            database,
            flowStarter,
            servicesForResolution,
            serverThread = MoreExecutors.directExecutor(),
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
                doReturn(ScheduledActivity(logicRef, time)).whenever(it).nextScheduledActivity(same(stateRef)!!, any())
            }).whenever(it).data
        }
        flows[logicRef] = flowLogic
        scheduler.scheduleStateActivity(ssr)
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

class NodeSchedulerPersistenceTest : NodeSchedulerServiceTestBase() {
    private val databaseConfig: DatabaseConfig = DatabaseConfig()

    fun createScheduler(db: CordaPersistence): NodeSchedulerService {
        return NodeSchedulerService(
                testClock,
                db,
                flowStarter,
                servicesForResolution,
                serverThread = MoreExecutors.directExecutor(),
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
        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(SecureHash.randomSHA256(), 0)
            val ssr = ScheduledStateRef(stateRef, mark)
            repo.merge(ssr)

            val output = repo.getLatest()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, ssr)
        }
    }

    @Test
    fun `test that schedule is persisted`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val timeInTheFuture = mark + 1.days
        val stateRef = StateRef(SecureHash.zeroHash, 0)

        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())
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
        database.close()

        val flowLogic = rigorousMock<FlowLogic<*>>()
        val logicRef = rigorousMock<FlowLogicRef>()

        transactionStates[stateRef] = transactionStateMock(logicRef, timeInTheFuture)
        flows[logicRef] = flowLogic

        val newDatabase = configureDatabase(dataSourceProps, DatabaseConfig(), rigorousMock())
        val newScheduler = newDatabase.transaction {
            createScheduler(newDatabase)
        }
        testClock.advanceBy(1.days)
        assertStarted(flowLogic)

        newScheduler.join()
        newDatabase.close()
    }

    @Test
    fun `test that if schedule is updated then the flow is invoked on the correct schedule`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val timeInTheFuture = mark + 1.days
        val stateRef = StateRef(SecureHash.allOnesHash, 0)

        val ssr1 = ScheduledStateRef(stateRef, mark)
        val ssr2 = ScheduledStateRef(stateRef, timeInTheFuture)
        val logicRef = rigorousMock<FlowLogicRef>()
        val flowLogic = rigorousMock<FlowLogic<*>>()
        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())

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
        database.close()
    }
}

class PersistentNodeSchedulerRepositoryTest {
    private val databaseConfig: DatabaseConfig = DatabaseConfig()
    private val mark = Instant.now()

    @Test
    fun `test that earliest item is returned`() {
        val laterTime = mark + 1.days
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())

        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val laterStateRef = StateRef(SecureHash.randomSHA256(), 0)
            val laterSsr = ScheduledStateRef(laterStateRef, laterTime)
            repo.merge(laterSsr)

            val earlierStateRef = StateRef(SecureHash.randomSHA256(), 0)
            val earlierSsr = ScheduledStateRef(earlierStateRef, mark)
            repo.merge(earlierSsr)

            val output = repo.getLatest()
            assertEquals(output?.first, earlierStateRef)
            assertEquals(output?.second, earlierSsr)
        }
    }

    @Test
    fun `test that item is rescheduled`() {
        val laterTime = mark + 1.days
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(SecureHash.randomSHA256(), 0)
            val laterSsr = ScheduledStateRef(stateRef, laterTime)

            repo.merge(laterSsr)

            val updatedEarlierSsr = ScheduledStateRef(stateRef, mark)
            repo.merge(updatedEarlierSsr)

            val output = repo.getLatest()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, updatedEarlierSsr)

            repo.delete(output?.first!!)

            val nextOutput = repo.getLatest()
            assertNull(nextOutput)
        }
    }

    @Test
    fun `test that item is rescheduled 2`() {
        val laterTime = mark + 1.days
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, rigorousMock())
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(SecureHash.randomSHA256(), 0)
            val laterSsr = ScheduledStateRef(stateRef, laterTime)

            repo.merge(laterSsr)

            val updatedEarlierSsr = ScheduledStateRef(stateRef, mark)
            repo.merge(updatedEarlierSsr)

            val output = repo.getLatest()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, updatedEarlierSsr)
        }
    }
}