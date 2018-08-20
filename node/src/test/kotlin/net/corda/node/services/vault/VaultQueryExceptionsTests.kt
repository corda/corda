package net.corda.node.services.vault

import net.corda.core.internal.packageName
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.SampleCashSchemaV3
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.core.*
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.*
import org.junit.rules.ExpectedException

class VaultQueryExceptionsTests : VaultQueryParties by rule {

    companion object {
        @ClassRule
        @JvmField
        val rule = object : VaultQueryTestRule() {
            override val cordappPackages = listOf(
                    "net.corda.testing.contracts",
                    "net.corda.finance.contracts",
                    DummyLinearStateSchemaV1::class.packageName)
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val expectedEx: ExpectedException = ExpectedException.none()

    @Rule
    @JvmField
    val rollbackRule = VaultQueryRollbackRule(this)

    @Test
    fun `query attempting to use unregistered schema`() {
        database.transaction {
            // CashSchemaV3 NOT registered with NodeSchemaService
            val logicalExpression = builder { SampleCashSchemaV3.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)

            assertThatThrownBy {
                vaultService.queryBy<Cash.State>(criteria)
            }.isInstanceOf(VaultQueryException::class.java).hasMessageContaining("Please register the entity")
        }
    }
}
