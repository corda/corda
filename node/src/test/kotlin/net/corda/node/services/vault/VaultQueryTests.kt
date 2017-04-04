package net.corda.node.services.vault

import io.requery.kotlin.eq
import io.requery.query.Operator
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.contracts.testing.fillWithSomeTestDeals
import net.corda.contracts.testing.fillWithSomeTestLinearStates
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.LinearStateQueryCriteria
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LogicalExpression
import net.corda.core.node.services.vault.QueryCriteria.TimeInstantType
import net.corda.core.node.services.vault.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.VaultQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.node.services.vault.schemas.VaultSchema
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MINI_CORP
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.math.BigInteger
import java.security.KeyPair
import java.time.temporal.ChronoUnit
import java.util.*

class VaultQueryTests {
    lateinit var services: MockServices
    val vaultSvc: VaultService get() = services.vaultService
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val dataSourceProps = makeTestDataSourceProperties()
        val dataSourceAndDatabase = configureDatabase(dataSourceProps)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        databaseTransaction(database) {
            services = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(txs.map { it.tx })
                }
            }
        }
    }

    @After
    fun tearDown() {
        dataSource.close()
        LogHelper.reset(NodeVaultService::class)
    }

    /**
     * Query API tests
     */

    /** Generic Query tests
        (combining both FungibleState and LinearState contract types) */

    @Test
    fun `unconsumed states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(16)
        }
    }

    val CASH_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
    val CASH_NOTARY: Party get() = Party("Notary Service", CASH_NOTARY_KEY.public)

    @Test
    fun `unconsumed states by notary`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = VaultQueryCriteria(notary = CASH_NOTARY)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed states excluding soft locks`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            vaultSvc.softLockReserve(UUID.randomUUID(), setOf(issuedStates.states.first().ref, issuedStates.states.last().ref))

            val criteria = VaultQueryCriteria(includeSoftlocks = false)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed states recorded between two time intervals`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val start = TEST_TX_TIME
            val end = TEST_TX_TIME.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.RECORDED, Operator.BETWEEN, arrayOf(start, end))
            val criteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `states consumed after time`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val asOfDateTime = TEST_TX_TIME
            val consumedAfterExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.CONSUMED, Operator.GREATER_THAN, arrayOf(asOfDateTime))
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                    timeCondition = consumedAfterExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `consumed states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123","456","789"))

//            services.consumeLinearStates(UniqueIdentifier("TEST"))
//            services.consumeDeals("456")
//            services.consumeCash(80.DOLLARS)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(12)
        }
    }

    @Test
    fun `all states with paging specification`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))


            paging = VaultQueryCriteria.PageSpecification()
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(12)
        }
    }

    /** LinearState tests */

    @Test
    fun `unconsumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)

            val criteria = LinearStateQueryCriteria()
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(10)
        }
    }

    @Test
    fun `consumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
// services.spendCash()
            services.fillWithSomeTestLinearStates(10)
//            services.consumeLinearStates(8)

            val criteria = LinearStateQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads for linearId`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(10)

            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = LinearStateQueryCriteria(linearId = listOf(linearIds.first(), linearIds.last()) )
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `latest unconsumed linear heads for linearId`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)

            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = LinearStateQueryCriteria(linearId = listOf(linearIds.first()),
                    latestOnly = true)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed linear heads for state refs`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            val stateRefs = issuedStates.states.map { it.ref }.toList()

            val criteria = LinearStateQueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()),
                    latestOnly = true)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed deals`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = LinearStateQueryCriteria()
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed deals for ref`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123","456","789"))

            val criteria = LinearStateQueryCriteria(dealRef = listOf("456"))
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals for ref`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST"))
            services.fillWithSomeTestDeals(listOf("456"), 3)        // create 3 revisions with same ID
            services.fillWithSomeTestDeals(listOf("123", "789"))

            val criteria = LinearStateQueryCriteria(dealRef = listOf("456"), latestOnly = true)
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals with party`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST"))
            services.fillWithSomeTestDeals(listOf("456"), 3)        // specify party
            services.fillWithSomeTestDeals(listOf("123", "789"))

            val criteria = LinearStateQueryCriteria(dealParties = listOf(MEGA_CORP, MINI_CORP))
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    /** Custom Query tests */

    @Test
    fun `consumed linear heads for linearId between two timestamps`() {
        databaseTransaction(database) {
            val issuedStates = services.fillWithSomeTestLinearStates(10)
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val filterByLinearIds = listOf(linearIds.first(), linearIds.last())

            val start = TEST_TX_TIME
            val end = TEST_TX_TIME.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = LogicalExpression(TimeInstantType.RECORDED, Operator.BETWEEN, arrayOf(start, end))

            // TODO: enforce strict type safety of Attributes (via Enum) ???
            val linearIdsExpression = LogicalExpression(LinearState::linearId, Operator.IN, filterByLinearIds)
            val linearIdCondition = (VaultSchema.VaultLinearState::uuid eq linearIds[2])

            val compositeExpression = recordedBetweenExpression.and(linearIdsExpression).or(linearIdCondition)

            val criteria = VaultCustomQueryCriteria(compositeExpression)
            val states = vaultSvc.queryBy<LinearState>(criteria)

            assertThat(states).hasSize(2)
        }
    }
}
