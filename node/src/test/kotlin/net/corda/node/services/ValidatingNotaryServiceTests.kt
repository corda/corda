package net.corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.composite
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.MINI_CORP_KEY
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidatingNotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before fun setup() {
        net = MockNetwork()
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.type), ServiceInfo(ValidatingNotaryService.type))
        )
        clientNode = net.createNode(networkMapAddress = notaryNode.info.address)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(clientNode, notaryNode.info.notaryIdentity)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState)
            val keyPair = clientNode.services.keyManagementService.toKeyPair(clientNode.info.legalIdentity.owningKey.keys.single())
            tx.signWith(keyPair)
            tx.toSignedTransaction(false)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        assertThat(ex.error).isInstanceOf(NotaryError.SignaturesInvalid::class.java)
    }

    @Test fun `should report error for missing signatures`() {
        val expectedMissingKey = MEGA_CORP_KEY.public.composite
        val stx = run {
            val inputState = issueState(clientNode)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionType.General.Builder(notaryNode.info.notaryIdentity).withItems(inputState, command)
            val keyPair = clientNode.services.keyManagementService.toKeyPair(clientNode.info.legalIdentity.owningKey.keys.single())
            tx.signWith(keyPair)
            tx.toSignedTransaction(false)
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

    private fun runClient(stx: SignedTransaction): ListenableFuture<DigitalSignature.WithKey> {
        val flow = NotaryFlow.Client(stx)
        val future = clientNode.services.startFlow(flow).resultFuture
        net.runNetwork()
        return future
    }

    fun issueState(node: AbstractNode): StateAndRef<*> {
        val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notaryNode.info.notaryIdentity)
        val nodeKey = node.services.legalIdentityKey
        tx.signWith(nodeKey)
        val notaryKeyPair = notaryNode.services.notaryIdentityKey
        tx.signWith(notaryKeyPair)
        val stx = tx.toSignedTransaction()
        node.services.recordTransactions(listOf(stx))
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
