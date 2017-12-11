package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.issueInvalidState
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidatingNotaryServiceTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryServices: StartedNodeServices
    private lateinit var aliceServices: StartedNodeServices
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = setOf("net.corda.testing.contracts"))
        val aliceNode = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        notaryServices = mockNet.defaultNotaryNode.services
        aliceServices = aliceNode.services
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(aliceServices, alice, notary)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceServices.signInitialTransaction(tx)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).isInstanceOf(SignedTransaction.SignaturesMissingException::class.java)
    }

    @Test
    fun `should report error for missing signatures`() {
        val expectedMissingKey = MEGA_CORP_KEY.public
        val stx = run {
            val inputState = issueState(aliceServices, alice)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionBuilder(notary).withItems(inputState, command)
            aliceServices.signInitialTransaction(tx)
        }

        val ex = assertFailsWith(NotaryException::class) {
            val future = runClient(stx)
            future.getOrThrow()
        }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).isInstanceOf(SignedTransaction.SignaturesMissingException::class.java)

        val missingKeys = (notaryError.cause as SignedTransaction.SignaturesMissingException).missing
        assertEquals(setOf(expectedMissingKey), missingKeys)
    }

    private fun runClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceServices.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    private fun issueState(serviceHub: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryServices.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
