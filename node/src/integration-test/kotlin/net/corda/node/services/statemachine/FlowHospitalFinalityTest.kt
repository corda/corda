package net.corda.node.services.statemachine

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.NotaryException
import net.corda.core.internal.PlatformVersionSwitches.TWO_PHASE_FINALITY
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.FlowHospitalTest.CreateTransactionFlow
import net.corda.node.services.statemachine.FlowHospitalTest.IssueFlow
import net.corda.node.services.statemachine.FlowHospitalTest.SpendFlow
import net.corda.node.services.statemachine.FlowHospitalTest.SpendFlowWithCustomException
import net.corda.node.services.statemachine.FlowHospitalTest.SpendStateAndCatchDoubleSpendFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class FlowHospitalFinalityTest(private val platformVersion: Int) {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Int> = listOf(TWO_PHASE_FINALITY, TWO_PHASE_FINALITY - 1)
    }

    @Before
    fun before() {
        FlowHospitalTest.SpendStateAndCatchDoubleSpendResponderFlow.exceptionSeenInUserFlow = false
        FlowHospitalTest.CreateTransactionButDontFinalizeResponderFlow.exceptionSeenInUserFlow = false
    }

    @Test(timeout = 300_000)
    fun `when double spend occurs, the flow is successfully deleted on the counterparty`() {
        driver(DriverParameters()) {
            val (charlieClient, aliceClient) = listOf(CHARLIE_NAME, ALICE_NAME)
                    .map {
                        startNode(defaultParameters = NodeParameters(platformVersion = platformVersion),
                                providedName = it,
                                rpcUsers = listOf(rpcUser))
                    }
                    .transpose()
                    .getOrThrow()
                    .map {
                        CordaRPCClient(it.rpcAddress)
                                .start(rpcUser.username, rpcUser.password).proxy
                    }

            val aliceParty = aliceClient.nodeInfo().legalIdentities.first()

            val (firstLatch, secondLatch) = arrayOf(CountDownLatch(1), CountDownLatch(1))

            // case 1: the notary exception is not caught
            val stateAndRef = charlieClient.startFlow(::IssueFlow, defaultNotaryIdentity).returnValue.get()
            charlieClient.startFlow(::SpendFlow, stateAndRef, aliceParty).returnValue.get()

            val firstSubscription = aliceClient.stateMachinesFeed().updates.subscribe {
                if (it is StateMachineUpdate.Removed && it.result.isFailure)
                    firstLatch.countDown()
            }

            assertThatThrownBy {
                charlieClient.startFlow(::SpendFlow, stateAndRef, aliceParty).returnValue.getOrThrow()
            }.isInstanceOf(NotaryException::class.java)

            assertThat(firstLatch.await(5, TimeUnit.SECONDS)).isTrue()
            firstSubscription.unsubscribe()
            assertThat(aliceClient.stateMachinesSnapshot()).isEmpty()

            // case 2: the notary exception is caught and wrapped in a custom exception
            val secondStateAndRef = charlieClient.startFlow(::IssueFlow, defaultNotaryIdentity).returnValue.get()
            charlieClient.startFlow(::SpendFlowWithCustomException, secondStateAndRef, aliceParty).returnValue.get()

            val secondSubscription = aliceClient.stateMachinesFeed().updates.subscribe {
                if (it is StateMachineUpdate.Removed && it.result.isFailure)
                    secondLatch.countDown()
            }

            assertThatThrownBy {
                charlieClient.startFlow(::SpendFlowWithCustomException, secondStateAndRef, aliceParty).returnValue.getOrThrow()
            }.isInstanceOf(FlowHospitalTest.DoubleSpendException::class.java)

            assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue()
            secondSubscription.unsubscribe()
            assertThat(aliceClient.stateMachinesSnapshot()).isEmpty()
        }
    }

    @Ignore("Mis-behaving on startup for PV=12, incorrect results for PV=13")
    @Test(timeout = 300_000)
    fun `catching a notary error will cause a peer to fail with unexpected session end during ReceiveFinalityFlow that passes through user code`() {
        var dischargedCounter = 0
        StaffedFlowHospital.onFlowErrorPropagated.add { _, _ ->
            ++dischargedCounter
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(isDebug = false, startNodesInProcess = true)) {
            val nodeAHandle = startNode(defaultParameters = NodeParameters(platformVersion = platformVersion),
                    providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(defaultParameters = NodeParameters(platformVersion = platformVersion),
                    providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            nodeAHandle.rpc.let {
                val ref = it.startFlow(::CreateTransactionFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
            }
        }
        // 1 is the notary failing to notarise and propagating the error
        // 2 is the receiving flow failing due to the unexpected session end error
        assertEquals(1, dischargedCounter)
        assertTrue(FlowHospitalTest.SpendStateAndCatchDoubleSpendResponderFlow.exceptionSeenInUserFlow)
    }
}