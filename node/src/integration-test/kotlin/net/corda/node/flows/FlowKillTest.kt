package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
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
//            charlie.stop()

            val handle = CordaRPCClient(alice.rpcAddress).start(user.username, user.password).use {
                val h = it.proxy.startFlow(::DummyFlow, notary.identity, bob.nodeInfo.legalIdentities.first(), charlieParty)
                h
            }

            bob.rpc.stateMachinesFeed().let { it.updates.map {
                update -> update.id
            }.startWith(it.snapshot.map { snapshot -> snapshot.id }) }.toBlocking().first()

//            CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
//                assertEquals(1, it.proxy.stateMachinesSnapshot().size, "Wrong number of active flows on Bob")
//                assertEquals(
//                        DummyResponder::class.java.name,
//                        it.proxy.stateMachinesSnapshot().first().flowLogicClassName,
//                        "Unexpected active flow name")
//            }

            CordaRPCClient(alice.rpcAddress).start(user.username, user.password).use {
                val stateMachines = it.proxy.stateMachinesSnapshot()
                assertEquals(1, stateMachines.size, "Couldn't find initiated flow")
                val killed = it.proxy.killFlow(handle.id)
                assertEquals(true, killed, "Could not kill flow with id ${handle.id}")
            }
//
//            CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
//                assertEquals(0, it.proxy.stateMachinesSnapshot().size, "Flow on Bob has not been killed")
//            }
        }
    }
}

@StartableByRPC
@InitiatingFlow
class DummyFlow(val notary: Party, val partyA: Party, val partyB: Party): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val parties = listOf(partyA, partyB)
        val state = DummyContract.MultiOwnerState(owners = parties)
        val command = Command(DummyContract.Commands.Create(), parties.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(state, DummyContract.PROGRAM_ID)
                .addCommand(command)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        val sessions = parties.map { initiateFlow(it) }
        subFlow(CollectSignaturesFlow(signedTx, sessions))
        sessions.forEach {
            it.send(Unit)
        }
        sessions[1].receive<Unit>()
        sessions[0].receive<Unit>()
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
        subFlow(response)
        otherSideSession.receive<Unit>()
        otherSideSession.send(Unit)
    }
}