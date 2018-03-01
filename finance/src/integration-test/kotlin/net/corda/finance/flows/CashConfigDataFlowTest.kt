package net.corda.finance.flows

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.EUR
import net.corda.finance.USD
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CashConfigDataFlowTest {
    @Test
    fun `issuable currencies are read in from node config`() {
        driver {
            val node = startNode(customOverrides = mapOf("custom" to mapOf("issuableCurrencies" to listOf("EUR", "USD")))).getOrThrow()
            val config = node.rpc.startFlow(::CashConfigDataFlow).returnValue.getOrThrow()
            assertThat(config.issuableCurrencies).containsExactly(EUR, USD)
        }
    }
}