package net.corda.notarychange

import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.getOrThrow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Random
import kotlin.test.assertEquals

class NotaryChangeFlowTest {
    private val oldNotaryName = CordaX500Name("Old Notary", "Zurich", "CH")
    private val newNotaryName = CordaX500Name("New Notary", "Zurich", "CH")

    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var oldNotaryParty: Party
    private lateinit var newNotaryParty: Party

    @Before
    fun start(){
        mockNet = MockNetwork(MockNetworkParameters(
                notarySpecs = listOf(MockNetworkNotarySpec(oldNotaryName), MockNetworkNotarySpec(newNotaryName)),
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, cordappWithPackages("net.corda.notarychange"))
        ))
        nodeA = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        nodeB = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME))
        partyA = nodeA.info.singleIdentity()

        oldNotaryParty = nodeA.services.networkMapCache.getNotary(oldNotaryName)!!
        newNotaryParty = nodeA.services.networkMapCache.getNotary(newNotaryName)!!
    }

    @After
    fun cleanUp(){
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
    fun `should change notary for a state with single participant`() {
        val state = issueState(nodeA.services, partyA, oldNotaryParty)
        assertEquals(state.state.notary, oldNotaryParty)
        val newState = changeNotary(state, nodeA, newNotaryParty)
        assertEquals(newState.state.notary, newNotaryParty)
    }

    private fun changeNotary(movedState: StateAndRef<DummyContract.SingleOwnerState>, node: StartedMockNode, newNotary: Party): StateAndRef<DummyContract.SingleOwnerState> {
        val flow = NotaryChangeFlow(movedState, newNotary)
        val future = node.startFlow(flow)
        mockNet.runNetwork()

        return future.getOrThrow()
    }


}

fun issueState(services: ServiceHub, nodeIdentity: Party, notaryIdentity: Party): StateAndRef<DummyContract.SingleOwnerState> {
    val tx = DummyContract.generateInitial(Random().nextInt(), notaryIdentity, nodeIdentity.ref(0))
    val stx = services.signInitialTransaction(tx)
    services.recordTransactions(stx)
    return stx.tx.outRef(0)
}

