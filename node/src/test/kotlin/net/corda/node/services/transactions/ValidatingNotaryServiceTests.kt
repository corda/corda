package net.corda.node.services.transactions

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.DigitalSignature
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.issueInvalidState
import net.corda.node.services.network.NetworkMapService
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidatingNotaryServiceTests {
    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before
    fun setup() {
        mockNet = MockNetwork()
        notaryNode = mockNet.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type))
        )
        clientNode = mockNet.createNode(networkMapAddress = notaryNode.network.myAddress)
        mockNet.runNetwork() // Clear network map registration messages
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(clientNode, notaryNode.info.notaryIdentity)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            clientNode.services.signInitialTransaction(tx)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.SignaturesInvalid::class.java)
    }

    @Test
    fun `should report error for missing signatures`() {
        val expectedMissingKey = MEGA_CORP_KEY.public
        val stx = run {
            val inputState = issueState(clientNode)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState, command)
            clientNode.services.signInitialTransaction(tx)
        }

        val ex = assertFailsWith(NotaryException::class) {
            val future = runClient(stx)
            future.getOrThrow()
        }
        val notaryError = ex.error
        assertThat(notaryError).isInstanceOf(NotaryError.SignaturesMissing::class.java)

        val missingKeys = (notaryError as NotaryError.SignaturesMissing).cause.missing
        assertEquals(setOf(expectedMissingKey), missingKeys)
    }

    private fun runClient(stx: SignedTransaction): ListenableFuture<List<DigitalSignature.WithKey>> {
        val flow = NotaryFlow.Client(stx)
        val future = clientNode.services.startFlow(flow).resultFuture
        mockNet.runNetwork()
        return future
    }

    fun issueState(node: AbstractNode): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notaryNode.info.notaryIdentity, node.info.legalIdentity.ref(0))
        val signedByNode = node.services.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notaryNode.services.notaryIdentityKey)
        node.services.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
