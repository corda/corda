package net.corda.node.services.events

import net.corda.core.contracts.*
import net.corda.core.days
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.VaultService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.testing.ALICE_KEY
import net.corda.testing.DUMMY_CA
import net.corda.testing.DUMMY_NOTARY
import net.corda.node.services.MockServiceHubInternal
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.*
import net.corda.testing.getTestX509Name
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockKeyManagementService
import net.corda.testing.node.TestClock
import net.corda.testing.node.makeTestDataSourceProperties
import net.corda.testing.testNodeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class NodeSchedulerServiceTest : SingletonSerializeAsToken() {
    val realClock: Clock = Clock.systemUTC()
    val stoppedClock: Clock = Clock.fixed(realClock.instant(), realClock.zone)
    val testClock = TestClock(stoppedClock)

    val schedulerGatedExecutor = AffinityExecutor.Gate(true)

    lateinit var services: MockServiceHubInternal

    lateinit var scheduler: NodeSchedulerService
    lateinit var smmExecutor: AffinityExecutor.ServiceAffinityExecutor
    lateinit var database: CordaPersistence
    lateinit var countDown: CountDownLatch
    lateinit var smmHasRemovedAllFlows: CountDownLatch

    var calls: Int = 0

    /**
     * Have a reference to this test added to [ServiceHub] so that when the [FlowLogic] runs it can access the test instance.
     * The [TestState] is serialized and deserialized so attempting to use a transient field won't work, as it just
     * results in NPE.
     */
    interface TestReference {
        val testReference: NodeSchedulerServiceTest
    }

    @Before
    fun setup() {
        countDown = CountDownLatch(1)
        smmHasRemovedAllFlows = CountDownLatch(1)
        calls = 0
        val dataSourceProps = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProps)
        val identityService = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        val kms = MockKeyManagementService(identityService, ALICE_KEY)

        database.transaction {
            val nullIdentity = X500Name("cn=None")
            val mockMessagingService = InMemoryMessagingNetwork(false).InMemoryMessaging(
                    false,
                    InMemoryMessagingNetwork.PeerHandle(0, nullIdentity),
                    AffinityExecutor.ServiceAffinityExecutor("test", 1),
                    database)
            services = object : MockServiceHubInternal(
                    database,
                    testNodeConfiguration(Paths.get("."), getTestX509Name("Alice")),
                    overrideClock = testClock,
                    keyManagement = kms,
                    network = mockMessagingService), TestReference {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)
                override val testReference = this@NodeSchedulerServiceTest
            }
            scheduler = NodeSchedulerService(services, schedulerGatedExecutor)
            smmExecutor = AffinityExecutor.ServiceAffinityExecutor("test", 1)
            val mockSMM = StateMachineManager(services, DBCheckpointStorage(), smmExecutor, database)
            mockSMM.changes.subscribe { change ->
                if (change is StateMachineManager.Change.Removed && mockSMM.allStateMachines.isEmpty()) {
                    smmHasRemovedAllFlows.countDown()
                }
            }
            mockSMM.start()
            services.smm = mockSMM
            scheduler.start()
        }
    }

    @After
    fun tearDown() {
        // We need to make sure the StateMachineManager is done before shutting down executors.
        if (services.smm.allStateMachines.isNotEmpty()) {
            smmHasRemovedAllFlows.await()
        }
        smmExecutor.shutdown()
        smmExecutor.awaitTermination(60, TimeUnit.SECONDS)
        database.close()
    }

    class TestState(val flowLogicRef: FlowLogicRef, val instant: Instant) : LinearState, SchedulableState {
        override val participants: List<AbstractParty>
            get() = throw UnsupportedOperationException()

        override val linearId = UniqueIdentifier()

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean = true

        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            return ScheduledActivity(flowLogicRef, instant)
        }

        override val contract: Contract
            get() = throw UnsupportedOperationException()
    }

    class TestFlowLogic(val increment: Int = 1) : FlowLogic<Unit>() {
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
        database.transaction {
            scheduler.unscheduleStateActivity(scheduledRef1!!.ref)
        }
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

        database.transaction {
            scheduler.unscheduleStateActivity(scheduledRef1!!.ref)
        }
        testClock.advanceBy(1.days)
        assertThat(calls).isEqualTo(0)
        backgroundExecutor.shutdown()
        assertTrue(backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS))
    }

    private fun scheduleTX(instant: Instant, increment: Int = 1): ScheduledStateRef? {
        var scheduledRef: ScheduledStateRef? = null
        database.transaction {
            apply {
                val freshKey = services.keyManagementService.freshKey()
                val state = TestState(FlowLogicRefFactoryImpl.createForRPC(TestFlowLogic::class.java, increment), instant)
                val builder = TransactionType.General.Builder(null).apply {
                    addOutputState(state, DUMMY_NOTARY)
                    addCommand(Command(), freshKey)
                }
                val usefulTX = services.signInitialTransaction(builder, freshKey)
                val txHash = usefulTX.id

                services.recordTransactions(usefulTX)
                scheduledRef = ScheduledStateRef(StateRef(txHash, 0), state.instant)
                scheduler.scheduleStateActivity(scheduledRef!!)
            }
        }
        return scheduledRef
    }
}
