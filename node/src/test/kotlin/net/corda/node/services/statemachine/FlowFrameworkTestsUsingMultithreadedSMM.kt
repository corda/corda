package net.corda.node.services.statemachine

import net.corda.client.rpc.notUsed
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.Party
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*

// FlowFrameworkTests but each node uses MultiThreadedStateMachineManager with one thread.
// TODO This should extend FlowFrameworkTests so that it runs all those tests, but there are test failures
class FlowFrameworkTestsUsingMultithreadedSMM {
    companion object {
        init {
            LogHelper.setLevel("+net.corda.flow")
        }
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var bobNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notaryIdentity: Party
    private val receivedSessionMessages = ArrayList<SessionTransfer>()

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin()
        )

        aliceNode = createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        bobNode = createNode(InternalMockNodeParameters(legalName = BOB_NAME))

        // Extract identities
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notaryIdentity = mockNet.defaultNotaryIdentity

        receivedSessionMessagesObservable().forEach { receivedSessionMessages += it }
    }

    private fun receivedSessionMessagesObservable(): Observable<SessionTransfer> {
        return mockNet.messagingNetwork.receivedMessages.toSessionTransfers()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        receivedSessionMessages.clear()
    }

    private fun createNode(parameters: InternalMockNodeParameters): TestStartedNode {
        return mockNet.createNode(parameters) {
            object : InternalMockNetwork.MockNode(it) {
                override fun makeStateMachineManager(): StateMachineManager {
                    val executor = MultiThreadedStateMachineExecutor(metricRegistry, 1)
                    return MultiThreadedStateMachineManager(
                            services,
                            checkpointStorage,
                            executor,
                            database,
                            newSecureRandom(),
                            busyNodeLatch,
                            cordappLoader.appClassLoader
                    )
                }
            }
        }
    }

    @Test
    fun `session init with unknown class is sent to the flow hospital, from where it's dropped`() {
        aliceNode.sendSessionMessage(InitialSessionMessage(SessionId(random63BitValue()), 0, "not.a.real.Class", 1, "", null), bob)
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).hasSize(1) // Only the session-init is expected as the session-reject is blocked by the flow hospital
        val medicalRecords = bobNode.smm.flowHospital.track().apply { updates.notUsed() }.snapshot
        assertThat(medicalRecords).hasSize(1)
        val sessionInitRecord = medicalRecords[0] as StaffedFlowHospital.MedicalRecord.SessionInit
        assertThat(sessionInitRecord.initiatorFlowClassName).isEqualTo("not.a.real.Class")
        bobNode.smm.flowHospital.dropSessionInit(sessionInitRecord.id)  // Drop the message which is processed as an error back to sender
        mockNet.runNetwork()
        assertThat(receivedSessionMessages).hasSize(2) // Now the session-reject is expected
        val lastMessage = receivedSessionMessages.last().message as ExistingSessionMessage
        assertThat((lastMessage.payload as RejectSessionMessage).message).isEqualTo("Don't know not.a.real.Class")
    }
}
