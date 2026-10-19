package net.corda.node.services.events

import org.mockito.kotlin.*
import net.corda.core.contracts.*
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.randomHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.ServicesForResolution
import net.corda.core.utilities.days
import net.corda.core.utilities.minutes
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.configureDatabase
import net.corda.coretesting.internal.doLookup
import net.corda.coretesting.internal.rigorousMock
import net.corda.coretesting.internal.spectator
import net.corda.testing.internal.IS_S390X
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestClock
import org.junit.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.sql.SQLTransientConnectionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

private const val ASSERT_TIMEOUT_MS = 30_000L

open class NodeSchedulerServiceTestBase {
    protected class Event(time: Instant) {
        val stateRef = rigorousMock<StateRef>()
        val flowLogic = rigorousMock<FlowLogic<*>>()
        val ssr = ScheduledStateRef(stateRef, time)
    }

    // The clock is frozen at this instant, so it must survive a round trip through the database unchanged. Instant.now() has
    // nanosecond precision on Linux but the database stores less (H2 rounds to microseconds), and a stored time rounded up
    // past the frozen clock would never be reached.
    protected val mark: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
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
    protected val flows = ConcurrentHashMap<FlowLogicRef, FlowLogic<*>>()
    protected val flowLogicRefFactory = rigorousMock<FlowLogicRefFactory>().also {
        doLookup(flows).whenever(it).toFlowLogic(any())
    }

    protected val transactionStates = ConcurrentHashMap<StateRef, TransactionState<*>>()
    protected val servicesForResolution = rigorousMock<ServicesForResolution>().also {
        doLookup(transactionStates).whenever(it).loadState(any())
    }

    private val traces = Collections.synchronizedList(mutableListOf<ScheduledStateRef>())

    @Before
    fun resetTraces() {
        traces.clear()
    }

    protected val log = spectator<Logger>().also {
        doReturn(false).whenever(it).isTraceEnabled
        doAnswer {
            traces += it.getArgument<ScheduledStateRef>(1)
        }.whenever(it).trace(eq(NodeSchedulerService.schedulingAsNextFormat), any<Any>())
    }

    protected fun assertWaitingFor(ssr: ScheduledStateRef) {
        val endTime = System.currentTimeMillis() + ASSERT_TIMEOUT_MS
        while (System.currentTimeMillis() < endTime) {
            if (traces.lastOrNull() == ssr) return
            Thread.sleep(10)
        }
        fail("Was expecting to by waiting for $ssr")
    }

    protected fun assertWaitingFor(event: Event) = assertWaitingFor(event.ssr)

    protected fun assertStarted(flowLogic: FlowLogic<*>) {
        // Like in assertWaitingFor, use timeout to make verify wait as we often race the call to startFlow:
        verify(flowStarter, timeout(ASSERT_TIMEOUT_MS)).startFlow(argForWhich<ExternalEvent.ExternalStartFlowEvent<*>> { this.flowLogic == flowLogic })
    }

    protected fun assertStarted(event: Event) = assertStarted(event.flowLogic)
}

class MockScheduledFlowRepository : ScheduledFlowRepository {
    // Insertion ordered so that entries with the same scheduledAt are returned in a deterministic order (sortedBy is stable).
    private val map = LinkedHashMap<StateRef, ScheduledStateRef>()

    @Synchronized
    override fun getLatest(lookahead: Int): List<Pair<StateRef, ScheduledStateRef>> {
        return map.values.sortedBy { it.scheduledAt }.map { Pair(it.ref, it) }
    }

    @Synchronized
    override fun merge(value: ScheduledStateRef): Boolean {
        var result = false
        if (map.containsKey(value.ref)) {
            result = true
        }
        map[value.ref] = value
        return result
    }

    @Synchronized
    override fun delete(key: StateRef): Boolean {
        return map.remove(key) != null
    }
}

class NodeSchedulerServiceTest : NodeSchedulerServiceTestBase() {
    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })

    private val scheduler = NodeSchedulerService(
            testClock,
            database,
            flowStarter,
            servicesForResolution,
            flowLogicRefFactory,
            nodeProperties,
            Duration.ofSeconds(5),
            log,
            schedulerRepo = MockScheduledFlowRepository()
    ).apply { start() }

    // Runs before the rule below, and always, so that the scheduler never outlives the database it uses.
    @After
    fun tearDown() {
        try {
            scheduler.close()
        } finally {
            database.close()
        }
    }

    @Rule
    @JvmField
    val noMoreInteractions = object : TestWatcher() {
        override fun succeeded(description: Description) {
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

    @Test(timeout=300_000)
	fun `test activity due now`() {
        assertStarted(schedule(mark))
    }

    @Test(timeout=300_000)
	fun `test activity due in the past`() {
        assertStarted(schedule(mark - 1.days))
    }

    @Test(timeout=300_000)
	fun `test activity due in the future`() {
        val event = schedule(mark + 1.days)
        assertWaitingFor(event)
        testClock.advanceBy(1.days)
        assertStarted(event)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `test activity due in the future and schedule another for same time`() {
        val eventA = schedule(mark + 1.days)
        val eventB = schedule(mark + 1.days)
        testClock.advanceBy(1.days)
        assertStarted(eventA)
        assertStarted(eventB)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `test activity due in the future then unschedule`() {
        database.transaction {
            scheduler.unscheduleStateActivity(schedule(mark + 1.days).stateRef)
        }
        testClock.advanceBy(1.days)
    }
}

class NodeSchedulerServiceDatabaseFailureTest : NodeSchedulerServiceTestBase() {
    /** Fails the first [failures] calls to [getLatest] the way a database connection timeout would. */
    private class FailingScheduledFlowRepository(private var failures: Int) : ScheduledFlowRepository {
        private val delegate = MockScheduledFlowRepository()
        val failureCount = AtomicInteger()

        override fun getLatest(lookahead: Int): List<Pair<StateRef, ScheduledStateRef>> {
            if (failures-- > 0) {
                failureCount.incrementAndGet()
                throw SQLTransientConnectionException("Connection is not available, request timed out")
            }
            return delegate.getLatest(lookahead)
        }

        override fun merge(value: ScheduledStateRef) = delegate.merge(value)
        override fun delete(key: StateRef) = delegate.delete(key)
    }

    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test(timeout=300_000)
    fun `scheduler keeps running and logs recovery after repeatedly failing to load scheduled states from the database`() {
        val repo = FailingScheduledFlowRepository(failures = 3)
        val event = Event(mark)
        val logicRef = rigorousMock<FlowLogicRef>()
        transactionStates[event.stateRef] = rigorousMock<TransactionState<SchedulableState>>().also {
            doReturn(rigorousMock<SchedulableState>().also {
                doReturn(ScheduledActivity(logicRef, mark)).whenever(it).nextScheduledActivity(same(event.stateRef), any())
            }).whenever(it).data
        }
        flows[logicRef] = event.flowLogic
        repo.merge(event.ssr)

        val scheduler = NodeSchedulerService(
                testClock,
                database,
                flowStarter,
                servicesForResolution,
                flowLogicRefFactory,
                nodeProperties,
                Duration.ofSeconds(5),
                log,
                schedulerRepo = repo
        )
        try {
            scheduler.start()
            // The first load fails, so nothing is started until the scheduler retries once its error back-off elapses.
            // The clock is advanced repeatedly as the scheduler may not yet be waiting on it the first time it is advanced.
            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
            while (!mockingDetails(flowStarter).invocations.any { it.method.name == "startFlow" } && System.currentTimeMillis() < deadline) {
                testClock.advanceBy(1.minutes)
                Thread.sleep(50)
            }
            assertEquals(3, repo.failureCount.get())
            assertStarted(event)
            // Each failure is logged as an error with the running count of consecutive failures...
            (1..3).forEach { count ->
                verify(log).error(argThat<String> { contains("consecutive failures: $count)") }, any<Throwable>())
            }
            // ...and the recovery is logged once, reporting how many failures preceded it.
            verify(log, times(1)).info(argThat<String> { contains("recovered after 3 consecutive failure(s)") })
        } finally {
            scheduler.close()
        }
    }
}

class NodeSchedulerPersistenceTest : NodeSchedulerServiceTestBase() {
    private val databaseConfig: DatabaseConfig = DatabaseConfig()

    private fun createScheduler(db: CordaPersistence): NodeSchedulerService {
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

    private fun transactionStateMock(logicRef: FlowLogicRef, time: Instant): TransactionState<*> {
        return rigorousMock<TransactionState<SchedulableState>>().also {
            doReturn(rigorousMock<SchedulableState>().also {
                doReturn(ScheduledActivity(logicRef, time)).whenever(it).nextScheduledActivity(any(), any())
            }).whenever(it).data
        }
    }

    @Test(timeout=300_000)
	fun `test that correct item is returned`() {
        val dataSourceProps = MockServices.makeTestDataSourceProperties()
        val database = configureDatabase(dataSourceProps, databaseConfig, { null }, { null })
        database.transaction {
            val repo = PersistentScheduledFlowRepository(database)
            val stateRef = StateRef(DigestService.default.randomHash(), 0)
            val ssr = ScheduledStateRef(stateRef, mark)
            repo.merge(ssr)

            val output = repo.getLatest(5).firstOrNull()
            assertEquals(output?.first, stateRef)
            assertEquals(output?.second, ssr)
        }
    }

    @Test(timeout=300_000)
	fun `test that schedule is persisted`() {
        Assume.assumeFalse(IS_S390X)
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

            // Force the thread to shut down with operations waiting
            scheduler.cancelAndWait()

            val flowLogic = rigorousMock<FlowLogic<*>>()
            val logicRef = rigorousMock<FlowLogicRef>()

            transactionStates[stateRef] = transactionStateMock(logicRef, timeInTheFuture)
            flows[logicRef] = flowLogic

            val newScheduler = database.transaction {
                createScheduler(database)
            }
            testClock.advanceBy(1.days)
            assertStarted(flowLogic)

            newScheduler.close()

        }
    }

    @Test(timeout=300_000)
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
            // Both schedules are committed together, so the scheduler only ever sees the updated one.
            assertWaitingFor(ssr2)
            testClock.advanceBy(1.days)
            assertStarted(flowLogic)

            scheduler.close()
        }
    }
}
