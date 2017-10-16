package net.corda.docs

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.*
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.StartedNode
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class CustomVaultQueryTest {

    lateinit var mockNet: MockNetwork
    lateinit var nodeA: StartedNode<MockNetwork.MockNode>
    lateinit var nodeB: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))
        mockNet.createNotaryNode(legalName = DUMMY_NOTARY.name)
        nodeA = mockNet.createPartyNode()
        nodeB = mockNet.createPartyNode()
        nodeA.internals.registerInitiatedFlow(TopupIssuerFlow.TopupIssuer::class.java)
        nodeA.internals.installCordaService(CustomVaultQuery.Service::class.java)
        notary = nodeA.services.getDefaultNotary()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `test custom vault query`() {
        // issue some cash in several currencies
        issueCashForCurrency(POUNDS(1000))
        issueCashForCurrency(DOLLARS(900))
        issueCashForCurrency(SWISS_FRANCS(800))
        val (cashBalancesOriginal, _) = getBalances()

        // top up all currencies (by double original)
        topUpCurrencies()
        val (cashBalancesAfterTopup, _) = getBalances()

        Assert.assertEquals(cashBalancesOriginal[GBP]?.times(2), cashBalancesAfterTopup[GBP])
        Assert.assertEquals(cashBalancesOriginal[USD]?.times(2)  , cashBalancesAfterTopup[USD])
        Assert.assertEquals(cashBalancesOriginal[CHF]?.times( 2), cashBalancesAfterTopup[CHF])
    }

    private fun issueCashForCurrency(amountToIssue: Amount<Currency>) {
        // Use NodeA as issuer and create some dollars
        val flowHandle1 = nodeA.services.startFlow(CashIssueFlow(amountToIssue,
                OpaqueBytes.of(0x01),
                notary))
        // Wait for the flow to stop and print
        flowHandle1.resultFuture.getOrThrow()
    }

    private fun topUpCurrencies() {
        val flowHandle1 = nodeA.services.startFlow(TopupIssuerFlow.TopupIssuanceRequester(
                nodeA.info.chooseIdentity(),
                OpaqueBytes.of(0x01),
                nodeA.info.chooseIdentity(),
                notary))
        flowHandle1.resultFuture.getOrThrow()
    }

    private fun getBalances(): Pair<Map<Currency, Amount<Currency>>, Map<Currency, Amount<Currency>>> {
        // Print out the balances
        val balancesNodesA =
            nodeA.database.transaction {
                nodeA.services.getCashBalances()
            }
        println("BalanceA\n" + balancesNodesA)

        val balancesNodesB =
            nodeB.database.transaction {
                nodeB.services.getCashBalances()
            }
        println("BalanceB\n" + balancesNodesB)

        return Pair(balancesNodesA, balancesNodesB)
    }
}
