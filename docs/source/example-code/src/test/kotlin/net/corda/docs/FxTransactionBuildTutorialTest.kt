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

import net.corda.core.identity.Party
import net.corda.core.toFuture
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.*
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FxTransactionBuildTutorialTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.finance"))
        nodeA = mockNet.createPartyNode()
        nodeB = mockNet.createPartyNode()
        nodeB.registerInitiatedFlow(ForeignExchangeRemoteFlow::class.java)
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Run ForeignExchangeFlow to completion`() {
        // Use NodeA as issuer and create some dollars and wait for the flow to stop
        nodeA.startFlow(CashIssueFlow(DOLLARS(1000),
                OpaqueBytes.of(0x01),
                notary)).getOrThrow()
        printBalances()

        // Using NodeB as Issuer create some pounds and wait for the flow to stop
        nodeB.startFlow(CashIssueFlow(POUNDS(1000),
                OpaqueBytes.of(0x01),
                notary)).getOrThrow()
        printBalances()

        // Setup some futures on the vaults to await the arrival of the exchanged funds at both nodes
        val nodeAVaultUpdate = nodeA.services.vaultService.updates.toFuture()
        val nodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()

        // Now run the actual Fx exchange and wait for the flow to finish
        nodeA.startFlow(ForeignExchangeFlow("trade1",
                POUNDS(100).issuedBy(nodeB.info.singleIdentity().ref(0x01)),
                DOLLARS(200).issuedBy(nodeA.info.singleIdentity().ref(0x01)),
                nodeB.info.singleIdentity(),
                weAreBaseCurrencySeller = false)).getOrThrow()
        // wait for the flow to finish and the vault updates to be done
        // Get the balances when the vault updates
        nodeAVaultUpdate.get()
        val balancesA = nodeA.transaction {
            nodeA.services.getCashBalances()
        }
        nodeBVaultUpdate.get()
        val balancesB = nodeB.transaction {
            nodeB.services.getCashBalances()
        }

        println("BalanceA\n" + balancesA)
        println("BalanceB\n" + balancesB)
        // Verify the transfers occurred as expected
        assertEquals(POUNDS(100), balancesA[GBP])
        assertEquals(DOLLARS(1000 - 200), balancesA[USD])
        assertEquals(POUNDS(1000 - 100), balancesB[GBP])
        assertEquals(DOLLARS(200), balancesB[USD])
    }

    private fun printBalances() {
        // Print out the balances
        nodeA.transaction {
            println("BalanceA\n" + nodeA.services.getCashBalances())
        }
        nodeB.transaction {
            println("BalanceB\n" + nodeB.services.getCashBalances())
        }
    }
}
