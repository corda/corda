package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.FlowKilledException
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import rx.Observable.combineLatest
import kotlin.test.assertEquals

class FlowKillTest {

    @Test
    fun `Killing a flow causes flows to terminate on counterparties`() {
        val user = User("test", "test", setOf(Permissions.all()))
        driver(DriverParameters(
                startNodesInProcess = isQuasarAgentSpecified()
        )) {
            val (alice, bob, charlie) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            val notary = defaultNotaryHandle
            val charlieParty = charlie.nodeInfo.legalIdentities.first()
            // Detect when the flow has been killed on Bob by looking for an update that indicates the flow state machine has been removed.
            val bobEndedFuture = bob.rpc.stateMachinesFeed().updates.filter { it is StateMachineUpdate.Removed }
                    .map { it as StateMachineUpdate.Removed }.first().toFuture()
            // By stopping Charlie, the flow cannot complete on Alice
            charlie.stop()

            // Detect when the flow has started on both nodes
            val startedFuture = combineLatest(
                    alice.rpc.stateMachinesFeed().updates.filter { it is StateMachineUpdate.Added }.map { it as StateMachineUpdate.Added },
                    bob.rpc.stateMachinesFeed().updates.filter { it is StateMachineUpdate.Added }.map { it as StateMachineUpdate.Added }
            ) { a, b -> Pair(a, b) }.first().toFuture()

            CordaRPCClient(alice.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::DummyFlow, notary.identity, bob.nodeInfo.legalIdentities.first(), charlieParty)
                val (aliceUpdate, bobUpdate) = startedFuture.getOrThrow()
                val runId = bobUpdate.id
                // In order for the flow session to be killed on the counterparty node, the session must first be fully initialised. This
                // check is in place to ensure that the session is initialised before the flow is killed.
                val progressFeed = aliceUpdate.stateMachineInfo.progressTrackerStepAndUpdates
                if (progressFeed!!.snapshot != SESSION_STARTED_LABEL) {
                    progressFeed.updates.filter { update -> update == SESSION_STARTED_LABEL}.first().toFuture().getOrThrow()
                }
                it.proxy.killFlow(aliceUpdate.id)
                val ended = bobEndedFuture.getOrThrow()
                assertEquals(runId, ended.id)
                assertThrows(FlowKilledException::class.java) { ended.result.getOrThrow() }
            }
        }
    }
}

const val SESSION_STARTED_LABEL = "Session started"

@StartableByRPC
@InitiatingFlow
class DummyFlow(val notary: Party, val partyA: Party, val partyB: Party): FlowLogic<Unit>() {

    companion object {
        val SESSION_STARTED = ProgressTracker.Step(SESSION_STARTED_LABEL)
    }

    override val progressTracker = ProgressTracker(SESSION_STARTED)

    @Suspendable
    override fun call() {
        val parties = listOf(partyA, partyB)
        val state = DummyContract.MultiOwnerState(owners = parties)
        val command = Command(DummyContract.Commands.Create(), parties.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(state, DummyContract.PROGRAM_ID)
                .addCommand(command)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        // The flow from here is written slightly strangely to ensure that the progress tracker update happens after the session to partyA
        // has been fully initialised.
        val sessionA = initiateFlow(partyA)
        sessionA.send(Unit)
        sessionA.receive<Unit>()
        progressTracker.currentStep = SESSION_STARTED
        val sessionB = initiateFlow(partyB)
        sessionB.send(Unit)
        sessionB.receive<Unit>()
        val sessions = listOf(sessionA, sessionB)
        subFlow(CollectSignaturesFlow(signedTx, sessions))
    }
}

@InitiatedBy(DummyFlow::class)
class DummyResponder(val otherSideSession: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val response = object : SignTransactionFlow(otherSideSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                // Do nothing
            }
        }
        otherSideSession.receive<Unit>()
        otherSideSession.send(Unit)
        subFlow(response)
    }
}