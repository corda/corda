/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.asset.cash.selection

import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashException
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Test
import java.util.Collections.nCopies
import kotlin.test.assertNotNull

class CashSelectionH2ImplTest {
    private val mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.finance"))

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `selecting pennies amount larger than max int, which is split across multiple cash states`() {
        val node = mockNet.createNode()
        // The amount has to split across at least two states, probably to trigger the H2 accumulator variable during the
        // spend operation below.
        // Issuing Integer.MAX_VALUE will not cause an exception since PersistentCashState.pennies is a long
        nCopies(2, Integer.MAX_VALUE).map { issueAmount ->
            node.startFlow(CashIssueFlow(issueAmount.POUNDS, OpaqueBytes.of(1), mockNet.defaultNotaryIdentity))
        }.transpose().getOrThrow()
        // The spend must be more than the size of a single cash state to force the accumulator onto the second state.
        node.startFlow(CashPaymentFlow((Integer.MAX_VALUE + 1L).POUNDS, node.info.legalIdentities[0])).getOrThrow()
    }

    @Test
    fun `check does not hold connection over retries`() {
        val bankA = mockNet.createNode(MockNodeParameters(configOverrides = {
            // Tweak connections to be minimal to make this easier (1 results in a hung node during start up, so use 2 connections).
            it.dataSourceProperties.setProperty("maximumPoolSize", "2")
        }))
        val notary = mockNet.defaultNotaryIdentity

        // Start more cash spends than we have connections.  If spend leaks a connection on retry, we will run out of connections.
        val flow1 = bankA.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notary))
        val flow2 = bankA.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notary))
        val flow3 = bankA.startFlow(CashPaymentFlow(amount = 100.DOLLARS, anonymous = false, recipient = notary))

        assertThatThrownBy { flow1.getOrThrow() }.isInstanceOf(CashException::class.java)
        assertThatThrownBy { flow2.getOrThrow() }.isInstanceOf(CashException::class.java)
        assertThatThrownBy { flow3.getOrThrow() }.isInstanceOf(CashException::class.java)
    }

    @Test
    fun `select pennies amount from cash states with more than two different issuers and expect change`() {
        val node = mockNet.createNode()
        val notary = mockNet.defaultNotaryIdentity

        // Issue some cash
        node.startFlow(CashIssueFlow(1.POUNDS, OpaqueBytes.of(1), notary)).getOrThrow()
        node.startFlow(CashIssueFlow(1.POUNDS, OpaqueBytes.of(2), notary)).getOrThrow()
        node.startFlow(CashIssueFlow(1000.POUNDS, OpaqueBytes.of(3), notary)).getOrThrow()

        // Make a payment
        val paymentResult = node.startFlow(CashPaymentFlow(999.POUNDS, node.info.legalIdentities[0], false)).getOrThrow()
        assertNotNull(paymentResult.recipient)
    }
}