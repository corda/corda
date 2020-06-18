package net.corda.bn.flows

import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before

abstract class MembershipManagementFlowTest(
        private val numberOfAuthorisedMembers: Int,
        private val numberOfRegularMembers: Int
) {

    protected lateinit var authorisedMembers: List<StartedMockNode>
    protected lateinit var regularMembers: List<StartedMockNode>
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setUp() {
        mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.bn.contracts"),
                TestCordapp.findCordapp("net.corda.bn.flows")
        )))

        authorisedMembers = (0..numberOfAuthorisedMembers).mapIndexed { idx, _ ->
            createNode(CordaX500Name.parse("O=BNO_$idx,L=New York,C=US"))
        }
        regularMembers = (0..numberOfRegularMembers).mapIndexed { idx, _ ->
            createNode(CordaX500Name.parse("O=Member_$idx,L=New York,C=US"))
        }

        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    private fun createNode(name: CordaX500Name) = mockNetwork.createNode(MockNodeParameters(legalName = name))

    protected fun runCreateBusinessNetworkFlow(initiator: StartedMockNode, networkId: UniqueIdentifier = UniqueIdentifier(), notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(CreateBusinessNetworkFlow(networkId, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runRequestMembershipFlow(initiator: StartedMockNode, authorisedNode: StartedMockNode, networkId: String, notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(RequestMembershipFlow(authorisedNode.identity(), networkId, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runActivateMembershipFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier, notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(ActivateMembershipFlow(membershipId, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runRequestAndActivateMembershipFlows(initiator: StartedMockNode, authorisedNode: StartedMockNode, networkId: String, notary: Party? = null): SignedTransaction {
        val membership = runRequestMembershipFlow(initiator, authorisedNode, networkId, notary).tx.outputStates.single() as MembershipState
        return runActivateMembershipFlow(authorisedNode, membership.linearId, notary)
    }

    protected fun runSuspendMembershipFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier, notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(SuspendMembershipFlow(membershipId, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runRequestAndSuspendMembershipFlow(initiator: StartedMockNode, authorisedNode: StartedMockNode, networkId: String, notary: Party? = null): SignedTransaction {
        val membership = runRequestMembershipFlow(initiator, authorisedNode, networkId, notary).tx.outputStates.single() as MembershipState
        return runSuspendMembershipFlow(authorisedNode, membership.linearId, notary)
    }

    protected fun runSuspendMembershipFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier): SignedTransaction {
        val future = initiator.startFlow(SuspendMembershipFlow(membershipId))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runRequestAndSuspendMembershipFlow(initiator: StartedMockNode, authorisedNode: StartedMockNode, networkId: String): SignedTransaction {
        val membership = runRequestMembershipFlow(initiator, authorisedNode, networkId).tx.outputStates.single() as MembershipState
        return runSuspendMembershipFlow(authorisedNode, membership.linearId)
    }

    protected fun runRevokeMembershipFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier): SignedTransaction {
        val future = initiator.startFlow(RevokeMembershipFlow(membershipId))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun getAllMembershipsFromVault(node: StartedMockNode, networkId: String): List<MembershipState> {
        val databaseService = node.services.cordaService(DatabaseService::class.java)
        return databaseService.getAllMembershipsWithStatus(
                networkId,
                MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
        ).map {
            it.state.data
        }
    }
}

fun StartedMockNode.identity() = info.legalIdentities.single()
