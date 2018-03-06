/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.docs

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.*
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class CustomVaultQueryTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.finance", "net.corda.docs"))
        nodeA = mockNet.createPartyNode()
        nodeB = mockNet.createPartyNode()
        nodeA.registerInitiatedFlow(TopupIssuerFlow.TopupIssuer::class.java)
        notary = mockNet.defaultNotaryIdentity
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
        nodeA.startFlow(CashIssueFlow(amountToIssue,
                OpaqueBytes.of(0x01),
                notary)).getOrThrow()
    }

    private fun topUpCurrencies() {
        nodeA.startFlow(TopupIssuerFlow.TopupIssuanceRequester(
                nodeA.info.singleIdentity(),
                OpaqueBytes.of(0x01),
                nodeA.info.singleIdentity(),
                notary)).getOrThrow()
    }

    private fun getBalances(): Pair<Map<Currency, Amount<Currency>>, Map<Currency, Amount<Currency>>> {
        // Print out the balances
        val balancesNodesA = nodeA.transaction {
            nodeA.services.getCashBalances()
        }
        println("BalanceA\n" + balancesNodesA)

        val balancesNodesB = nodeB.transaction {
            nodeB.services.getCashBalances()
        }
        println("BalanceB\n" + balancesNodesB)

        return Pair(balancesNodesA, balancesNodesB)
    }
}
