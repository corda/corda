package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.identity.*
import net.corda.core.matchers.succeedsWith
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
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
import net.corda.testing.node.internal.startFlow
import org.junit.AfterClass
import org.junit.Test
import java.util.*
import com.natpryce.hamkrest.assertion.assert
import net.corda.core.contracts.PartyAndReference
import net.corda.core.matchers.failsWith
import net.corda.core.node.ServiceHub

class CollectSignaturesFlowTests {
    companion object {
        private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        private val miniCorpServices = MockServices(listOf("net.corda.testing.contracts"), miniCorp, rigorousMock())
        private val mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts", "net.corda.core.flows"))
        private const val MAGIC_NUMBER = 1337

        @JvmStatic
        @AfterClass
        fun tearDown() = mockNet.stopNodes()
    }

    private val aliceNode = makeNode(ALICE_NAME)
    private val bobNode = makeNode(BOB_NAME)
    private val charlieNode = makeNode(CHARLIE_NAME)

    private val alice = aliceNode.info.singleIdentity()
    private val bob = bobNode.info.singleIdentity()
    private val charlie = charlieNode.info.singleIdentity()

    @Test
    fun `successfully collects three signatures`() {
        val bConfidentialIdentity = bobNode.createConfidentialIdentity(bob)
        aliceNode.verifyAndRegister(bConfidentialIdentity)

        assert.that(
            aliceNode.startTestFlow(alice, bConfidentialIdentity.party, charlie),
            succeedsWith(requiredSignatures(3))
        )
    }

    @Test
    fun `no need to collect any signatures`() {
        val ptx = aliceNode.signDummyContract(alice.ref(1))

        assert.that(
                aliceNode.collectSignatures(ptx),
                succeedsWith(requiredSignatures(1))
        )
    }

    @Test
    fun `fails when not signed by initiator`() {
        val ptx = miniCorpServices.signDummyContract(alice.ref(1))

        assert.that(
                aliceNode.collectSignatures(ptx),
                failsWith(errorMessage("The Initiator of CollectSignaturesFlow must have signed the transaction.")))
    }

    @Test
    fun `passes with multiple initial signatures`() {
        val signedByA = aliceNode.signDummyContract(
                alice.ref(1),
                bob.ref(2),
                bob.ref(3))
        val signedByBoth = bobNode.addSignatureTo(signedByA)

        assert.that(
                aliceNode.collectSignatures(signedByBoth),
                succeedsWith(requiredSignatures(2))
        )
    }

    //region Test Flow
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
                        "Must be $MAGIC_NUMBER or greater" using (magicNumberState.magicNumber >= MAGIC_NUMBER)
                    }
                }

                val stx = subFlow(signFlow)
                waitForLedgerCommit(stx.id)
            }
        }
    }
    //region

    //region Generators
    private fun makeNode(name: CordaX500Name) = mockNet.createPartyNode(randomise(name))
    private fun randomise(name: CordaX500Name) = name.copy(commonName = "${name.commonName}_${UUID.randomUUID()}")
    //endregion

    //region Operations
    private fun StartedNode<*>.createConfidentialIdentity(party: Party) = database.transaction {
        services.keyManagementService.freshKeyAndCert(
            services.myInfo.legalIdentitiesAndCerts.single { it.name == party.name },
            false)
    }

    private fun StartedNode<*>.verifyAndRegister(identity: PartyAndCertificate) = database.transaction {
        services.identityService.verifyAndRegisterIdentity(identity)
    }

    private fun StartedNode<*>.startTestFlow(vararg party: Party) =
        services.startFlow(
            TestFlow.Initiator(DummyContract.MultiOwnerState(
                MAGIC_NUMBER,
                listOf(*party)),
            mockNet.defaultNotaryIdentity))
        .andRunNetwork()

    private fun createDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            DummyContract.generateInitial(
                    MAGIC_NUMBER,
                    mockNet.defaultNotaryIdentity,
                    owner,
                    *others)

    private fun StartedNode<*>.signDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            services.signDummyContract(owner, *others).andRunNetwork()

    private fun ServiceHub.signDummyContract(owner: PartyAndReference, vararg others: PartyAndReference) =
            signInitialTransaction(createDummyContract(owner, *others))

    private fun StartedNode<*>.collectSignatures(ptx: SignedTransaction) =
            services.startFlow(CollectSignaturesFlow(ptx, emptySet()))
                    .andRunNetwork()

    private fun StartedNode<*>.addSignatureTo(ptx: SignedTransaction) =
            services.addSignature(ptx).andRunNetwork()

    private fun <T: Any> T.andRunNetwork(): T {
        mockNet.runNetwork()
        return this
    }
    //endregion

    //region Matchers
    private fun requiredSignatures(count: Int = 1) = object : Matcher<SignedTransaction> {
        override val description: String = "A transaction with valid required signatures"

        override fun invoke(actual: SignedTransaction): MatchResult = try {
            actual.verifyRequiredSignatures()
            has(SignedTransaction::sigs, hasSize(equalTo(count)))(actual)
        } catch (e: Exception) {
            MatchResult.Mismatch("$e")
        }
    }

    private fun errorMessage(expected: String) = has(
        Exception::message,
        equalTo(expected))
    //endregion
}
