package node.services

import com.r3corda.contracts.DummyContract
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.contracts.TransactionState
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.internal.testing.issueState
import com.r3corda.node.services.transactions.NotaryService
import org.junit.Before
import org.junit.Test
import protocols.NotaryChangeProtocol
import protocols.NotaryChangeProtocol.Instigator
import kotlin.test.assertEquals

class NotaryChangeTests {
    lateinit var net: MockNetwork
    lateinit var oldNotaryNode: MockNetwork.MockNode
    lateinit var newNotaryNode: MockNetwork.MockNode
    lateinit var clientNodeA: MockNetwork.MockNode
    lateinit var clientNodeB: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        oldNotaryNode = net.createNotaryNode(DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        clientNodeA = net.createPartyNode(networkMapAddr = oldNotaryNode.info)
        clientNodeB = net.createPartyNode(networkMapAddr = oldNotaryNode.info)
        newNotaryNode = net.createNode(networkMapAddress = oldNotaryNode.info, advertisedServices = NotaryService.Type)

        net.runNetwork() // Clear network map registration messages
    }

    @Test
    fun `should change notary for a state with single participant`() {
        val ref = issueState(clientNodeA, DUMMY_NOTARY).ref
        val state = clientNodeA.services.loadState(ref)

        val newNotary = newNotaryNode.info.identity

        val protocol = Instigator(StateAndRef(state, ref), newNotary)
        val future = clientNodeA.smm.add(NotaryChangeProtocol.TOPIC_CHANGE, protocol)

        net.runNetwork()

        val newState = future.get()
        assertEquals(newState.state.notary, newNotary)
    }

    @Test
    fun `should change notary for a state with multiple participants`() {
        val state = TransactionState(DummyContract.MultiOwnerState(0,
                listOf(clientNodeA.info.identity.owningKey, clientNodeB.info.identity.owningKey)), DUMMY_NOTARY)

        val tx = TransactionType.NotaryChange.Builder().withItems(state)
        tx.signWith(clientNodeA.storage.myLegalIdentityKey)
        tx.signWith(clientNodeB.storage.myLegalIdentityKey)
        tx.signWith(DUMMY_NOTARY_KEY)
        val stx = tx.toSignedTransaction()
        clientNodeA.services.recordTransactions(listOf(stx))
        clientNodeB.services.recordTransactions(listOf(stx))
        val stateAndRef = StateAndRef(state, StateRef(stx.id, 0))

        val newNotary = newNotaryNode.info.identity

        val protocol = Instigator(stateAndRef, newNotary)
        val future = clientNodeA.smm.add(NotaryChangeProtocol.TOPIC_CHANGE, protocol)

        net.runNetwork()

        val newState = future.get()
        assertEquals(newState.state.notary, newNotary)
        val loadedStateA = clientNodeA.services.loadState(newState.ref)
        val loadedStateB = clientNodeB.services.loadState(newState.ref)
        assertEquals(loadedStateA, loadedStateB)
    }

    // TODO: Add more test cases once we have a general protocol/service exception handling mechanism:
    //       - A participant refuses to change Notary
    //       - A participant is offline/can't be found on the network
    //       - The requesting party is not a participant
    //       - The requesting party wants to change additional state fields
    //       - Multiple states in a single "notary change" transaction
    //       - Transaction contains additional states and commands with business logic
}