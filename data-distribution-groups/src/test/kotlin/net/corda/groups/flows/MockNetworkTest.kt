package net.corda.groups.flows

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.groups.contracts.Group
import net.corda.testing.core.DummyCommandData
import net.corda.testing.internal.vault.DUMMY_LINEAR_CONTRACT_PROGRAM_ID
import net.corda.testing.internal.vault.DummyLinearContract
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import java.security.PublicKey

/** Helper class for creating data distribution group tests. */
abstract class MockNetworkTest(val numberOfNodes: Int) {

    protected val network = InternalMockNetwork(
            cordappsForAllNodes = cordappsForPackages("net.corda.groups", "net.corda.testing.internal.vault"),
            threadPerNode = true
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<TestStartedNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = createSomeNodes(numberOfNodes)
    }

    @After
    fun tearDownNetwork() {
        network.stopNodes()
    }

    private fun createSomeNodes(numberOfNodes: Int = 2): List<TestStartedNode> {
        val partyNodes = (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            network.createPartyNode(name)
        }
        return partyNodes
    }

    /** Sugar for creating a new data distribution group with the specified name. Returns a future signed transaction. */
    fun TestStartedNode.createGroup(name: String) = services.startFlow(CreateGroup(name)).resultFuture

    /** Add a transaction to the specified data distribution group. */
    fun TestStartedNode.addToGroup(key: PublicKey, stx: SignedTransaction) {
        services.startFlow(AddDataToGroup(key, setOf(stx)))
    }

    /** Invite a [TestStartedNode] to a data distribution group. The first identity in the identities list is used. */
    fun TestStartedNode.inviteToGroup(key: PublicKey, node: TestStartedNode): CordaFuture<SignedTransaction> {
        val flow = InviteToGroup.Initiator(key, node.info.legalIdentities.first())
        return services.startFlow(flow).resultFuture
    }

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : ContractState> SignedTransaction.getSingleOutput() = tx.outRefsOfType<T>().single()

    /** Syntactic sugar to make unit tests easier to read. */
    fun StateAndRef<Group.State>.key() = state.data.details.key

    /** Create and sign a tx containing a dummy linear state as output, return a future signed transaction. */
    fun TestStartedNode.createDummyTransaction(): CordaFuture<SignedTransaction> {
        val me = services.myInfo.legalIdentities.first()
        val tx = TransactionBuilder(services.networkMapCache.notaryIdentities.first()).apply {
            val state = DummyLinearContract.State(participants = listOf(me))
            addOutputState(state, DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
            addCommand(DummyCommandData, listOf(me.owningKey))
        }
        return services.startFlow(FinalityFlow(services.signInitialTransaction(tx))).resultFuture
    }

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun TestStartedNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return services.validatedTransactions.updates.filter { it.id == txId }.toFuture()
    }

}

