package com.r3corda.node.services

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.r3corda.core.contracts.*
import com.r3corda.core.days
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.recordTransactions
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolLogicRef
import com.r3corda.core.protocols.ProtocolLogicRefFactory
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.node.services.events.NodeSchedulerService
import com.r3corda.node.services.persistence.PerFileCheckpointStorage
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.node.utilities.AffinityExecutor
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.testing.ALICE_KEY
import com.r3corda.testing.node.InMemoryMessagingNetwork
import com.r3corda.testing.node.MockKeyManagementService
import com.r3corda.testing.node.TestClock
import com.r3corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.nio.file.FileSystem
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class NodeSchedulerServiceTest : SingletonSerializeAsToken() {
    // Use an in memory file system for testing attachment storage.
    val fs: FileSystem = Jimfs.newFileSystem(Configuration.unix())

    val realClock: Clock = Clock.systemUTC()
    val stoppedClock = Clock.fixed(realClock.instant(), realClock.zone)
    val testClock = TestClock(stoppedClock)

    val schedulerGatedExecutor = AffinityExecutor.Gate(true)

    // We have to allow Java boxed primitives but Kotlin warns we shouldn't be using them
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    val factory = ProtocolLogicRefFactory(mapOf(Pair(TestProtocolLogic::class.java.name, setOf(NodeSchedulerServiceTest::class.java.name, Integer::class.java.name))))

    val services: MockServiceHubInternal

    lateinit var scheduler: NodeSchedulerService
    lateinit var smmExecutor: AffinityExecutor.ServiceAffinityExecutor
    lateinit var dataSource: Closeable
    lateinit var countDown: CountDownLatch
    lateinit var smmHasRemovedAllProtocols: CountDownLatch

    var calls: Int = 0

    /**
     * Have a reference to this test added to [ServiceHub] so that when the [ProtocolLogic] runs it can access the test instance.
     * The [TestState] is serialized and deserialized so attempting to use a transient field won't work, as it just
     * results in NPE.
     */
    interface TestReference {
        val testReference: NodeSchedulerServiceTest
    }

    init {
        val kms = MockKeyManagementService(ALICE_KEY)
        val mockMessagingService = InMemoryMessagingNetwork(false).InMemoryMessaging(false, InMemoryMessagingNetwork.Handle(0, "None"), persistenceTx = { it() })
        services = object : MockServiceHubInternal(overrideClock = testClock, keyManagement = kms, net = mockMessagingService), TestReference {
            override val testReference = this@NodeSchedulerServiceTest
        }
    }

    @Before
    fun setup() {
        countDown = CountDownLatch(1)
        smmHasRemovedAllProtocols = CountDownLatch(1)
        calls = 0
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        val database = dataSourceAndDatabase.second
        scheduler = NodeSchedulerService(database, services, factory, schedulerGatedExecutor)
        smmExecutor = AffinityExecutor.ServiceAffinityExecutor("test", 1)
        val mockSMM = StateMachineManager(services, listOf(services), PerFileCheckpointStorage(fs.getPath("checkpoints")), smmExecutor, database)
        mockSMM.changes.subscribe { change ->
            if (change.addOrRemove == AddOrRemove.REMOVE && mockSMM.allStateMachines.isEmpty()) {
                smmHasRemovedAllProtocols.countDown()
            }
        }
        mockSMM.start()
        services.smm = mockSMM
    }

    @After
    fun tearDown() {
        // We need to make sure the StateMachineManager is done before shutting down executors.
        if (services.smm.allStateMachines.isNotEmpty()) {
            smmHasRemovedAllProtocols.await()
        }
        smmExecutor.shutdown()
        smmExecutor.awaitTermination(60, TimeUnit.SECONDS)
        dataSource.close()
    }

    class TestState(val protocolLogicRef: ProtocolLogicRef, val instant: Instant) : LinearState, SchedulableState {
        override val participants: List<PublicKey>
            get() = throw UnsupportedOperationException()

        override val linearId = UniqueIdentifier()

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean = true

        override fun nextScheduledActivity(thisStateRef: StateRef, protocolLogicRefFactory: ProtocolLogicRefFactory): ScheduledActivity? = ScheduledActivity(protocolLogicRef, instant)

        override val contract: Contract
            get() = throw UnsupportedOperationException()
    }

    class TestProtocolLogic(val increment: Int = 1) : ProtocolLogic<Unit>() {
        override fun call() {
            (serviceHub as TestReference).testReference.calls += increment
            (serviceHub as TestReference).testReference.countDown.countDown()
        }
    }

    class Command : TypeOnlyCommandData()

    @Test
    fun `test activity due now`() {
        val time = stoppedClock.instant()
        scheduleTX(time)

        assertThat(calls).isEqualTo(0)
        schedulerGatedExecutor.waitAndRun()
        countDown.await()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `test activity due in the past`() {
        val time = stoppedClock.instant() - 1.days
        scheduleTX(time)

        assertThat(calls).isEqualTo(0)
        schedulerGatedExecutor.waitAndRun()
        countDown.await()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `test activity due in the future`() {
        val time = stoppedClock.instant() + 1.days
        scheduleTX(time)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)
        testClock.advanceBy(1.days)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
        countDown.await()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `test activity due in the future and schedule another earlier`() {
        val time = stoppedClock.instant() + 1.days
        scheduleTX(time + 1.days)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)
        scheduleTX(time, 3)

        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        testClock.advanceBy(1.days)
        countDown.await()
        assertThat(calls).isEqualTo(3)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    @Test
    fun `test activity due in the future and schedule another later`() {
        val time = stoppedClock.instant() + 1.days
        scheduleTX(time)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)
        scheduleTX(time + 1.days, 3)

        testClock.advanceBy(1.days)
        countDown.await()
        assertThat(calls).isEqualTo(1)
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        testClock.advanceBy(1.days)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    @Test
    fun `test activity due in the future and schedule another for same time`() {
        val time = stoppedClock.instant() + 1.days
        scheduleTX(time)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)
        scheduleTX(time, 3)

        testClock.advanceBy(1.days)
        countDown.await()
        assertThat(calls).isEqualTo(1)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    @Test
    fun `test activity due in the future and schedule another for same time then unschedule original`() {
        val time = stoppedClock.instant() + 1.days
        val scheduledRef1 = scheduleTX(time)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)
        scheduleTX(time, 3)

        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        scheduler.unscheduleStateActivity(scheduledRef1!!.ref)
        testClock.advanceBy(1.days)
        countDown.await()
        assertThat(calls).isEqualTo(3)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    @Test
    fun `test activity due in the future then unschedule`() {
        val scheduledRef1 = scheduleTX(stoppedClock.instant() + 1.days)

        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute { schedulerGatedExecutor.waitAndRun() }
        assertThat(calls).isEqualTo(0)

        scheduler.unscheduleStateActivity(scheduledRef1!!.ref)
        testClock.advanceBy(1.days)
        assertThat(calls).isEqualTo(0)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    private fun scheduleTX(instant: Instant, increment: Int = 1): ScheduledStateRef? {
        var scheduledRef: ScheduledStateRef? = null
        apply {
            val freshKey = services.keyManagementService.freshKey()
            val state = TestState(factory.create(TestProtocolLogic::class.java, increment), instant)
            val usefulTX = TransactionType.General.Builder(null).apply {
                addOutputState(state, DUMMY_NOTARY)
                addCommand(Command(), freshKey.public)
                signWith(freshKey)
            }.toSignedTransaction()
            val txHash = usefulTX.id

            services.recordTransactions(usefulTX)
            scheduledRef = ScheduledStateRef(StateRef(txHash, 0), state.instant)
            scheduler.scheduleStateActivity(scheduledRef!!)
        }
        return scheduledRef
    }
}
