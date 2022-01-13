package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContract.SingleOwnerState
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.findCordapp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
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

    @Before
    fun before() {
        SpendStateAndCatchDoubleSpendResponderFlow.exceptionSeenInUserFlow = false
        CreateTransactionButDontFinalizeResponderFlow.exceptionSeenInUserFlow = false
    }

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
        var observationCounter = 0
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

    @Test(timeout = 300_000)
    fun `catching a notary error will cause a peer to fail with unexpected session end during ReceiveFinalityFlow that passes through user code`() {
        var dischargedCounter = 0
        StaffedFlowHospital.onFlowErrorPropagated.add { _, _ ->
            ++dischargedCounter
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(isDebug = false, startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            nodeAHandle.rpc.let {
                val ref = it.startFlow(::CreateTransactionFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
            }
        }
        // 1 is the notary failing to notarise and propagating the error
        // 2 is the receiving flow failing due to the unexpected session end error
        assertEquals(2, dischargedCounter)
        assertTrue(SpendStateAndCatchDoubleSpendResponderFlow.exceptionSeenInUserFlow)
    }

    @Test(timeout = 300_000)
    fun `unexpected session end errors outside of ReceiveFinalityFlow are not handled`() {
        var dischargedCounter = 0
        var observationCounter = 0
        StaffedFlowHospital.onFlowErrorPropagated.add { _, _ ->
            ++dischargedCounter
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(isDebug = false, startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeCHandle = startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(user)).getOrThrow()
            nodeAHandle.rpc.let {
                val ref = it.startFlow(::CreateTransactionFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
                val ref2 = it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
                val ref3 = it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeCHandle.nodeInfo.singleIdentity(), ref2).returnValue.getOrThrow(20.seconds)
                it.startFlow(::CreateTransactionButDontFinalizeFlow, nodeBHandle.nodeInfo.singleIdentity(), ref3).returnValue.getOrThrow(20.seconds)
            }
        }
        assertEquals(0, dischargedCounter)
        assertEquals(1, observationCounter)
        assertTrue(CreateTransactionButDontFinalizeResponderFlow.exceptionSeenInUserFlow)
    }

    @Test(timeout = 300_000)
    fun `unexpected session end errors within ReceiveFinalityFlow can be caught and the flow can end gracefully`() {
        var dischargedCounter = 0
        var observationCounter = 0
        StaffedFlowHospital.onFlowErrorPropagated.add { _, _ ->
            ++dischargedCounter
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(isDebug = false, startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            nodeAHandle.rpc.let {
                val ref = it.startFlow(::CreateTransactionFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref).returnValue.getOrThrow(20.seconds)
                it.startFlow(::SpendStateAndCatchDoubleSpendFlow, nodeBHandle.nodeInfo.singleIdentity(), ref, true).returnValue.getOrThrow(20.seconds)
            }
        }
        // 1 is the notary failing to notarise and propagating the error
        assertEquals(1, dischargedCounter)
        assertEquals(0, observationCounter)
        assertTrue(SpendStateAndCatchDoubleSpendResponderFlow.exceptionSeenInUserFlow)
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

    @InitiatingFlow
    @StartableByRPC
    class CreateTransactionFlow(private val peer: Party) : FlowLogic<StateAndRef<DummyState>>() {
        @Suspendable
        override fun call(): StateAndRef<DummyState> {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(DummyState(participants = listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey, peer.owningKey))
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(peer)
            val ftx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            subFlow(FinalityFlow(ftx, session))

            return ftx.coreTransaction.outRef(0)
        }
    }

    @InitiatedBy(CreateTransactionFlow::class)
    class CreateTransactionResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("CREATE TX - WAITING TO SIGN TX")
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            })
            logger.info("CREATE TX - SIGNED TO SIGN TX")
            subFlow(ReceiveFinalityFlow(session, stx.id))
            logger.info("CREATE TX - RECEIVED TX")
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class SpendStateAndCatchDoubleSpendFlow(
        private val peer: Party,
        private val ref: StateAndRef<DummyState>,
        private val consumePeerError: Boolean
    ) : FlowLogic<StateAndRef<DummyState>>() {

        constructor(peer: Party, ref: StateAndRef<DummyState>): this(peer, ref, false)

        @Suspendable
        override fun call(): StateAndRef<DummyState> {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addInputState(ref)
                addOutputState(DummyState(participants = listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Move(), listOf(ourIdentity.owningKey, peer.owningKey))
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(peer)
            session.send(consumePeerError)
            val ftx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            try {
                subFlow(FinalityFlow(ftx, session))
            } catch(e: NotaryException) {
                logger.info("Caught notary exception")
            }
            return ftx.coreTransaction.outRef(0)
        }
    }

    @InitiatedBy(SpendStateAndCatchDoubleSpendFlow::class)
    class SpendStateAndCatchDoubleSpendResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            var exceptionSeenInUserFlow = false
        }

        @Suspendable
        override fun call() {
            val consumeError = session.receive<Boolean>().unwrap { it }
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {

                }
            })
            try {
                subFlow(ReceiveFinalityFlow(session, stx.id))
            } catch (e: UnexpectedFlowEndException) {
                exceptionSeenInUserFlow = true
                if (!consumeError) {
                    throw e
                }
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class CreateTransactionButDontFinalizeFlow(private val peer: Party, private val ref: StateAndRef<DummyState>) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addInputState(ref)
                addOutputState(DummyState(participants = listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Move(), listOf(ourIdentity.owningKey))
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(peer)
            // Send the transaction id to the peer instead of the transaction.
            // This allows transaction dependency resolution to occur within the peer's [ReceiveTransactionFlow].
            session.send(stx.id)
            // Mimic notarisation from [FinalityFlow] so that failing inside [ResolveTransactionsFlow] can be achieved.
            val notarySignatures = subFlow(NotaryFlow.Client(stx, skipVerification = true))
            val notarisedTx = stx + notarySignatures
            session.send(notarisedTx)
        }
    }

    @InitiatedBy(CreateTransactionButDontFinalizeFlow::class)
    class CreateTransactionButDontFinalizeResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            var exceptionSeenInUserFlow = false
        }

        @Suspendable
        override fun call() {
            val id = session.receive<SecureHash>().unwrap { it }
            try {
                subFlow(ReceiveFinalityFlow(session, id))
            } catch (e: UnexpectedFlowEndException) {
                exceptionSeenInUserFlow = true
                throw e
            }
        }
    }
}