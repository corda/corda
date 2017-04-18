package net.corda.node.services.vault

import io.requery.kotlin.eq
import io.requery.query.Operator
import io.requery.query.Order
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.contracts.testing.fillWithSomeTestDeals
import net.corda.contracts.testing.fillWithSomeTestLinearStates
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.node.services.vault.schemas.VaultSchema
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.schemas.CashSchemaV1
import net.corda.testing.*
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
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample1
            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample1
            assertThat(states).hasSize(16)
        }
    }

    @Test
    fun `unconsumed states for state refs`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2)
            val stateRefs = issuedStates.states.map { it.ref }.toList()
            services.fillWithSomeTestLinearStates(8)

            // DOCSTART VaultQueryExample2
            val vaultCriteria = VaultQueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()))
            val states = vaultSvc.queryBy<LinearState>(vaultCriteria)
            // DOCEND VaultQueryExample2

            assertThat(states).hasSize(2)
            assertThat(states.first().ref).isEqualTo(issuedStates.states.first().ref)
            assertThat(states.last().ref).isEqualTo(issuedStates.states.last().ref)
        }
    }

    @Test
    fun `unconsumed states for contract state types`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // default State.Status is UNCONSUMED
            // DOCSTART VaultQueryExample3
            val criteria = VaultQueryCriteria(contractStateTypes = setOf(Cash.State::class.java, DealState::class.java))
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample3
            assertThat(states).hasSize(6)
        }
    }

    @Test
    fun `consumed states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

//            services.consumeLinearStates(UniqueIdentifier("TEST"))
//            services.consumeDeals("456")
//            services.consumeCash(80.DOLLARS)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            assertThat(states).hasSize(5)
        }
    }

    @Test
    fun `all states`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

//            services.consumeLinearStates(UniqueIdentifier("TEST"))
//            services.consumeDeals("456")
//            services.consumeCash(80.DOLLARS)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
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
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample4
            val criteria = VaultQueryCriteria(notary = setOf(CASH_NOTARY))
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample4
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
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample5
            val start = TEST_TX_TIME
            val end = TEST_TX_TIME.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.RECORDED, Operator.BETWEEN, arrayOf(start, end))
            val criteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample5
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `states consumed after time`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample6
            val asOfDateTime = TEST_TX_TIME
            val consumedAfterExpression = QueryCriteria.LogicalExpression(
                    QueryCriteria.TimeInstantType.CONSUMED, Operator.GREATER_THAN, arrayOf(asOfDateTime))
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                                              timeCondition = consumedAfterExpression)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample6
            assertThat(states).hasSize(3)
        }
    }

    // pagination: first page
    @Test
    fun `all states with paging specification - first page`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            // DOCSTART VaultQueryExample7
            val pagingSpec = QueryCriteria.PageSpecification(1, 10)
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val states = vaultSvc.queryBy<ContractState>(criteria, paging = pagingSpec)
            // DOCEND VaultQueryExample7
            assertThat(states).hasSize(10)
        }
    }

    // pagination: last page
    @Test
    fun `all states with paging specification  - last`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            // Last page implies we need to perform a row count for the Query first,
            // and then re-query for a given offset defined by (count - pageSize)
            val pagingSpec = QueryCriteria.PageSpecification(-1, 10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val states = vaultSvc.queryBy<ContractState>(criteria, paging = pagingSpec)
            assertThat(states).hasSize(10) // should retrieve states 90..99
        }
    }

    @Test
    fun `unconsumed fungible assets`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
//            services.fillWithSomeTestCommodity()
            services.fillWithSomeTestLinearStates(10)

            val criteria = VaultQueryCriteria(contractStateTypes = setOf(FungibleAsset::class.java)) // default is UNCONSUMED
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(4)
        }
    }

    @Test
    fun `consumed fungible assets`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
//          services.consumeCash(2)
//            services.fillWithSomeTestCommodity()
//            services.consumeCommodity()
            services.fillWithSomeTestLinearStates(10)
//          services.consumeLinearStates(8)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                                              contractStateTypes = setOf(FungibleAsset::class.java))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash fungible assets`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)

            val criteria = VaultQueryCriteria(contractStateTypes = setOf(Cash.State::class.java)) // default is UNCONSUMED
            val states = vaultSvc.queryBy<Cash.State>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `consumed cash fungible assets`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
//          services.consumeCash(2)
            services.fillWithSomeTestLinearStates(10)
//          services.consumeLinearStates(8)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val states = vaultSvc.queryBy<Cash.State>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)

            val criteria = VaultQueryCriteria(contractStateTypes = setOf(LinearState::class.java)) // default is UNCONSUMED
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(10)
        }
    }

    @Test
    fun `consumed linear heads`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
//          services.consumeLinearStates(8)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                                              contractStateTypes = setOf(LinearState::class.java))
            val states = vaultSvc.queryBy<LinearState>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    /** LinearState tests */

    @Test
    fun `unconsumed linear heads for linearId`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(10)

            // DOCSTART VaultQueryExample8
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = LinearStateQueryCriteria(linearId = listOf(linearIds.first(), linearIds.last()))
            val states = vaultSvc.queryBy<LinearState>(criteria)
            // DOCEND VaultQueryExample8
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
    fun `return chain of linear state for a given id`() {
        databaseTransaction(database) {

            val id = UniqueIdentifier("TEST")
            val issuedStates = services.fillWithSomeTestLinearStates(1, UniqueIdentifier("TEST"))
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with UniqueIdentifier("TEST")
            // DOCSTART VaultQueryExample9
            val linearStateCriteria = LinearStateQueryCriteria(linearId = listOf(id))
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val states = vaultSvc.queryBy<LinearState>(linearStateCriteria.and(vaultCriteria), ordering = Order.DESC)
            // DOCEND VaultQueryExample9
            assertThat(states).hasSize(4)
        }
    }

    @Test
    fun `DEPRECATED return linear states for a given id`() {
        databaseTransaction(database) {

            val linearUid = UniqueIdentifier("TEST")
            val issuedStates = services.fillWithSomeTestLinearStates(1, UniqueIdentifier("TEST"))
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with UniqueIdentifier("TEST")

            // DOCSTART VaultDeprecatedQueryExample1
            val states = vaultSvc.linearHeadsOfType<LinearState>().filter { it.key == linearUid }
            // DOCEND VaultDeprecatedQueryExample1

            assertThat(states).hasSize(4)
        }
    }

    @Test
    fun `DEPRECATED return consumed linear states for a given id`() {
        databaseTransaction(database) {

            val linearUid = UniqueIdentifier("TEST")
            val issuedStates = services.fillWithSomeTestLinearStates(1, UniqueIdentifier("TEST"))
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference
//            services.processLinearState(id)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with UniqueIdentifier("TEST")

            // DOCSTART VaultDeprecatedQueryExample2
            val states = vaultSvc.states(setOf(LinearState::class.java),
                                         EnumSet.of(Vault.StateStatus.CONSUMED)).filter { it.state.data.linearId == linearUid }
            // DOCEND VaultDeprecatedQueryExample2

            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `latest unconsumed linear heads for state refs`() {
        databaseTransaction(database) {

            val issuedStates = services.fillWithSomeTestLinearStates(2, UniqueIdentifier("TEST")) // create 2 states with same UID
            services.fillWithSomeTestLinearStates(8)
            val stateRefs = issuedStates.states.map { it.ref }.toList()

            val vaultCriteria = VaultQueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()))
            val linearStateCriteria = LinearStateQueryCriteria(latestOnly = true)
            val states = vaultSvc.queryBy<LinearState>(vaultCriteria.and(linearStateCriteria))
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed deals`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val criteria = LinearStateQueryCriteria()
            val states = vaultSvc.queryBy<DealState>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed deals for ref`() {
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample10
            val criteria = LinearStateQueryCriteria(dealRef = listOf("456", "789"))
            val states = vaultSvc.queryBy<DealState>(criteria)
            // DOCEND VaultQueryExample10

            assertThat(states).hasSize(2)
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

            // DOCSTART VaultQueryExample11
            val criteria = LinearStateQueryCriteria(dealParties = listOf(MEGA_CORP, MINI_CORP))
            val states = vaultSvc.queryBy<DealState>(criteria)
            // DOCEND VaultQueryExample11

            assertThat(states).hasSize(1)
        }
    }

    /** FungibleAsset tests */
    @Test
    fun `unconsumed fungible assets of token type`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            val criteria = FungibleAssetQueryCriteria(tokenType = setOf(Currency::class.java))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(9)
        }
    }

    @Test
    fun `unconsumed fungible assets for single currency`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            // DOCSTART VaultQueryExample12
            val criteria = FungibleAssetQueryCriteria(tokenValue = setOf(USD.currencyCode))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample12

            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed fungible assets for single currency and quantity greater than`() {
        databaseTransaction(database) {

            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(50.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            // DOCSTART VaultQueryExample13
            val criteria = FungibleAssetQueryCriteria(tokenValue = setOf(GBP.currencyCode),
                                                      quantity = LogicalExpression(this, Operator.GREATER_THAN, 50))
            val states = vaultSvc.queryBy<Cash.State>(criteria)
            // DOCEND VaultQueryExample13

            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed fungible assets for several currencies`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            val criteria = FungibleAssetQueryCriteria(tokenValue = setOf(CHF.currencyCode, GBP.currencyCode))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed fungible assets for issuer party`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY)

            // DOCSTART VaultQueryExample14
            val criteria = FungibleAssetQueryCriteria(issuerParty = setOf(BOC))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample14

            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed fungible assets for specific issuer party and refs`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(1))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(2)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(2))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(3)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(3))

            val criteria = FungibleAssetQueryCriteria(issuerParty = setOf(BOC), issuerRef = setOf(BOC.ref(1).reference, BOC.ref(2).reference))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed fungible assets with exit keys`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY)

            // DOCSTART VaultQueryExample15
            val criteria = FungibleAssetQueryCriteria(exitKeys = setOf(DUMMY_CASH_ISSUER_KEY.public.composite))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample15

            assertThat(states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed fungible assets by owner`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            // issue some cash to BOB
            // issue some cash to ALICE

            val criteria = FungibleAssetQueryCriteria(owner = setOf(BOB, ALICE))
            val states = vaultSvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(states).hasSize(1)
        }
    }

    /** Custom Query tests */

    // specifying Order
    @Test
    fun `all states with paging specification reverse order`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            val statusAllExpression = LogicalExpression(VaultSchema.VaultStates::stateStatus, Operator.EQUAL, Vault.StateStatus.ALL)
            val customCriteria = VaultCustomQueryCriteria(expression = statusAllExpression)

            val pagingSpec = PageSpecification(1, 10)
            val states = vaultSvc.queryBy<ContractState>(customCriteria, ordering = Order.DESC, paging = pagingSpec)
            assertThat(states).hasSize(10)
        }
    }

    // specifying Order by custom attribute
    @Test
    fun `all states with paging specification reverse chronological order by recorded timestamp`() {

    }

    // specifying Query on custom Contract state attributes
    @Test
    fun `unconsumed cash states with amount of currency greater or equal than`() {

        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            // DOCSTART VaultQueryExample16
            val statusUnconsumedExpr = LogicalExpression(VaultSchema.VaultStates::stateStatus, Operator.EQUAL, Vault.StateStatus.UNCONSUMED)
            val currencyExpr = LogicalExpression(CashSchemaV1.PersistentCashState::currency, Operator.EQUAL, USD.currencyCode)
            val quantityExpr = LogicalExpression(CashSchemaV1.PersistentCashState::pennies, Operator.GREATER_THAN_OR_EQUAL, 10)
            val combinedExpr = statusUnconsumedExpr.and(currencyExpr).and(quantityExpr)

            val criteria = VaultCustomQueryCriteria(expression = combinedExpr)
            val states = vaultSvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample16

            assertThat(states).hasSize(2)
        }
    }

    // chaining LogicalExpressions with AND and OR
    @Test
    fun `consumed linear heads for linearId between two timestamps`() {
        databaseTransaction(database) {
            val issuedStates = services.fillWithSomeTestLinearStates(10)
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val filterByLinearIds = listOf(linearIds.first(), linearIds.last())

            // DOCSTART VaultQueryExample17
            val start = TEST_TX_TIME
            val end = TEST_TX_TIME.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = LogicalExpression(TimeInstantType.RECORDED, Operator.BETWEEN, arrayOf(start, end))

            // TODO: enforce strict type safety of Attributes (via Enum) ???
            val linearIdsExpression = LogicalExpression(LinearState::linearId, Operator.IN, filterByLinearIds)
            val linearIdCondition = (VaultSchema.VaultLinearState::uuid eq linearIds[2])

            val compositeExpression = recordedBetweenExpression.and(linearIdsExpression).or(linearIdCondition)

            val criteria = VaultCustomQueryCriteria(compositeExpression)
            val states = vaultSvc.queryBy<LinearState>(criteria)
            // DOCEND VaultQueryExample17

            assertThat(states).hasSize(2)
        }
    }

    // chaining QueryCriteria specifications
    @Test
    fun `all cash states with amount of currency greater or equal than`() {

        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            // services.spend(100.DOLLARS)
            services.fillWithSomeTestCash(10.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            // custom criteria
            val currencyExpr = LogicalExpression(CashSchemaV1.PersistentCashState::currency, Operator.EQUAL, USD.currencyCode)
            val quantityExpr = LogicalExpression(CashSchemaV1.PersistentCashState::pennies, Operator.GREATER_THAN_OR_EQUAL, 10)
            val customCriteria = VaultCustomQueryCriteria(currencyExpr.and(quantityExpr))

            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)

            val states = vaultSvc.queryBy<ContractState>(vaultCriteria.and(customCriteria))
            assertThat(states).hasSize(2)
        }
    }

    /**
     *  USE CASE demonstrations (outside of mainline Corda)
     *
     *  1) Template / Tutorial CorDapp service using Vault API Custom Query to access attributes of IOU State
     *  2) Template / Tutorial Flow using a JDBC session to execute a custom query
     *  3) Template / Tutorial CorDapp service query extension executing Named Queries via JPA
     *  4) Advanced pagination queries using Spring Data (and/or Hibernate/JPQL)
     */
}