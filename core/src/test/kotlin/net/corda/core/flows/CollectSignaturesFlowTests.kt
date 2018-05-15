/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class CollectSignaturesFlowTests {
    companion object {
        private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: StartedNode<MockNode>
    private lateinit var bobNode: StartedNode<MockNode>
    private lateinit var charlieNode: StartedNode<MockNode>
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts", "net.corda.core.flows"))
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // With this flow, the initiator starts the "CollectTransactionFlow". It is then the responders responsibility to
    // override "checkTransaction" and add whatever logic their require to verify the SignedTransaction they are
    // receiving off the wire.
    object TestFlow {
        @InitiatingFlow
        class Initiator(private val state: DummyContract.MultiOwnerState, private val notary: Party) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val myInputKeys = state.participants.map { it.owningKey }
                val command = Command(DummyContract.Commands.Create(), myInputKeys)
                val builder = TransactionBuilder(notary).withItems(StateAndContract(state, DummyContract.PROGRAM_ID), command)
                val ptx = serviceHub.signInitialTransaction(builder)
                val sessions = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, state.owners)).map { initiateFlow(it.key) }
                val stx = subFlow(CollectSignaturesFlow(ptx, sessions, myInputKeys))
                return subFlow(FinalityFlow(stx))
            }
        }

        @InitiatedBy(TestFlow.Initiator::class)
        class Responder(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val signFlow = object : SignTransactionFlow(otherSideSession) {
                    @Suspendable
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        val tx = stx.tx
                        val ltx = tx.toLedgerTransaction(serviceHub)
                        "There should only be one output state" using (tx.outputs.size == 1)
                        "There should only be one output state" using (tx.inputs.isEmpty())
                        val magicNumberState = ltx.outputsOfType<DummyContract.MultiOwnerState>().single()
                        "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
                    }
                }

                val stx = subFlow(signFlow)
                waitForLedgerCommit(stx.id)
            }
        }
    }

    @Test
    fun `successfully collects two signatures`() {
        val bConfidentialIdentity = bobNode.database.transaction {
            val bobCert = bobNode.services.myInfo.legalIdentitiesAndCerts.single { it.name == bob.name }
            bobNode.services.keyManagementService.freshKeyAndCert(bobCert, false)
        }
        aliceNode.database.transaction {
            // Normally this is handled by TransactionKeyFlow, but here we have to manually let A know about the identity
            aliceNode.services.identityService.verifyAndRegisterIdentity(bConfidentialIdentity)
        }
        val magicNumber = 1337
        val parties = listOf(alice, bConfidentialIdentity.party, charlie)
        val state = DummyContract.MultiOwnerState(magicNumber, parties)
        val flow = aliceNode.services.startFlow(TestFlow.Initiator(state, notary))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `no need to collect any signatures`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, alice.ref(1))
        val ptx = aliceNode.services.signInitialTransaction(onePartyDummyContract)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(ptx, emptySet()))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        result.verifyRequiredSignatures()
        println(result.tx)
        println(result.sigs)
    }

    @Test
    fun `fails when not signed by initiator`() {
        val onePartyDummyContract = DummyContract.generateInitial(1337, notary, alice.ref(1))
        val miniCorpServices = MockServices(listOf("net.corda.testing.contracts"), miniCorp, rigorousMock())
        val ptx = miniCorpServices.signInitialTransaction(onePartyDummyContract)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(ptx, emptySet()))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException>("The Initiator of CollectSignaturesFlow must have signed the transaction.") {
            flow.resultFuture.getOrThrow()
        }
    }

    @Test
    fun `passes with multiple initial signatures`() {
        val twoPartyDummyContract = DummyContract.generateInitial(1337, notary,
                alice.ref(1),
                bob.ref(2),
                bob.ref(3))
        val signedByA = aliceNode.services.signInitialTransaction(twoPartyDummyContract)
        val signedByBoth = bobNode.services.addSignature(signedByA)
        val flow = aliceNode.services.startFlow(CollectSignaturesFlow(signedByBoth, emptySet()))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()
        println(result.tx)
        println(result.sigs)
    }
}
