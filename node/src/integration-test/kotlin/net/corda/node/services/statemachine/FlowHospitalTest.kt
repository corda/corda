package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContract.SingleOwnerState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FlowHospitalTest {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))

    @Test
    fun `when double spend occurs, the flow is successfully deleted on the counterparty`() {
        driver {
            val charlie = startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()

            val charlieClient = CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

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

            val secondSubscription = aliceClient.stateMachinesFeed().updates.subscribe{
                if (it is StateMachineUpdate.Removed && it.result.isFailure)
                    secondLatch.countDown()
            }

            assertThatThrownBy {
                charlieClient.startFlow(::SpendFlowWithCustomException, secondStateAndRef, aliceParty).returnValue.getOrThrow()
            }.isInstanceOf(DoubleSpendException::class.java)

            assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue()
            secondSubscription.unsubscribe()
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
            return notarised.coreTransaction.outRef(0)
        }

    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlow(private val stateAndRef: StateAndRef<SingleOwnerState>, private val newOwner: Party): FlowLogic<Unit>() {

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
    class AcceptSpendFlow(private val otherSide: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }

    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlowWithCustomException(private val stateAndRef: StateAndRef<SingleOwnerState>, private val newOwner: Party):
            FlowLogic<Unit>() {

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
    class AcceptSpendFlowWithCustomException(private val otherSide: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }

    }

    class DoubleSpendException(message: String, cause: Throwable): FlowException(message, cause)

}