package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.vault.DUMMY_DEAL_PROGRAM_ID
import net.corda.testing.internal.vault.DummyDealContract
import net.corda.testing.internal.vault.UNIQUE_DUMMY_LINEAR_CONTRACT_PROGRAM_ID
import net.corda.testing.internal.vault.UniqueDummyLinearContract
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals

class VaultFlowTest {

    private lateinit var mockNetwork: MockNetwork
    private lateinit var partyA: StartedMockNode
    private lateinit var partyB: StartedMockNode
    private lateinit var notaryNode: MockNetworkNotarySpec

    @Before
    fun setup() {
        notaryNode = MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))
        mockNetwork = MockNetwork(
                listOf(
                        "net.corda.node.services.vault", "net.corda.testing.internal.vault"
                ),
                notarySpecs = listOf(notaryNode),
                threadPerNode = true,
                networkSendManuallyPumped = false
        )
        partyA =
                mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("PartyA", "Berlin", "DE")))

        partyB =
                mockNetwork.createNode(MockNodeParameters(legalName = CordaX500Name("PartyB", "Berlin", "DE")))
        mockNetwork.startNodes()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `Unique column constraint failing causes states to not persist to vaults`() {
        partyA.startFlow(Initiator(listOf(partyA.info.singleIdentity(), partyB.info.singleIdentity()))).get()
        Assertions.assertThatExceptionOfType(ExecutionException::class.java).isThrownBy {
            partyA.startFlow(Initiator(listOf(partyA.info.singleIdentity(), partyB.info.singleIdentity()))).get()
        }
        assertEquals(1, partyA.transaction {
            partyA.services.vaultService.queryBy<UniqueDummyLinearContract.State>().states.size
        })
        assertEquals(1, partyB.transaction {
            partyB.services.vaultService.queryBy<UniqueDummyLinearContract.State>().states.size
        })
        assertEquals(1, partyA.transaction {
            partyA.services.vaultService.queryBy<DummyDealContract.State>().states.size
        })
        assertEquals(1, partyB.transaction {
            partyB.services.vaultService.queryBy<DummyDealContract.State>().states.size
        })
    }
}

@InitiatingFlow
class Initiator(private val participants: List<Party>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val stx = serviceHub.signInitialTransaction(TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
            addOutputState(UniqueDummyLinearContract.State(participants, "Dummy linear id"), UNIQUE_DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
            addOutputState(DummyDealContract.State(participants, "linear id"), DUMMY_DEAL_PROGRAM_ID)
            addCommand(DummyCommandData, listOf(ourIdentity.owningKey))
        })
        subFlow(FinalityFlow(stx))
    }
}
