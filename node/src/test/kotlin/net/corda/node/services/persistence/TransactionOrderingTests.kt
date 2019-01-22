package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.testing.MESSAGE_CHAIN_CONTRACT_PROGRAM_ID
import net.corda.node.testing.MessageChainContract
import net.corda.node.testing.MessageChainState
import net.corda.node.testing.MessageData
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TransactionOrderingTests {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun start() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(cordappWithPackages("net.corda.node.testing"), enclosedCordapp()),
                networkSendManuallyPumped = false,
                threadPerNode = true
        )
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Out of order transactions are recorded in vault correctly`() {
        val alice = mockNet.createPartyNode(ALICE_NAME)
        val aliceID = alice.info.identityFromX500Name(ALICE_NAME)

        val bob = mockNet.createPartyNode(BOB_NAME)
        val bobID = bob.info.identityFromX500Name(BOB_NAME)
        bob.registerInitiatedFlow(ReceiveTx::class.java)

        val notary = mockNet.defaultNotaryNode
        val notaryID = mockNet.defaultNotaryIdentity

        fun signTx(txBuilder: TransactionBuilder): SignedTransaction {
            val first = alice.services.signInitialTransaction(txBuilder)
            val second = bob.services.addSignature(first)
            return notary.services.addSignature(second)
        }

        val state1 = MessageChainState(MessageData("A"), aliceID, extraParty = bobID)
        val command = Command(MessageChainContract.Commands.Send(), state1.participants.map {it.owningKey})
        val tx1Builder = TransactionBuilder(notaryID).withItems(
                StateAndContract(state1, MESSAGE_CHAIN_CONTRACT_PROGRAM_ID),
                command)
        val stx1 = signTx(tx1Builder)

        val state2 = MessageChainState(MessageData("AA"), aliceID, state1.linearId, extraParty = bobID)
        val tx2Builder = TransactionBuilder(notaryID).withItems(
                StateAndContract(state2, MESSAGE_CHAIN_CONTRACT_PROGRAM_ID),
                command,
                StateAndRef(stx1.coreTransaction.outputs[0], StateRef(stx1.coreTransaction.id, 0))
        )
        val stx2 = signTx(tx2Builder)

        val state3 = MessageChainState(MessageData("AAA"), aliceID, state1.linearId, extraParty = bobID)
        val tx3Builder = TransactionBuilder(notaryID).withItems(
                StateAndContract(state3, MESSAGE_CHAIN_CONTRACT_PROGRAM_ID),
                command,
                StateAndRef(stx2.coreTransaction.outputs[0], StateRef(stx2.coreTransaction.id, 0))
        )
        val stx3 = signTx(tx3Builder)

        alice.services.recordTransactions(listOf(stx1, stx2, stx3))

        alice.services.startFlow(SendTx(bobID, stx3)).resultFuture.getOrThrow()
        alice.services.startFlow(SendTx(bobID, stx1)).resultFuture.getOrThrow()
        alice.services.startFlow(SendTx(bobID, stx2)).resultFuture.getOrThrow()

        val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val bobStates = bob.services.vaultService.queryBy(MessageChainState::class.java, queryCriteria)
        assertEquals(3, bobStates.states.size)
    }


    @InitiatingFlow
    @StartableByRPC
    class SendTx(private val party: Party,
                 private val stx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            subFlow(SendTransactionFlow(session, stx))
            session.receive<Unit>()
        }
    }

    @InitiatedBy(SendTx::class)
    class ReceiveTx(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ONLY_RELEVANT))
            otherSideSession.send(Unit)
        }
    }
}
