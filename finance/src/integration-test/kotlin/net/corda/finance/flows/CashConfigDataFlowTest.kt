package net.corda.finance.flows

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.EUR
import net.corda.finance.USD
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class CashConfigDataFlowTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(),
                DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }
    @Test
    fun `issuable currencies are read in from node config`() {
        driver(DriverParameters(notarySpecs = emptyList())) {
            val node = startNode(customOverrides = mapOf("custom" to mapOf("issuableCurrencies" to listOf("EUR", "USD")))).getOrThrow()
            val config = node.rpc.startFlow(::CashConfigDataFlow).returnValue.getOrThrow()
            assertThat(config.issuableCurrencies).containsExactly(EUR, USD)
        }
    }
}
