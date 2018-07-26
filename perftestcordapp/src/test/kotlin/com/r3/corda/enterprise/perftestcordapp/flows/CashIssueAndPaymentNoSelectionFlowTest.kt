/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.enterprise.perftestcordapp.flows

import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.`issued by`
import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.trackBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.*
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class CashIssueAndPayNoSelectionTests(private val anonymous: Boolean) {
    companion object {
        @Parameterized.Parameters(name = "Anonymous = {0}")
        @JvmStatic
        fun data() = listOf(false, true)
    }

    private lateinit var mockNet: InternalMockNetwork
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: TestStartedNode
    private lateinit var bankOfCorda: Party
    private lateinit var aliceNode: TestStartedNode
    private lateinit var notary: Party

    @Before
    fun start() {
        mockNet = InternalMockNetwork(servicePeerAllocationStrategy = RoundRobin(),
                cordappsForAllNodes = cordappsForPackages("com.r3.corda.enterprise.perftestcordapp.contracts.asset", "com.r3.corda.enterprise.perftestcordapp.schemas"))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bankOfCorda = bankOfCordaNode.info.singleIdentity()
        mockNet.runNetwork()
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `issue and pay some cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expectedPayment = 500.DOLLARS

        bankOfCordaNode.database.transaction {
            // Register for vault updates
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val (_, vaultUpdatesBoc)
                    = bankOfCordaNode.services.vaultService.trackBy<Cash.State>(criteria)
            val (_, vaultUpdatesBankClient)
                    = aliceNode.services.vaultService.trackBy<Cash.State>(criteria)

            val future = bankOfCordaNode.services.startFlow(CashIssueAndPaymentNoSelection(
                    expectedPayment, OpaqueBytes.of(1), payTo, anonymous, notary)).resultFuture
            mockNet.runNetwork()
            future.getOrThrow()

            // Check bank of corda vault - should see two consecutive updates of issuing $500
            // and paying $500 to alice
            vaultUpdatesBoc.expectEvents {
                sequence(
                        expect { update ->
                            require(update.produced.size == 1) { "Expected 1 produced states, actual: $update" }
                            val changeState = update.produced.single().state.data
                            assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), changeState.amount)
                        },
                        expect { update ->
                            require(update.consumed.size == 1) { "Expected 1 consumed states, actual: $update" }
                        }
                )
            }

            // Check notary node vault updates
            vaultUpdatesBankClient.expectEvents {
                expect { (consumed, produced) ->
                    require(consumed.isEmpty()) { consumed.size }
                    require(produced.size == 1) { produced.size }
                    val paymentState = produced.single().state.data
                    assertEquals(expectedPayment.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
                }
            }
        }
    }
}
