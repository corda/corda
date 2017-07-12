package net.corda.flows

import com.google.common.util.concurrent.ListenableFuture
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.currency
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.node.utilities.transaction
import net.corda.testing.*
import net.corda.testing.contracts.calculateRandomlySizedAmounts
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class IssuerFlowTest(val anonymous: Boolean) {
    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun data(): Collection<Array<Boolean>> {
            return listOf(arrayOf(false), arrayOf(true))
        }
    }

    lateinit var mockNet: MockNetwork
    lateinit var notaryNode: MockNode
    lateinit var bankOfCordaNode: MockNode
    lateinit var bankClientNode: MockNode

    @Before
    fun start() {
        mockNet = MockNetwork(threadPerNode = true)
        notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        bankOfCordaNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOC.name)
        bankClientNode = mockNet.createPartyNode(notaryNode.network.myAddress, MEGA_CORP.name)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `test issuer flow`() {
        val notary = notaryNode.services.myInfo.notaryIdentity
        val (vaultUpdatesBoc, vaultUpdatesBankClient) = bankOfCordaNode.database.transaction {
            // Register for vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultQueryService.trackBy<Cash.State>(criteria)
            val (_, vaultUpdatesBankClient) = bankClientNode.services.vaultQueryService.trackBy<Cash.State>(criteria)

            // using default IssueTo Party Reference
            val issuerResult = runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, 1000000.DOLLARS,
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123), notary)
            issuerResult.get()

            Pair(vaultUpdatesBoc, vaultUpdatesBankClient)
        }

        // Check Bank of Corda Vault Updates
        vaultUpdatesBoc.expectEvents {
            sequence(
                    // ISSUE
                    expect { update ->
                        require(update.consumed.isEmpty()) { "Expected 0 consumed states, actual: $update" }
                        require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                        val issued = update.produced.single().state.data as Cash.State
                        require(issued.owner.owningKey in bankOfCordaNode.services.keyManagementService.keys)
                    },
                    // MOVE
                    expect { update ->
                        require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                        require(update.produced.isEmpty()) { "Expected 0 produced states, actual: $update" }
                    }
            )
        }

        // Check Bank Client Vault Updates
        vaultUpdatesBankClient.expectEvents {
            // MOVE
            expect { update ->
                require(update.consumed.isEmpty()) { update.consumed.size }
                require(update.produced.size == 1) { update.produced.size }
                val paidState = update.produced.single().state.data as Cash.State
                require(paidState.owner.owningKey in bankClientNode.services.keyManagementService.keys)
            }
        }
    }

    @Test
    fun `test issuer flow rejects restricted`() {
        val notary = notaryNode.services.myInfo.notaryIdentity
        // try to issue an amount of a restricted currency
        assertFailsWith<FlowException> {
            runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(100000L, currency("BRL")),
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123), notary).getOrThrow()
        }
    }

    @Test
    fun `test issue flow to self`() {
        val notary = notaryNode.services.myInfo.notaryIdentity
        val vaultUpdatesBoc = bankOfCordaNode.database.transaction {
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, vaultUpdatesBoc) = bankOfCordaNode.services.vaultQueryService.trackBy<Cash.State>(criteria)

            // using default IssueTo Party Reference
            runIssuerAndIssueRequester(bankOfCordaNode, bankOfCordaNode, 1000000.DOLLARS,
                    bankOfCordaNode.info.legalIdentity, OpaqueBytes.of(123), notary).getOrThrow()
            vaultUpdatesBoc
        }

        // Check Bank of Corda Vault Updates
        vaultUpdatesBoc.expectEvents {
            sequence(
                    // ISSUE
                    expect { update ->
                        require(update.consumed.isEmpty()) { "Expected 0 consumed states, actual: $update" }
                        require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                    }
            )
        }
    }

    @Test
    fun `test concurrent issuer flow`() {
        val notary = notaryNode.services.myInfo.notaryIdentity
        // this test exercises the Cashflow issue and move subflows to ensure consistent spending of issued states
        val amount = 10000.DOLLARS
        val amounts = calculateRandomlySizedAmounts(10000.DOLLARS, 10, 10, Random())
        val handles = amounts.map { pennies ->
            runIssuerAndIssueRequester(bankOfCordaNode, bankClientNode, Amount(pennies, amount.token),
                    bankClientNode.info.legalIdentity, OpaqueBytes.of(123), notary)
        }
        handles.forEach {
            require(it.get().stx is SignedTransaction)
        }
    }

    private fun runIssuerAndIssueRequester(issuerNode: MockNode,
                                           issueToNode: MockNode,
                                           amount: Amount<Currency>,
                                           issueToParty: Party,
                                           ref: OpaqueBytes,
                                           notaryParty: Party): ListenableFuture<AbstractCashFlow.Result> {
        val issueToPartyAndRef = issueToParty.ref(ref)
        val issueRequest = IssuanceRequester(amount, issueToParty, issueToPartyAndRef.reference, issuerNode.info.legalIdentity, notaryParty,
                anonymous)
        return issueToNode.services.startFlow(issueRequest).resultFuture
    }
}