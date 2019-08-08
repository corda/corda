package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContract.SingleOwnerState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.*
import java.util.concurrent.ExecutionException

class FlowHospitalTest: IntegrationTest() {

    companion object {
        val databaseSchemas = IntegrationTestSchemas(CHARLIE_NAME, ALICE_NAME, DUMMY_NOTARY_NAME)
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    }

    @Test
    fun `when double spend occurs, the flow is successfully deleted on the counterparty`() {
        driver(DriverParameters(startNodesInProcess = false, inMemoryDB = false)) {
            val charlie = startNode(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(FlowHospitalTest.rpcUser)
            ).getOrThrow()
            val alice = startNode(
                    providedName = ALICE_NAME,
                    rpcUsers = listOf(FlowHospitalTest.rpcUser)
            ).getOrThrow()

            val charlieClient = CordaRPCClient(charlie.rpcAddress).start(FlowHospitalTest.rpcUser.username, FlowHospitalTest.rpcUser.password).proxy
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(FlowHospitalTest.rpcUser.username, FlowHospitalTest.rpcUser.password).proxy

            val aliceParty = aliceClient.nodeInfo().legalIdentities.first()

            val stateAndRef = charlieClient.startFlow(::IssueFlow, defaultNotaryIdentity).returnValue.get()

            // case 1: the notary exception is not caught
            charlieClient.startFlow(::SpendFlow, stateAndRef, aliceParty).returnValue.get()

            assertThatThrownBy {
                charlieClient.startFlow(::SpendFlow, stateAndRef, aliceParty).returnValue.get()
            }.isInstanceOf(ExecutionException::class.java)
                .hasCauseExactlyInstanceOf(NotaryException::class.java)

            Thread.sleep(2_000)

            assertThat(aliceClient.stateMachinesSnapshot()).isEmpty()

            // case 2: the notary exception is caught and wrapped in a custom exception
            val secondStateAndRef = charlieClient.startFlow(::IssueFlow, defaultNotaryIdentity).returnValue.get()

            charlieClient.startFlow(::SpendFlowWithCustomException, secondStateAndRef, aliceParty).returnValue.get()

            assertThatThrownBy {
                charlieClient.startFlow(::SpendFlowWithCustomException, secondStateAndRef, aliceParty).returnValue.get()
            }.isInstanceOf(ExecutionException::class.java)
                    .hasMessageContaining("double spend!")

            Thread.sleep(2_000)

            assertThat(aliceClient.stateMachinesSnapshot()).isEmpty()
        }
    }

    @StartableByRPC
    class IssueFlow(val notary: Party): FlowLogic<StateAndRef<SingleOwnerState>>() {

        @Suspendable
        override fun call(): StateAndRef<SingleOwnerState> {
            val partyAndReference = PartyAndReference(ourIdentity, OpaqueBytes.of(1))
            val txBuilder = DummyContract.generateInitial(Random().nextInt(), notary, partyAndReference)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val notarised = subFlow(FinalityFlow(signedTransaction, emptySet<FlowSession>()))
            val stateAndRef = notarised.coreTransaction.outRef<SingleOwnerState>(0)
            return stateAndRef
        }

    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlow(val stateAndRef: StateAndRef<SingleOwnerState>, val newOwner: Party): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            sessionWithCounterParty.sendAndReceive<String>("initial-message")
            subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty)))
        }

    }

    @InitiatedBy(SpendFlow::class)
    class AcceptSpendFlow(val otherSide: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }

    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlowWithCustomException(val stateAndRef: StateAndRef<SingleOwnerState>, val newOwner: Party): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val txBuilder = DummyContract.move(stateAndRef, newOwner)
            val signedTransaction = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val sessionWithCounterParty = initiateFlow(newOwner)
            sessionWithCounterParty.sendAndReceive<String>("initial-message")
            try {
                subFlow(FinalityFlow(signedTransaction, setOf(sessionWithCounterParty)))
            } catch (e: NotaryException) {
                throw DoubleSpendException("double spend!", e)
            }
        }

    }

    @InitiatedBy(SpendFlowWithCustomException::class)
    class AcceptSpendFlowWithCustomException(val otherSide: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }

    }

    class DoubleSpendException(message: String, cause: Throwable): FlowException(message, cause)

}