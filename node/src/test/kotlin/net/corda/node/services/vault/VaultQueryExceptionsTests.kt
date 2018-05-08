/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
        @ClassRule @JvmField
        val rule = object : VaultQueryTestRule() {
            override val cordappPackages = listOf(
                    "net.corda.testing.contracts",
                    "net.corda.finance.contracts",
                    CashSchemaV1::class.packageName,
                    DummyLinearStateSchemaV1::class.packageName) - SampleCashSchemaV3::class.packageName
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val expectedEx: ExpectedException = ExpectedException.none()

    @Test
    fun `query attempting to use unregistered schema`() {
        database.transaction {
            vaultFiller.fillWithSomeTestCash(100.DOLLARS, notaryServices, 1, DUMMY_CASH_ISSUER)
            vaultFiller.fillWithSomeTestCash(100.POUNDS, notaryServices, 1, DUMMY_CASH_ISSUER)
            vaultFiller.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, 1, DUMMY_CASH_ISSUER)
            // CashSchemaV3 NOT registered with NodeSchemaService
            val logicalExpression = builder { SampleCashSchemaV3.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)

            assertThatThrownBy {
                vaultService.queryBy<Cash.State>(criteria)
            }.isInstanceOf(VaultQueryException::class.java).hasMessageContaining("Please register the entity")
        }
    }
}
