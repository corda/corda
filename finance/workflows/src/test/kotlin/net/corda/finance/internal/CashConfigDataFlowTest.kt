package net.corda.finance.internal

import net.corda.core.utilities.getOrThrow
import net.corda.finance.EUR
import net.corda.finance.USD
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
}
