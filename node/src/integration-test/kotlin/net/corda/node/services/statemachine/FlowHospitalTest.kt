package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContract.SingleOwnerState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.findCordapp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowHospitalTest {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))

    @Test(timeout = 300_000)
    fun `when double spend occurs, the flow is successfully deleted on the counterparty`() {
        driver(DriverParameters(cordappsForAllNodes = listOf(enclosedCordapp(), findCordapp("net.corda.testing.contracts")))) {
            val (charlieClient, aliceClient) = listOf(CHARLIE_NAME, ALICE_NAME)
                    .map {
                        startNode(providedName = it,
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
            }.isInstanceOf(DoubleSpendException::class.java)

            assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue()
            secondSubscription.unsubscribe()
            assertThat(aliceClient.stateMachinesSnapshot()).isEmpty()
        }
    }

    @Test(timeout = 300_000)
    fun `HospitalizeFlowException thrown`() {
        var observationCounter: Int = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        driver(
                DriverParameters(
                        startNodesInProcess = true,
                        cordappsForAllNodes = listOf(enclosedCordapp(), findCordapp("net.corda.testing.contracts"))
                )
        ) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::ThrowingHospitalisedExceptionFlow, HospitalizeFlowException::class.java)
                        .returnValue.getOrThrow(5.seconds)
            }
            assertEquals(1, observationCounter)
        }
    }

    @Test(timeout = 300_000)
    fun `Custom exception wrapping HospitalizeFlowException thrown`() {
        var observationCounter: Int = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        driver(
                DriverParameters(
                        startNodesInProcess = true,
                        cordappsForAllNodes = listOf(enclosedCordapp(), findCordapp("net.corda.testing.contracts"))
                )
        ) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::ThrowingHospitalisedExceptionFlow, WrappingHospitalizeFlowException::class.java)
                        .returnValue.getOrThrow(5.seconds)
            }
            assertEquals(1, observationCounter)
        }
    }

    @Test(timeout = 300_000)
    fun `Custom exception extending HospitalizeFlowException thrown`() {
        var observationCounter: Int = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        driver(
                DriverParameters(
                        startNodesInProcess = true,
                        cordappsForAllNodes = listOf(enclosedCordapp(), findCordapp("net.corda.testing.contracts"))
                )
        ) {
            // one node will be enough for this testing
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::ThrowingHospitalisedExceptionFlow, ExtendingHospitalizeFlowException::class.java)
                        .returnValue.getOrThrow(5.seconds)
            }
            assertEquals(1, observationCounter)
        }
    }

    @Test(timeout = 300_000)
    fun `HospitalizeFlowException cloaking an important exception thrown`() {
        var dischargedCounter = 0
        var observationCounter: Int = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ ->
            ++dischargedCounter
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        driver(
                DriverParameters(
                        startNodesInProcess = true,
                        cordappsForAllNodes = listOf(enclosedCordapp(), findCordapp("net.corda.testing.contracts"))
                )
        ) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)).getOrThrow()
            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::ThrowingHospitalisedExceptionFlow, CloakingHospitalizeFlowException::class.java)
                        .returnValue.getOrThrow(5.seconds)
            }
            assertEquals(0, observationCounter)
            // Since the flow will keep getting discharged from hospital dischargedCounter will be > 1.
            assertTrue(dischargedCounter > 0)
        }
    }

    @StartableByRPC
    class IssueFlow(val notary: Party) : FlowLogic<StateAndRef<SingleOwnerState>>() {

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
    class SpendFlow(private val stateAndRef: StateAndRef<SingleOwnerState>, private val newOwner: Party) : FlowLogic<Unit>() {

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
    class AcceptSpendFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SpendFlowWithCustomException(private val stateAndRef: StateAndRef<SingleOwnerState>, private val newOwner: Party) :
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
    class AcceptSpendFlowWithCustomException(private val otherSide: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            otherSide.receive<String>()
            otherSide.send("initial-response")

            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    class DoubleSpendException(message: String, cause: Throwable) : FlowException(message, cause)

    @StartableByRPC
    class ThrowingHospitalisedExceptionFlow(
            // Starting this Flow from an RPC client: if we pass in an encapsulated exception within another exception then the wrapping
            // exception, when deserialized, will get grounded into a CordaRuntimeException (this happens in ThrowableSerializer#fromProxy).
            private val hospitalizeFlowExceptionClass: Class<*>) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val throwable = hospitalizeFlowExceptionClass.newInstance()
            (throwable as? Throwable)?.let {
                throw it
            }
        }
    }

    class WrappingHospitalizeFlowException(cause: HospitalizeFlowException = HospitalizeFlowException()) : Exception(cause)

    class ExtendingHospitalizeFlowException : HospitalizeFlowException()

    class CloakingHospitalizeFlowException : HospitalizeFlowException() { // HospitalizeFlowException wrapping important exception
        init {
            // Wrapping an SQLException with "deadlock" as a message should lead the flow being handled
            // by StaffedFlowHospital#DeadlockNurse as well and therefore having the flow discharged
            // and not getting it for overnight observation.
            setCause(SQLException("deadlock"))
        }
    }
}