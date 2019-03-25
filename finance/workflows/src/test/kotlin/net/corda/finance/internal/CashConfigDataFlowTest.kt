package net.corda.finance.internal

import net.corda.core.utilities.OpaqueBytes
import org.assertj.core.api.Assertions.assertThatThrownBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.EUR
import net.corda.finance.POUNDS
import net.corda.finance.USD
import net.corda.finance.flows.CashException
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class CashConfigDataFlowTest {
    private val mockNet = MockNetwork(MockNetworkParameters(threadPerNode = true))

    @After
    fun cleanUp() = mockNet.stopNodes()

    @Test
    fun `issuable currencies read in from cordapp config`() {
        val node = mockNet.createNode(MockNodeParameters(
                additionalCordapps = listOf(FINANCE_WORKFLOWS_CORDAPP.withConfig(mapOf("issuableCurrencies" to listOf("EUR", "USD"))))
        ))
        val config = node.startFlow(CashConfigDataFlow()).getOrThrow()
        assertThat(config.issuableCurrencies).containsExactly(EUR, USD)
    }

    @Test
    fun `cannot issue unsupported currency`() {
        val node = mockNet.createNode(MockNodeParameters(
                additionalCordapps = listOf(FINANCE_WORKFLOWS_CORDAPP.withConfig(mapOf("issuableCurrencies" to listOf("EUR", "USD"))))
        ))

        val notary = mockNet.defaultNotaryIdentity
        node.startFlow(CashConfigDataFlow()).getOrThrow()

        val issuerBankPartyRef = OpaqueBytes.of("1".toByte())
        val future = node.startFlow(CashIssueFlow(amount = 100.POUNDS, issuerBankPartyRef = issuerBankPartyRef, notary = notary))

        assertThatThrownBy {
            future.getOrThrow()
        }.isInstanceOf(CashException::class.java).hasMessage("Unsupported currency requested")
    }
}
