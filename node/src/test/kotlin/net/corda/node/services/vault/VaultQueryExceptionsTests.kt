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

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.packageName
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.sampleschemas.SampleCashSchemaV3
import net.corda.finance.schemas.CashSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import net.corda.testing.node.makeTestIdentityService
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.*
import org.junit.rules.ExpectedException

class VaultQueryExceptionsTests {
    private companion object {
        val bankOfCorda = TestIdentity(BOC_NAME)
        val cashNotary = TestIdentity(CordaX500Name("Cash Notary Service", "Zurich", "CH"), 21)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val DUMMY_CASH_ISSUER = dummyCashIssuer.ref(1)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val BOC_IDENTITY get() = bankOfCorda.identity
        val BOC_KEY get() = bankOfCorda.keyPair
        val CASH_NOTARY get() = cashNotary.party
        val CASH_NOTARY_IDENTITY get() = cashNotary.identity
        val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair
        val MEGA_CORP_IDENTITY get() = megaCorp.identity
        val MEGA_CORP_KEY get() = megaCorp.keyPair
        val MINI_CORP_IDENTITY get() = miniCorp.identity

        private val cordappPackages = listOf(
                "net.corda.testing.contracts",
                "net.corda.finance.contracts",
                CashSchemaV1::class.packageName,
                DummyLinearStateSchemaV1::class.packageName) - SampleCashSchemaV3::class.packageName

        private lateinit var services: MockServices
        private lateinit var vaultFiller: VaultFiller
        private lateinit var vaultFillerCashNotary: VaultFiller
        private lateinit var notaryServices: MockServices
        private val vaultService: VaultService get() = services.vaultService
        private lateinit var identitySvc: IdentityService
        private lateinit var database: CordaPersistence


        @BeforeClass @JvmStatic
        fun setUpClass() {
            // register additional identities
            val databaseAndServices = makeTestDatabaseAndMockServices(
                    cordappPackages,
                    makeTestIdentityService(Companion.MEGA_CORP_IDENTITY, Companion.MINI_CORP_IDENTITY, Companion.dummyCashIssuer.identity, Companion.dummyNotary.identity),
                    Companion.megaCorp,
                    moreKeys = Companion.DUMMY_NOTARY_KEY)
            database = databaseAndServices.first
            services = databaseAndServices.second
            vaultFiller = VaultFiller(services, Companion.dummyNotary)
            vaultFillerCashNotary = VaultFiller(services, Companion.dummyNotary, Companion.CASH_NOTARY)
            notaryServices = MockServices(cordappPackages, Companion.dummyNotary, rigorousMock(), Companion.dummyCashIssuer.keyPair, Companion.BOC_KEY, Companion.MEGA_CORP_KEY)
            identitySvc = services.identityService
            // Register all of the identities we're going to use
            (notaryServices.myInfo.legalIdentitiesAndCerts + Companion.BOC_IDENTITY + Companion.CASH_NOTARY_IDENTITY + Companion.MINI_CORP_IDENTITY + Companion.MEGA_CORP_IDENTITY).forEach { identity ->
                services.identityService.verifyAndRegisterIdentity(identity)
            }
        }
    }

    private lateinit var transaction: DatabaseTransaction


    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val expectedEx: ExpectedException = ExpectedException.none()

    @Before
    fun setUp() {
        transaction = database.newTransaction()
    }

    @After
    fun tearDown() {
        transaction.rollback()
        transaction.close()
    }

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