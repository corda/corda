package net.corda.bn.demo.workflows

import net.corda.bn.demo.contracts.LoanState
import net.corda.bn.flows.ActivateMembershipFlow
import net.corda.bn.flows.CreateBusinessNetworkFlow
import net.corda.bn.flows.ModifyGroupFlow
import net.corda.bn.flows.RequestMembershipFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before

abstract class LoanFlowTest(private val numberOfLenders: Int, private val numberOfBorrowers: Int) {

    protected lateinit var lenders: List<StartedMockNode>
    protected lateinit var borrowers: List<StartedMockNode>
    private lateinit var mockNetwork: MockNetwork

    @Before
    fun setUp() {
        mockNetwork = MockNetwork(MockNetworkParameters(
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("net.corda.bn.contracts"),
                        TestCordapp.findCordapp("net.corda.bn.flows"),
                        TestCordapp.findCordapp("net.corda.bn.demo.contracts"),
                        TestCordapp.findCordapp("net.corda.bn.demo.workflows")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        ))

        lenders = (0..numberOfLenders).mapIndexed { idx, _ ->
            createNode(CordaX500Name.parse("O=Lender_$idx,L=New York,C=US"))
        }
        borrowers = (0..numberOfBorrowers).mapIndexed { idx, _ ->
            createNode(CordaX500Name.parse("O=Borrower_$idx,L=New York,C=US"))
        }

        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    private fun createNode(name: CordaX500Name) = mockNetwork.createNode(MockNodeParameters(legalName = name))

    protected fun runCreateBusinessNetworkFlow(initiator: StartedMockNode): SignedTransaction {
        val future = initiator.startFlow(CreateBusinessNetworkFlow())
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runRequestMembershipFlow(initiator: StartedMockNode, authorisedParty: Party, networkId: String): SignedTransaction {
        val future = initiator.startFlow(RequestMembershipFlow(authorisedParty, networkId))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runActivateMembershipFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier): SignedTransaction {
        val future = initiator.startFlow(ActivateMembershipFlow(membershipId))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runModifyGroupFlow(initiator: StartedMockNode, groupId: UniqueIdentifier, participants: Set<UniqueIdentifier>): SignedTransaction {
        val future = initiator.startFlow(ModifyGroupFlow(groupId, null, participants))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runAssignBICFlow(initiator: StartedMockNode, membershipId: UniqueIdentifier, bic: String, notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(AssignBICFlow(membershipId, bic, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runAssignLoanIssuerRole(initiator: StartedMockNode, networkId: String, notary: Party? = null): SignedTransaction {
        val future = initiator.startFlow(AssignLoanIssuerRoleFlow(networkId, notary))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runIssueLoanFlow(initiator: StartedMockNode, networkId: String, borrower: Party, amount: Int): SignedTransaction {
        val future = initiator.startFlow(IssueLoanFlow(networkId, borrower, amount))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun runSettleLoanFlow(initiator: StartedMockNode, loanId: UniqueIdentifier, amountToSettle: Int): SignedTransaction {
        val future = initiator.startFlow(SettleLoanFlow(loanId, amountToSettle))
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    protected fun getAllLoansFromVault(node: StartedMockNode): List<LoanState> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        return node.services.vaultService.queryBy(LoanState::class.java, criteria).states.map { it.state.data }
    }
}

fun StartedMockNode.identity() = info.legalIdentities.single()