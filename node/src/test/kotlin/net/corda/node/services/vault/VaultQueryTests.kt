package net.corda.node.services.vault

import net.corda.contracts.CommercialPaper
import net.corda.contracts.Commodity
import net.corda.contracts.DealState
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.Party
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.utilities.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toHexString
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.schema.NodeSchemaService
import net.corda.core.utilities.*
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.schemas.CashSchemaV1
import net.corda.schemas.CashSchemaV1.PersistentCashState
import net.corda.schemas.CommercialPaperSchemaV1
import net.corda.schemas.SampleCashSchemaV3
import net.corda.testing.*
import net.corda.testing.contracts.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDatabaseAndMockServices
import net.corda.testing.node.makeTestDatabaseProperties
import net.corda.testing.schemas.DummyLinearStateSchemaV1
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x500.X500Name
import org.junit.*
import org.junit.rules.ExpectedException
import java.lang.Thread.sleep
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

class VaultQueryTests : TestDependencyInjectionBase() {

    lateinit var services: MockServices
    val vaultSvc: VaultService get() = services.vaultService
    val vaultQuerySvc: VaultQueryService get() = services.vaultQueryService
    lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        val databaseAndServices = makeTestDatabaseAndMockServices(keys = listOf(MEGA_CORP_KEY))
        database = databaseAndServices.first
        services = databaseAndServices.second
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Helper method for generating a Persistent H2 test database
     */
    @Ignore
    @Test
    fun createPersistentTestDb() {
        val database = configureDatabase(makePersistentDataSourceProperties(), makeTestDatabaseProperties())

        setUpDb(database, 5000)

        database.close()
    }

    private fun setUpDb(_database: CordaPersistence, delay: Long = 0) {

        _database.transaction {

            // create new states
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            val linearStatesXYZ = services.fillWithSomeTestLinearStates(1, "XYZ")
            val linearStatesJKL = services.fillWithSomeTestLinearStates(2, "JKL")
            services.fillWithSomeTestLinearStates(3, "ABC")
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // Total unconsumed states = 10 + 1 + 2 + 3 + 3 = 19

            sleep(delay)

            // consume some states
            services.consumeLinearStates(linearStatesXYZ.states.toList())
            services.consumeLinearStates(linearStatesJKL.states.toList())
            services.consumeDeals(dealStates.states.filter { it.state.data.ref == "456" })
            services.consumeCash(50.DOLLARS)

            // Total unconsumed states = 4 + 3 + 2 + 1 (new cash change) = 10
            // Total consumed states = 6 + 1 + 2 + 1 = 10
        }
    }

    private fun makePersistentDataSourceProperties(): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:~/test/vault_query_persistence;DB_CLOSE_ON_EXIT=TRUE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }

    @get:Rule
    val expectedEx = ExpectedException.none()!!

    /**
     * Query API tests
     */

    /** Generic Query tests
    (combining both FungibleState and LinearState contract types) */

    @Test
    fun `unconsumed states simple`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample1
            val result = vaultQuerySvc.queryBy<ContractState>()

            /**
             * Query result returns a [Vault.Page] which contains:
             *  1) actual states as a list of [StateAndRef]
             *  2) state reference and associated vault metadata as a list of [Vault.StateMetadata]
             *  3) [PageSpecification] used to delimit the size of items returned in the result set (defaults to [DEFAULT_PAGE_SIZE])
             *  4) Total number of items available (to aid further pagination if required)
             */
            val states = result.states
            val metadata = result.statesMetadata

            // DOCEND VaultQueryExample1
            assertThat(states).hasSize(16)
            assertThat(metadata).hasSize(16)
        }
    }

    @Test
    fun `unconsumed states verbose`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val result = vaultQuerySvc.queryBy<ContractState>(criteria)

            assertThat(result.states).hasSize(16)
            assertThat(result.statesMetadata).hasSize(16)
        }
    }

    @Test
    fun `unconsumed states with count`() {
        database.transaction {

            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
            val resultsBeforeConsume = vaultQuerySvc.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(4)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(4)

            services.consumeCash(75.DOLLARS)

            val consumedCriteria = VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            val resultsAfterConsume = vaultQuerySvc.queryBy<ContractState>(consumedCriteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(1)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(1)
        }
    }

    @Test
    fun `unconsumed cash states simple`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val result = vaultQuerySvc.queryBy<Cash.State>()

            assertThat(result.states).hasSize(3)
            assertThat(result.statesMetadata).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash states verbose`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val result = vaultQuerySvc.queryBy<Cash.State>(criteria)

            assertThat(result.states).hasSize(3)
            assertThat(result.statesMetadata).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash states sorted by state ref`() {
        database.transaction {

            val stateRefs: MutableList<StateRef> = mutableListOf()

            val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            val issuedStateRefs = issuedStates.states.map { it.ref }.toList()
            stateRefs.addAll(issuedStateRefs)

            val spentStates = services.consumeCash(25.DOLLARS)
            val consumedStateRefs = spentStates.consumed.map { it.ref }.toList()
            val producedStateRefs = spentStates.produced.map { it.ref }.toList()
            stateRefs.addAll(consumedStateRefs.plus(producedStateRefs))

            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
            val criteria = VaultQueryCriteria()
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria, Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC))))

            // default StateRef sort is by index then txnId:
            // order by
            //    vaultschem1_.output_index,
            //    vaultschem1_.transaction_id asc
            assertThat(results.states).hasSize(8)       // -3 CONSUMED + 1 NEW UNCONSUMED (change)

            val sortedStateRefs = stateRefs.sortedBy { it.index }

            assertThat(results.states.first().ref.index).isEqualTo(sortedStateRefs.first().index)   // 0
            assertThat(results.states.last().ref.index).isEqualTo(sortedStateRefs.last().index)     // 1
        }
    }

    @Test
    fun `unconsumed cash states sorted by state ref txnId and index`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            val consumed = mutableSetOf<SecureHash>()
            services.consumeCash(10.DOLLARS).consumed.forEach { consumed += it.ref.txhash }
            services.consumeCash(10.DOLLARS).consumed.forEach { consumed += it.ref.txhash }

            val sortAttributeTxnId = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID)
            val sortAttributeIndex = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_INDEX)
            val sortBy = Sort(setOf(Sort.SortColumn(sortAttributeTxnId, Sort.Direction.ASC),
                                    Sort.SortColumn(sortAttributeIndex, Sort.Direction.ASC)))
            val criteria = VaultQueryCriteria()
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria, sortBy)

            results.statesMetadata.forEach {
                println(" ${it.ref}")
                assertThat(it.status).isEqualTo(Vault.StateStatus.UNCONSUMED)
            }
            val sorted = results.states.sortedBy { it.ref.toString() }
            assertThat(results.states).isEqualTo(sorted)
            assertThat(results.states).allSatisfy { !consumed.contains(it.ref.txhash) }
        }
    }

    @Test
    fun `unconsumed states for state refs`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(8)
            val issuedStates = services.fillWithSomeTestLinearStates(2)
            val stateRefs = issuedStates.states.map { it.ref }.toList()

            // DOCSTART VaultQueryExample2
            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID)
            val criteria = VaultQueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()))
            val results = vaultQuerySvc.queryBy<DummyLinearContract.State>(criteria, Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC))))
            // DOCEND VaultQueryExample2

            assertThat(results.states).hasSize(2)

            val sortedStateRefs = stateRefs.sortedBy { it.txhash.bytes.toHexString() }
            assertThat(results.states.first().ref).isEqualTo(sortedStateRefs.first())
            assertThat(results.states.last().ref).isEqualTo(sortedStateRefs.last())
        }
    }

    @Test
    fun `unconsumed states for contract state types`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))


            // default State.Status is UNCONSUMED
            // DOCSTART VaultQueryExample3
            val criteria = VaultQueryCriteria(contractStateTypes = setOf(Cash.State::class.java, DealState::class.java))
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample3
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `consumed states`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 states with same externalId
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList())
            services.consumeDeals(dealStates.states.filter { it.state.data.ref == "456" })
            services.consumeCash(50.DOLLARS)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            assertThat(results.states).hasSize(5)
        }
    }

    @Test
    fun `consumed states with count`() {
        database.transaction {

            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
            val resultsBeforeConsume = vaultQuerySvc.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(4)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(4)

            services.consumeCash(75.DOLLARS)

            val consumedCriteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val resultsAfterConsume = vaultQuerySvc.queryBy<ContractState>(consumedCriteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(3)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(3)
        }
    }

    @Test
    fun `all states`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 results with same UID
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList())
            services.consumeDeals(dealStates.states.filter { it.state.data.ref == "456" })
            services.consumeCash(50.DOLLARS) // generates a new change state!

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            assertThat(results.states).hasSize(17)
        }
    }

    @Test
    fun `all states with count`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
            val resultsBeforeConsume = vaultQuerySvc.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(1)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(1)

            services.consumeCash(50.DOLLARS)    // consumed 100 (spent), produced 50 (change)

            val resultsAfterConsume = vaultQuerySvc.queryBy<ContractState>(criteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(2)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(2)
        }
    }

    val CASH_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(21)) }
    val CASH_NOTARY: Party get() = Party(X500Name("CN=Cash Notary Service,O=R3,OU=corda,L=Zurich,C=CH"), CASH_NOTARY_KEY.public)

    @Test
    fun `unconsumed states by notary`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample4
            val criteria = VaultQueryCriteria(notaryName = listOf(CASH_NOTARY.name))
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample4
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed linear states for single participant`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(2, "TEST", participants = listOf(MEGA_CORP, MINI_CORP))
            services.fillWithSomeTestDeals(listOf("456"), participants = listOf(MEGA_CORP, BIG_CORP))
            services.fillWithSomeTestDeals(listOf("123", "789"), participants = listOf(BIG_CORP))

            val criteria = LinearStateQueryCriteria(participants = listOf(BIG_CORP))
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)

            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed linear states for two participants`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(2, "TEST", participants = listOf(MEGA_CORP, MINI_CORP))
            services.fillWithSomeTestDeals(listOf("456"), participants = listOf(MEGA_CORP, BIG_CORP))
            services.fillWithSomeTestDeals(listOf("123", "789"), participants = listOf(BIG_CORP))

            // DOCSTART VaultQueryExample5
            val criteria = LinearStateQueryCriteria(participants = listOf(MEGA_CORP, MINI_CORP))
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample5

            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed states with soft locking`() {
        database.transaction {

            val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 10, 10, Random(0L)).states.toList()
            vaultSvc.softLockReserve(UUID.randomUUID(), NonEmptySet.of(issuedStates[1].ref, issuedStates[2].ref, issuedStates[3].ref))
            val lockId1 = UUID.randomUUID()
            vaultSvc.softLockReserve(lockId1, NonEmptySet.of(issuedStates[4].ref, issuedStates[5].ref))
            val lockId2 = UUID.randomUUID()
            vaultSvc.softLockReserve(lockId2, NonEmptySet.of(issuedStates[6].ref))

            // excluding soft locked states
            val criteriaExclusive = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_ONLY))
            val resultsExclusive = vaultQuerySvc.queryBy<ContractState>(criteriaExclusive)
            assertThat(resultsExclusive.states).hasSize(4)

            // only soft locked states
            val criteriaLockedOnly = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            val resultsLockedOnly = vaultQuerySvc.queryBy<ContractState>(criteriaLockedOnly)
            assertThat(resultsLockedOnly.states).hasSize(6)

            // soft locked states by single lock id
            val criteriaByLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(lockId1)))
            val resultsByLockId = vaultQuerySvc.queryBy<ContractState>(criteriaByLockId)
            assertThat(resultsByLockId.states).hasSize(2)

            // soft locked states by multiple lock ids
            val criteriaByLockIds = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(lockId1, lockId2)))
            val resultsByLockIds = vaultQuerySvc.queryBy<ContractState>(criteriaByLockIds)
            assertThat(resultsByLockIds.states).hasSize(3)

            // unlocked and locked by `lockId2`
            val criteriaUnlockedAndByLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId2)))
            val resultsUnlockedAndByLockIds = vaultQuerySvc.queryBy<ContractState>(criteriaUnlockedAndByLockId)
            assertThat(resultsUnlockedAndByLockIds.states).hasSize(5)

            // missing lockId
            expectedEx.expect(IllegalArgumentException::class.java)
            expectedEx.expectMessage("Must specify one or more lockIds")
            val criteriaMissingLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_AND_SPECIFIED))
            vaultQuerySvc.queryBy<ContractState>(criteriaMissingLockId)
        }
    }

    @Test
    fun `logical operator EQUAL`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator NOT EQUAL`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notEqual(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator GREATER_THAN`() {
        database.transaction {

            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.greaterThan(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator GREATER_THAN_OR_EQUAL`() {
        database.transaction {

            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.greaterThanOrEqual(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator LESS_THAN`() {
        database.transaction {

            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.lessThan(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator LESS_THAN_OR_EQUAL`() {
        database.transaction {

            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.lessThanOrEqual(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator BETWEEN`() {
        database.transaction {

            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.between(500L, 1500L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator IN`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val currencies = listOf(CHF.currencyCode, GBP.currencyCode)
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.`in`(currencies) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator NOT IN`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val currencies = listOf(CHF.currencyCode, GBP.currencyCode)
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notIn(currencies) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator LIKE`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.like("%BP") }  // GPB
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator NOT LIKE`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notLike("%BP") }  // GPB
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator IS_NULL`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::issuerParty.isNull() }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(0)
        }
    }

    @Test
    fun `logical operator NOT_NULL`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val logicalExpression = builder { CashSchemaV1.PersistentCashState::issuerParty.notNull() }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `aggregate functions without group clause`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, DUMMY_NOTARY, 5, 5, Random(0L))

            // DOCSTART VaultQueryExample21
            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum() }
            val sumCriteria = VaultCustomQueryCriteria(sum)

            val count = builder { CashSchemaV1.PersistentCashState::pennies.count() }
            val countCriteria = VaultCustomQueryCriteria(count)

            val max = builder { CashSchemaV1.PersistentCashState::pennies.max() }
            val maxCriteria = VaultCustomQueryCriteria(max)

            val min = builder { CashSchemaV1.PersistentCashState::pennies.min() }
            val minCriteria = VaultCustomQueryCriteria(min)

            val avg = builder { CashSchemaV1.PersistentCashState::pennies.avg() }
            val avgCriteria = VaultCustomQueryCriteria(avg)

            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(sumCriteria
                                                             .and(countCriteria)
                                                             .and(maxCriteria)
                                                             .and(minCriteria)
                                                             .and(avgCriteria))
            // DOCEND VaultQueryExample21

            assertThat(results.otherResults).hasSize(5)
            assertThat(results.otherResults[0]).isEqualTo(150000L)
            assertThat(results.otherResults[1]).isEqualTo(15L)
            assertThat(results.otherResults[2]).isEqualTo(11298L)
            assertThat(results.otherResults[3]).isEqualTo(8702L)
            assertThat(results.otherResults[4]).isEqualTo(10000.0)
        }
    }

    @Test
    fun `aggregate functions with single group clause`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, DUMMY_NOTARY, 5, 5, Random(0L))

            // DOCSTART VaultQueryExample22
            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val sumCriteria = VaultCustomQueryCriteria(sum)

            val max = builder { CashSchemaV1.PersistentCashState::pennies.max(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val maxCriteria = VaultCustomQueryCriteria(max)

            val min = builder { CashSchemaV1.PersistentCashState::pennies.min(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val minCriteria = VaultCustomQueryCriteria(min)

            val avg = builder { CashSchemaV1.PersistentCashState::pennies.avg(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val avgCriteria = VaultCustomQueryCriteria(avg)

            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(sumCriteria
                                                             .and(maxCriteria)
                                                             .and(minCriteria)
                                                             .and(avgCriteria))
            // DOCEND VaultQueryExample22

            assertThat(results.otherResults).hasSize(24)
            /** CHF */
            assertThat(results.otherResults[0]).isEqualTo(50000L)
            assertThat(results.otherResults[1]).isEqualTo("CHF")
            assertThat(results.otherResults[2]).isEqualTo(10274L)
            assertThat(results.otherResults[3]).isEqualTo("CHF")
            assertThat(results.otherResults[4]).isEqualTo(9481L)
            assertThat(results.otherResults[5]).isEqualTo("CHF")
            assertThat(results.otherResults[6]).isEqualTo(10000.0)
            assertThat(results.otherResults[7]).isEqualTo("CHF")
            /** GBP */
            assertThat(results.otherResults[8]).isEqualTo(40000L)
            assertThat(results.otherResults[9]).isEqualTo("GBP")
            assertThat(results.otherResults[10]).isEqualTo(10343L)
            assertThat(results.otherResults[11]).isEqualTo("GBP")
            assertThat(results.otherResults[12]).isEqualTo(9351L)
            assertThat(results.otherResults[13]).isEqualTo("GBP")
            assertThat(results.otherResults[14]).isEqualTo(10000.0)
            assertThat(results.otherResults[15]).isEqualTo("GBP")
            /** USD */
            assertThat(results.otherResults[16]).isEqualTo(60000L)
            assertThat(results.otherResults[17]).isEqualTo("USD")
            assertThat(results.otherResults[18]).isEqualTo(11298L)
            assertThat(results.otherResults[19]).isEqualTo("USD")
            assertThat(results.otherResults[20]).isEqualTo(8702L)
            assertThat(results.otherResults[21]).isEqualTo("USD")
            assertThat(results.otherResults[22]).isEqualTo(10000.0)
            assertThat(results.otherResults[23]).isEqualTo("USD")
        }
    }

    @Test
    fun `aggregate functions sum by issuer and currency and sort by aggregate sum`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = DUMMY_CASH_ISSUER)
            services.fillWithSomeTestCash(200.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L), issuedBy = BOC.ref(1), issuerKey = BOC_KEY)
            services.fillWithSomeTestCash(300.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L), issuedBy = DUMMY_CASH_ISSUER)
            services.fillWithSomeTestCash(400.POUNDS, DUMMY_NOTARY, 4, 4, Random(0L), issuedBy = BOC.ref(2), issuerKey = BOC_KEY)

            // DOCSTART VaultQueryExample23
            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::issuerParty,
                                                                                                      CashSchemaV1.PersistentCashState::currency),
                                                                              orderBy = Sort.Direction.DESC)
            }

            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(VaultCustomQueryCriteria(sum))
            // DOCEND VaultQueryExample23

            assertThat(results.otherResults).hasSize(12)

            assertThat(results.otherResults[0]).isEqualTo(40000L)
            assertThat(results.otherResults[1]).isEqualTo(BOC_PUBKEY.toBase58String())
            assertThat(results.otherResults[2]).isEqualTo("GBP")
            assertThat(results.otherResults[3]).isEqualTo(30000L)
            assertThat(results.otherResults[4]).isEqualTo(DUMMY_CASH_ISSUER.party.owningKey.toBase58String())
            assertThat(results.otherResults[5]).isEqualTo("GBP")
            assertThat(results.otherResults[6]).isEqualTo(20000L)
            assertThat(results.otherResults[7]).isEqualTo(BOC_PUBKEY.toBase58String())
            assertThat(results.otherResults[8]).isEqualTo("USD")
            assertThat(results.otherResults[9]).isEqualTo(10000L)
            assertThat(results.otherResults[10]).isEqualTo(DUMMY_CASH_ISSUER.party.owningKey.toBase58String())
            assertThat(results.otherResults[11]).isEqualTo("USD")
        }
    }

    @Test
    fun `aggregate functions count by contract type`() {
        database.transaction {
            // create new states
            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestLinearStates(1, "XYZ")
            services.fillWithSomeTestLinearStates(2, "JKL")
            services.fillWithSomeTestLinearStates(3, "ABC")
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // count fungible assets
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count)
            val fungibleStateCount = vaultQuerySvc.queryBy<FungibleAsset<*>>(countCriteria).otherResults.single() as Long
            assertThat(fungibleStateCount).isEqualTo(10L)

            // count linear states
            val linearStateCount = vaultQuerySvc.queryBy<LinearState>(countCriteria).otherResults.single() as Long
            assertThat(linearStateCount).isEqualTo(9L)

            // count deal states
            val dealStateCount = vaultQuerySvc.queryBy<DealState>(countCriteria).otherResults.single() as Long
            assertThat(dealStateCount).isEqualTo(3L)
        }
    }

    @Test
    fun `aggregate functions count by contract type and state status`() {
        database.transaction {
            // create new states
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            val linearStatesXYZ = services.fillWithSomeTestLinearStates(1, "XYZ")
            val linearStatesJKL = services.fillWithSomeTestLinearStates(2, "JKL")
            services.fillWithSomeTestLinearStates(3, "ABC")
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // ALL states

            // count fungible assets
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.ALL)
            val fungibleStateCount = vaultQuerySvc.queryBy<FungibleAsset<*>>(countCriteria).otherResults.single() as Long
            assertThat(fungibleStateCount).isEqualTo(10L)

            // count linear states
            val linearStateCount = vaultQuerySvc.queryBy<LinearState>(countCriteria).otherResults.single() as Long
            assertThat(linearStateCount).isEqualTo(9L)

            // count deal states
            val dealStateCount = vaultQuerySvc.queryBy<DealState>(countCriteria).otherResults.single() as Long
            assertThat(dealStateCount).isEqualTo(3L)

            // consume some states
            services.consumeLinearStates(linearStatesXYZ.states.toList())
            services.consumeLinearStates(linearStatesJKL.states.toList())
            services.consumeDeals(dealStates.states.filter { it.state.data.ref == "456" })
            val cashUpdates = services.consumeCash(50.DOLLARS)

            // UNCONSUMED states (default)

            // count fungible assets
            val countCriteriaUnconsumed = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.UNCONSUMED)
            val fungibleStateCountUnconsumed = vaultQuerySvc.queryBy<FungibleAsset<*>>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(fungibleStateCountUnconsumed.toInt()).isEqualTo(10 - cashUpdates.consumed.size + cashUpdates.produced.size)

            // count linear states
            val linearStateCountUnconsumed = vaultQuerySvc.queryBy<LinearState>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(linearStateCountUnconsumed).isEqualTo(5L)

            // count deal states
            val dealStateCountUnconsumed = vaultQuerySvc.queryBy<DealState>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(dealStateCountUnconsumed).isEqualTo(2L)

            // CONSUMED states

            // count fungible assets
            val countCriteriaConsumed = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.CONSUMED)
            val fungibleStateCountConsumed = vaultQuerySvc.queryBy<FungibleAsset<*>>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(fungibleStateCountConsumed.toInt()).isEqualTo(cashUpdates.consumed.size)

            // count linear states
            val linearStateCountConsumed = vaultQuerySvc.queryBy<LinearState>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(linearStateCountConsumed).isEqualTo(4L)

            // count deal states
            val dealStateCountConsumed = vaultQuerySvc.queryBy<DealState>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(dealStateCountConsumed).isEqualTo(1L)
        }
    }

    private val TODAY = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)

    @Test
    fun `unconsumed states recorded between two time intervals`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 3, 3, Random(0L))

            // DOCSTART VaultQueryExample6
            val start = TODAY
            val end = TODAY.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = TimeCondition(
                    QueryCriteria.TimeInstantType.RECORDED,
                    ColumnPredicate.Between(start, end))
            val criteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample6
            assertThat(results.states).hasSize(3)

            // Future
            val startFuture = TODAY.plus(1, ChronoUnit.DAYS)
            val recordedBetweenExpressionFuture = TimeCondition(
                    QueryCriteria.TimeInstantType.RECORDED, ColumnPredicate.Between(startFuture, end))
            val criteriaFuture = VaultQueryCriteria(timeCondition = recordedBetweenExpressionFuture)
            assertThat(vaultQuerySvc.queryBy<ContractState>(criteriaFuture).states).isEmpty()
        }
    }

    @Test
    fun `states consumed after time`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeCash(100.DOLLARS)

            val asOfDateTime = TODAY
            val consumedAfterExpression = TimeCondition(
                    QueryCriteria.TimeInstantType.CONSUMED, ColumnPredicate.BinaryComparison(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL, asOfDateTime))
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                    timeCondition = consumedAfterExpression)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)

            assertThat(results.states).hasSize(3)
        }
    }

    // pagination: first page
    @Test
    fun `all states with paging specification - first page`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            // DOCSTART VaultQueryExample7
            val pagingSpec = PageSpecification(DEFAULT_PAGE_NUM, 10)
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria, paging = pagingSpec)
            // DOCEND VaultQueryExample7
            assertThat(results.states).hasSize(10)
            assertThat(results.totalStatesAvailable).isEqualTo(100)
        }
    }

    // pagination: last page
    @Test
    fun `all states with paging specification  - last`() {
        database.transaction {

            services.fillWithSomeTestCash(95.DOLLARS, DUMMY_NOTARY, 95, 95, Random(0L))

            // Last page implies we need to perform a row count for the Query first,
            // and then re-query for a given offset defined by (count - pageSize)
            val pagingSpec = PageSpecification(10, 10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultQuerySvc.queryBy<ContractState>(criteria, paging = pagingSpec)
            assertThat(results.states).hasSize(5) // should retrieve states 90..94
            assertThat(results.totalStatesAvailable).isEqualTo(95)
        }
    }

    // pagination: invalid page number
    @Test
    fun `invalid page number`() {
        expectedEx.expect(VaultQueryException::class.java)
        expectedEx.expectMessage("Page specification: invalid page number")

        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            val pagingSpec = PageSpecification(0, 10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultQuerySvc.queryBy<ContractState>(criteria, paging = pagingSpec)
        }
    }

    // pagination: invalid page size
    @Test
    fun `invalid page size`() {
        expectedEx.expect(VaultQueryException::class.java)
        expectedEx.expectMessage("Page specification: invalid page size")

        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 100, 100, Random(0L))

            val pagingSpec = PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE + 1)  // overflow = -2147483648
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultQuerySvc.queryBy<ContractState>(criteria, paging = pagingSpec)
        }
    }

    // pagination not specified but more than DEFAULT_PAGE_SIZE results available (fail-fast test)
    @Test
    fun `pagination not specified but more than default results available`() {
        expectedEx.expect(VaultQueryException::class.java)
        expectedEx.expectMessage("Please specify a `PageSpecification`")

        database.transaction {

            services.fillWithSomeTestCash(201.DOLLARS, DUMMY_NOTARY, 201, 201, Random(0L))

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultQuerySvc.queryBy<ContractState>(criteria)
        }
    }

    // sorting
    @Test
    fun `sorting - all states sorted by contract type, state status, consumed time`() {

        setUpDb(database)

        database.transaction {

            val sortCol1 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.CONTRACT_TYPE), Sort.Direction.DESC)
            val sortCol2 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.STATE_STATUS), Sort.Direction.ASC)
            val sortCol3 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.CONSUMED_TIME), Sort.Direction.DESC)
            val sorting = Sort(setOf(sortCol1, sortCol2, sortCol3))
            val result = vaultQuerySvc.queryBy<ContractState>(VaultQueryCriteria(status = Vault.StateStatus.ALL), sorting = sorting)

            val states = result.states
            val metadata = result.statesMetadata

            for (i in 0..states.size - 1) {
                println("${states[i].ref} : ${metadata[i].contractStateClassName}, ${metadata[i].status}, ${metadata[i].consumedTime}")
            }

            assertThat(states).hasSize(20)
            assertThat(metadata.first().contractStateClassName).isEqualTo("net.corda.testing.contracts.DummyLinearContract\$State")
            assertThat(metadata.first().status).isEqualTo(Vault.StateStatus.UNCONSUMED) // 0 = UNCONSUMED
            assertThat(metadata.last().contractStateClassName).isEqualTo("net.corda.contracts.asset.Cash\$State")
            assertThat(metadata.last().status).isEqualTo(Vault.StateStatus.CONSUMED)    // 1 = CONSUMED
        }
    }

    @Test
    fun `unconsumed fungible assets`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCommodity(Amount(100, Commodity.getInstance("FCOJ")!!))
            services.fillWithSomeTestLinearStates(10)

            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>()
            assertThat(results.states).hasSize(4)
        }
    }

    @Test
    fun `consumed fungible assets`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.consumeCash(50.DOLLARS)
            services.fillWithSomeTestCommodity(Amount(100, Commodity.getInstance("FCOJ")!!))
            services.fillWithSomeTestLinearStates(10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed cash fungible assets`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)

            val results = vaultQuerySvc.queryBy<Cash.State>()
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash fungible assets after spending`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.consumeCash(50.DOLLARS)
            // should now have x2 CONSUMED + x2 UNCONSUMED (one spent + one change)

            val results = vaultQuerySvc.queryBy<Cash.State>(FungibleAssetQueryCriteria())
            assertThat(results.statesMetadata).hasSize(2)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `consumed cash fungible assets`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.consumeCash(50.DOLLARS)
            val linearStates = services.fillWithSomeTestLinearStates(10)
            services.consumeLinearStates(linearStates.states.toList())

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultQuerySvc.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val results = vaultQuerySvc.queryBy<LinearState>()
            assertThat(results.states).hasSize(13)
        }
    }

    @Test
    fun `consumed linear heads`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 states with same externalId
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList())
            services.consumeDeals(dealStates.states.filter { it.state.data.ref == "456" })
            services.consumeCash(50.DOLLARS)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultQuerySvc.queryBy<LinearState>(criteria)
            assertThat(results.states).hasSize(3)
        }
    }

    /** LinearState tests */

    @Test
    fun `unconsumed linear heads for linearId without external Id`() {
        database.transaction {

            val issuedStates = services.fillWithSomeTestLinearStates(10)

            // DOCSTART VaultQueryExample8
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = LinearStateQueryCriteria(linearId = listOf(linearIds.first(), linearIds.last()))
            val results = vaultQuerySvc.queryBy<LinearState>(criteria)
            // DOCEND VaultQueryExample8
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads for linearId with external Id`() {
        database.transaction {

            val linearState1 = services.fillWithSomeTestLinearStates(1, "ID1")
            services.fillWithSomeTestLinearStates(1, "ID2")
            val linearState3 = services.fillWithSomeTestLinearStates(1, "ID3")

            val linearIds = listOf(linearState1.states.first().state.data.linearId, linearState3.states.first().state.data.linearId)
            val criteria = LinearStateQueryCriteria(linearId = linearIds)
            val results = vaultQuerySvc.queryBy<LinearState>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `all linear states for a given id`() {
        database.transaction {

            val txns = services.fillWithSomeTestLinearStates(1, "TEST")
            val linearState = txns.states.first()
            val linearId = linearState.state.data.linearId
            services.evolveLinearState(linearState)  // consume current and produce new state reference
            services.evolveLinearState(linearState)  // consume current and produce new state reference
            services.evolveLinearState(linearState)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            // DOCSTART VaultQueryExample9
            val linearStateCriteria = LinearStateQueryCriteria(linearId = listOf(linearId), status = Vault.StateStatus.ALL)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultQuerySvc.queryBy<LinearState>(linearStateCriteria and vaultCriteria)
            // DOCEND VaultQueryExample9
            assertThat(results.states).hasSize(4)
        }
    }

    @Test
    fun `all linear states for a given id sorted by uuid`() {
        database.transaction {

            val txns = services.fillWithSomeTestLinearStates(2, "TEST")
            val linearStates = txns.states.toList()
            services.evolveLinearStates(linearStates)  // consume current and produce new state reference
            services.evolveLinearStates(linearStates)  // consume current and produce new state reference
            services.evolveLinearStates(linearStates)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            val linearStateCriteria = LinearStateQueryCriteria(linearId = linearStates.map { it.state.data.linearId }, status = Vault.StateStatus.ALL)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC)))

            val results = vaultQuerySvc.queryBy<LinearState>(linearStateCriteria.and(vaultCriteria), sorting = sorting)
            results.states.forEach { println("${it.state.data.linearId.id}") }
            assertThat(results.states).hasSize(8)
        }
    }

    @Test
    fun `unconsumed linear states sorted by linear state attribute`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, externalId = "111")
            services.fillWithSomeTestLinearStates(2, externalId = "222")
            services.fillWithSomeTestLinearStates(3, externalId = "333")

            val vaultCriteria = VaultQueryCriteria()
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.EXTERNAL_ID), Sort.Direction.DESC)))

            val results = vaultQuerySvc.queryBy<DummyLinearContract.State>((vaultCriteria), sorting = sorting)
            results.states.forEach { println("${it.state.data.linearString}") }
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `unconsumed deal states sorted`() {
        database.transaction {

            val linearStates = services.fillWithSomeTestLinearStates(10)
            val uid = linearStates.states.first().state.data.linearId
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val linearStateCriteria = LinearStateQueryCriteria(linearId = listOf(uid))
            val dealStateCriteria = LinearStateQueryCriteria(dealRef = listOf("123", "456", "789"))
            val compositeCriteria = linearStateCriteria or dealStateCriteria

            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.DEAL_REFERENCE), Sort.Direction.DESC)))

            val results = vaultQuerySvc.queryBy<LinearState>(compositeCriteria, sorting = sorting)
            assertThat(results.statesMetadata).hasSize(13)
            assertThat(results.states).hasSize(13)
        }
    }

    @Test
    fun `unconsumed linear states sorted by custom attribute`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, linearString = "111")
            services.fillWithSomeTestLinearStates(2, linearString = "222")
            services.fillWithSomeTestLinearStates(3, linearString = "333")

            val vaultCriteria = VaultQueryCriteria()
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Custom(DummyLinearStateSchemaV1.PersistentDummyLinearState::class.java, "linearString"), Sort.Direction.DESC)))

            val results = vaultQuerySvc.queryBy<DummyLinearContract.State>((vaultCriteria), sorting = sorting)
            results.states.forEach { println("${it.state.data.linearString}") }
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `return consumed linear states for a given id`() {
        database.transaction {

            val txns = services.fillWithSomeTestLinearStates(1, "TEST")
            val linearState = txns.states.first()
            val linearState2 = services.evolveLinearState(linearState)  // consume current and produce new state reference
            val linearState3 = services.evolveLinearState(linearState2)  // consume current and produce new state reference
            services.evolveLinearState(linearState3)  // consume current and produce new state reference

            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            val linearStateCriteria = LinearStateQueryCriteria(linearId = txns.states.map { it.state.data.linearId }, status = Vault.StateStatus.CONSUMED)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC)))
            val results = vaultQuerySvc.queryBy<LinearState>(linearStateCriteria.and(vaultCriteria), sorting = sorting)
            assertThat(results.states).hasSize(3)
        }
    }

    /**
     *  Deal Contract state to be removed as is duplicate of LinearState
     */
    @Test
    fun `unconsumed deals`() {
        database.transaction {

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            val results = vaultQuerySvc.queryBy<DealState>()
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed deals for ref`() {
        database.transaction {

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // DOCSTART VaultQueryExample10
            val criteria = LinearStateQueryCriteria(dealRef = listOf("456", "789"))
            val results = vaultQuerySvc.queryBy<DealState>(criteria)
            // DOCEND VaultQueryExample10

            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `latest unconsumed deals for ref`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(2, "TEST")
            services.fillWithSomeTestDeals(listOf("456"))
            services.fillWithSomeTestDeals(listOf("123", "789"))

            val all = vaultQuerySvc.queryBy<DealState>()
            all.states.forEach { println(it.state) }

            val criteria = LinearStateQueryCriteria(dealRef = listOf("456"))
            val results = vaultQuerySvc.queryBy<DealState>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals with party`() {

        database.transaction {

            val parties = listOf(MEGA_CORP)

            services.fillWithSomeTestLinearStates(2, "TEST")
            services.fillWithSomeTestDeals(listOf("456"), parties)
            services.fillWithSomeTestDeals(listOf("123", "789"))

            // DOCSTART VaultQueryExample11
            val criteria = LinearStateQueryCriteria(participants = parties)
            val results = vaultQuerySvc.queryBy<DealState>(criteria)
            // DOCEND VaultQueryExample11

            assertThat(results.states).hasSize(1)
        }
    }

    /** FungibleAsset tests */

    @Test
    fun `unconsumed fungible assets for specific issuer party and refs`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(1))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(2)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(2))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(3)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(3))

            val criteria = FungibleAssetQueryCriteria(issuerPartyName = listOf(BOC),
                    issuerRef = listOf(BOC.ref(1).reference, BOC.ref(2).reference))
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed fungible assets for selected issuer parties`() {
        // GBP issuer
        val GBP_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(1001)) }
        val GBP_CASH_ISSUER by lazy { Party(X500Name("CN=British Pounds Cash Issuer,O=R3,OU=corda,L=London,C=GB"), GBP_CASH_ISSUER_KEY.public).ref(1) }
        // USD issuer
        val USD_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(1002)) }
        val USD_CASH_ISSUER by lazy { Party(X500Name("CN=US Dollars Cash Issuer,O=R3,OU=corda,L=New York,C=US"), USD_CASH_ISSUER_KEY.public).ref(1) }
        // CHF issuer
        val CHF_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(1003)) }
        val CHF_CASH_ISSUER by lazy { Party(X500Name("CN=Swiss Francs Cash Issuer,O=R3,OU=corda,L=Zurich,C=CH"), CHF_CASH_ISSUER_KEY.public).ref(1) }

        database.transaction {
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (GBP_CASH_ISSUER), issuerKey = (GBP_CASH_ISSUER_KEY))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (USD_CASH_ISSUER), issuerKey = (USD_CASH_ISSUER_KEY))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (CHF_CASH_ISSUER), issuerKey = (CHF_CASH_ISSUER_KEY))

            val criteria = FungibleAssetQueryCriteria(issuerPartyName = listOf(GBP_CASH_ISSUER.party, USD_CASH_ISSUER.party))
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed fungible assets by owner`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = MEGA_CORP.ref(0), issuerKey = MEGA_CORP_KEY, ownedBy = (MEGA_CORP))

            val criteria = FungibleAssetQueryCriteria(owner = listOf(MEGA_CORP))
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }


    @Test
    fun `unconsumed fungible states for owners`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, CASH_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = MEGA_CORP.ref(0), issuerKey = MEGA_CORP_KEY, ownedBy = (MEGA_CORP))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = MINI_CORP.ref(0), issuerKey = MINI_CORP_KEY, ownedBy = (MINI_CORP))  // irrelevant to this vault

            // DOCSTART VaultQueryExample5.2
            val criteria = FungibleAssetQueryCriteria(owner = listOf(MEGA_CORP,MINI_CORP))
            val results = vaultQuerySvc.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample5.2

            assertThat(results.states).hasSize(1)   // can only be 1 owner of a node (MEGA_CORP in this MockServices setup)
        }
    }

    /** Cash Fungible State specific */
    @Test
    fun `unconsumed fungible assets for single currency`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            // DOCSTART VaultQueryExample12
            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(USD.currencyCode) }
            val criteria = VaultCustomQueryCriteria(ccyIndex)
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample12

            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash balance for single currency`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))

            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val sumCriteria = VaultCustomQueryCriteria(sum)

            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(USD.currencyCode) }
            val ccyCriteria = VaultCustomQueryCriteria(ccyIndex)

            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(sumCriteria.and(ccyCriteria))

            assertThat(results.otherResults).hasSize(2)
            assertThat(results.otherResults[0]).isEqualTo(30000L)
            assertThat(results.otherResults[1]).isEqualTo("USD")
        }
    }

    @Test
    fun `unconsumed cash balances for all currencies`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.POUNDS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, DUMMY_NOTARY, 5, 5, Random(0L))
            services.fillWithSomeTestCash(600.SWISS_FRANCS, DUMMY_NOTARY, 6, 6, Random(0L))

            val ccyIndex = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val criteria = VaultCustomQueryCriteria(ccyIndex)
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)

            assertThat(results.otherResults).hasSize(6)
            assertThat(results.otherResults[0]).isEqualTo(110000L)
            assertThat(results.otherResults[1]).isEqualTo("CHF")
            assertThat(results.otherResults[2]).isEqualTo(70000L)
            assertThat(results.otherResults[3]).isEqualTo("GBP")
            assertThat(results.otherResults[4]).isEqualTo(30000L)
            assertThat(results.otherResults[5]).isEqualTo("USD")
        }
    }

    @Test
    fun `unconsumed fungible assets for quantity greater than`() {
        database.transaction {

            services.fillWithSomeTestCash(10.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(25.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(50.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 3, 3, Random(0L))

            // DOCSTART VaultQueryExample13
            val fungibleAssetCriteria = FungibleAssetQueryCriteria(quantity = builder { greaterThan(2500L) })
            val results = vaultQuerySvc.queryBy<Cash.State>(fungibleAssetCriteria)
            // DOCEND VaultQueryExample13

            assertThat(results.states).hasSize(4)  // POUNDS, SWISS_FRANCS
        }
    }

    @Test
    fun `unconsumed fungible assets for issuer party`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY)

            // DOCSTART VaultQueryExample14
            val criteria = FungibleAssetQueryCriteria(issuerPartyName = listOf(BOC))
            val results = vaultQuerySvc.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample14

            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed fungible assets for single currency and quantity greater than`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(50.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(GBP.currencyCode) }
            val customCriteria = VaultCustomQueryCriteria(ccyIndex)
            val fungibleAssetCriteria = FungibleAssetQueryCriteria(quantity = builder { greaterThan(5000L) })
            val results = vaultQuerySvc.queryBy<Cash.State>(fungibleAssetCriteria.and(customCriteria))

            assertThat(results.states).hasSize(1)   // POUNDS > 50
        }
    }

    /** Vault Custom Query tests */

    // specifying Query on Commercial Paper contract state attributes
    @Test
    fun `custom query using JPA - commercial paper schema V1 single attribute`() {
        database.transaction {

            val issuance = MEGA_CORP.ref(1)

            // MegaCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue = 10000.DOLLARS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper =
                    CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY).apply {
                        setTimeWindow(TEST_TX_TIME, 30.seconds)
                        signWith(MEGA_CORP_KEY)
                        signWith(DUMMY_NOTARY_KEY)
                    }.toSignedTransaction()
            services.recordTransactions(commercialPaper)

            // MegaCorp™ now issues £10,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue2 = 10000.POUNDS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper2 =
                    CommercialPaper().generateIssue(issuance, faceValue2, TEST_TX_TIME + 30.days, DUMMY_NOTARY).apply {
                        setTimeWindow(TEST_TX_TIME, 30.seconds)
                        signWith(MEGA_CORP_KEY)
                        signWith(DUMMY_NOTARY_KEY)
                    }.toSignedTransaction()
            services.recordTransactions(commercialPaper2)

            val ccyIndex = builder { CommercialPaperSchemaV1.PersistentCommercialPaperState::currency.equal(USD.currencyCode) }
            val criteria1 = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)

            val result = vaultQuerySvc.queryBy<CommercialPaper.State>(criteria1)

            Assertions.assertThat(result.states).hasSize(1)
            Assertions.assertThat(result.statesMetadata).hasSize(1)
        }
    }

    // specifying Query on Commercial Paper contract state attributes
    @Test
    fun `custom query using JPA - commercial paper schema V1 - multiple attributes`() {
        database.transaction {

            val issuance = MEGA_CORP.ref(1)

            // MegaCorp™ issues $10,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue = 10000.DOLLARS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper =
                    CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY).apply {
                        setTimeWindow(TEST_TX_TIME, 30.seconds)
                        signWith(MEGA_CORP_KEY)
                        signWith(DUMMY_NOTARY_KEY)
                    }.toSignedTransaction()
            services.recordTransactions(commercialPaper)

            // MegaCorp™ now issues £5,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue2 = 5000.POUNDS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper2 =
                    CommercialPaper().generateIssue(issuance, faceValue2, TEST_TX_TIME + 30.days, DUMMY_NOTARY).apply {
                        setTimeWindow(TEST_TX_TIME, 30.seconds)
                        signWith(MEGA_CORP_KEY)
                        signWith(DUMMY_NOTARY_KEY)
                    }.toSignedTransaction()
            services.recordTransactions(commercialPaper2)

            val result = builder {

                val ccyIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::currency.equal(USD.currencyCode)
                val maturityIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::maturity.greaterThanOrEqual(TEST_TX_TIME + 30.days)
                val faceValueIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::faceValue.greaterThanOrEqual(10000L)

                val criteria1 = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
                val criteria2 = QueryCriteria.VaultCustomQueryCriteria(maturityIndex)
                val criteria3 = QueryCriteria.VaultCustomQueryCriteria(faceValueIndex)

                vaultQuerySvc.queryBy<CommercialPaper.State>(criteria1.and(criteria3).and(criteria2))
            }


            Assertions.assertThat(result.states).hasSize(1)
            Assertions.assertThat(result.statesMetadata).hasSize(1)
        }
    }

    @Test
    fun `query attempting to use unregistered schema`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 1, 1, Random(0L))

            // CashSchemaV3 NOT registered with NodeSchemaService
            val logicalExpression = builder { SampleCashSchemaV3.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)

            assertThatThrownBy {
                vaultQuerySvc.queryBy<Cash.State>(criteria)
            }.isInstanceOf(VaultQueryException::class.java).hasMessageContaining("Please register the entity")
        }
    }

    /** Chaining together different Query Criteria tests**/

    // specifying Query on Cash contract state attributes
    @Test
    fun `custom - all cash states with amount of currency greater or equal than`() {

        database.transaction {

            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(1.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            // DOCSTART VaultQueryExample20
            val generalCriteria = VaultQueryCriteria(Vault.StateStatus.ALL)

            val results = builder {
                val currencyIndex = PersistentCashState::currency.equal(USD.currencyCode)
                val quantityIndex = PersistentCashState::pennies.greaterThanOrEqual(10L)

                val customCriteria1 = VaultCustomQueryCriteria(currencyIndex)
                val customCriteria2 = VaultCustomQueryCriteria(quantityIndex)

                val criteria = generalCriteria.and(customCriteria1.and(customCriteria2))
                vaultQuerySvc.queryBy<Cash.State>(criteria)
            }
            // DOCEND VaultQueryExample20

            assertThat(results.states).hasSize(3)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for linearId between two timestamps`() {
        database.transaction {

            val start = Instant.now()
            val end = start.plus(1, ChronoUnit.SECONDS)

            services.fillWithSomeTestLinearStates(1, "TEST")
            sleep(1000)
            services.fillWithSomeTestLinearStates(1, "TEST")

            // 2 unconsumed states with same external ID

            val recordedBetweenExpression = TimeCondition(TimeInstantType.RECORDED, builder { between(start, end) })
            val basicCriteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)

            val results = vaultQuerySvc.queryBy<LinearState>(basicCriteria)

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for a given external id`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1, "TEST2")

            // 2 unconsumed states with same external ID

            val externalIdCondition = builder { VaultSchemaV1.VaultLinearStates::externalId.equal("TEST2") }
            val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

            val results = vaultQuerySvc.queryBy<LinearState>(externalIdCustomCriteria)

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for linearId between two timestamps for a given external id`() {
        database.transaction {

            val start = Instant.now()
            val end = start.plus(1, ChronoUnit.SECONDS)

            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1, "TEST2")
            sleep(1000)
            services.fillWithSomeTestLinearStates(1, "TEST3")

            // 2 unconsumed states with same external ID

            val results = builder {
                val linearIdCondition = VaultSchemaV1.VaultLinearStates::externalId.equal("TEST2")
                val customCriteria = VaultCustomQueryCriteria(linearIdCondition)

                val recordedBetweenExpression = TimeCondition(TimeInstantType.RECORDED, between(start, end))
                val basicCriteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)

                val criteria = basicCriteria.and(customCriteria)
                vaultQuerySvc.queryBy<LinearState>(criteria)
            }

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for a given external id or uuid`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, "TEST1")
            val aState = services.fillWithSomeTestLinearStates(1, "TEST2").states
            services.consumeLinearStates(aState.toList())
            val uuid = services.fillWithSomeTestLinearStates(1, "TEST3").states.first().state.data.linearId.id

            // 2 unconsumed states with same external ID, 1 with different external ID

            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.equal("TEST2")
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                val uuidCondition = VaultSchemaV1.VaultLinearStates::uuid.equal(uuid)
                val uuidCustomCriteria = VaultCustomQueryCriteria(uuidCondition)

                val criteria = externalIdCustomCriteria or uuidCustomCriteria
                vaultQuerySvc.queryBy<LinearState>(criteria)
            }
            assertThat(results.statesMetadata).hasSize(3)
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed linear heads where external id is null`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1)
            services.fillWithSomeTestLinearStates(1, "TEST3")

            // 3 unconsumed states (one without an external ID)

            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.isNull()
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                vaultQuerySvc.queryBy<LinearState>(externalIdCustomCriteria)
            }

            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed linear heads where external id is not null`() {
        database.transaction {

            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1)
            services.fillWithSomeTestLinearStates(1, "TEST3")

            // 3 unconsumed states (two with an external ID)

            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.notNull()
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                vaultQuerySvc.queryBy<LinearState>(externalIdCustomCriteria)
            }

            assertThat(results.states).hasSize(2)
        }
    }

    /**
     * Dynamic trackBy() tests
     */

    @Test
    fun trackCashStates_unconsumed() {
        val updates =
            database.transaction {

                services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 5, 5, Random(0L))
                val linearStates = services.fillWithSomeTestLinearStates(10).states
                val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                // DOCSTART VaultQueryExample15
                val (snapshot, updates)  = vaultQuerySvc.trackBy<Cash.State>()  // UNCONSUMED default
                // DOCEND VaultQueryExample15

                assertThat(snapshot.states).hasSize(5)
                assertThat(snapshot.statesMetadata).hasSize(5)

                // add more cash
                services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
                // add another deal
                services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))

                // consume stuff
                services.consumeCash(100.DOLLARS)
                services.consumeDeals(dealStates.toList())
                services.consumeLinearStates(linearStates.toList())

                updates
            }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 5) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 1) {}
                    }
            )
        }
    }

    @Test
    fun trackCashStates_consumed() {
        val updates =
            database.transaction {

                services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 5, 5, Random(0L))
                val linearStates = services.fillWithSomeTestLinearStates(10).states
                val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                // add more cash
                services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
                // add another deal
                services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))

                // consume stuff
                services.consumeCash(100.POUNDS)

                val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
                val (snapshot, updates)  = vaultQuerySvc.trackBy<Cash.State>(criteria)

                assertThat(snapshot.states).hasSize(1)
                assertThat(snapshot.statesMetadata).hasSize(1)

                // consume more stuff
                services.consumeCash(100.DOLLARS)
                services.consumeDeals(dealStates.toList())
                services.consumeLinearStates(linearStates.toList())

                updates
            }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 1) {}
                        require(produced.size == 0) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 5) {}
                        require(produced.size == 0) {}
                    }
            )
        }
    }

    @Test
    fun trackCashStates_all() {
        val updates =
            database.transaction {

                services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 5, 5, Random(0L))
                val linearStates = services.fillWithSomeTestLinearStates(10).states
                val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                // add more cash
                services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
                // add another deal
                services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))

                // consume stuff
                services.consumeCash(99.POUNDS)

                val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
                val (snapshot, updates) = vaultQuerySvc.trackBy<Cash.State>(criteria)

                assertThat(snapshot.states).hasSize(7)
                assertThat(snapshot.statesMetadata).hasSize(7)

                // consume more stuff
                services.consumeCash(100.DOLLARS)
                services.consumeDeals(dealStates.toList())
                services.consumeLinearStates(linearStates.toList())

                updates
            }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 5) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 1) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 1) {}
                        require(produced.size == 1) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 5) {}
                        require(produced.size == 0) {}
                    }
            )
        }
    }

    @Test
    fun trackLinearStates() {
        val updates =
            database.transaction {

                services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                val linearStates = services.fillWithSomeTestLinearStates(10).states
                val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                // DOCSTART VaultQueryExample16
                val (snapshot, updates)  = vaultQuerySvc.trackBy<LinearState>()
                // DOCEND VaultQueryExample16


                assertThat(snapshot.states).hasSize(13)
                assertThat(snapshot.statesMetadata).hasSize(13)

                // add more cash
                services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
                // add another deal
                services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))

                // consume stuff
                services.consumeCash(100.DOLLARS)
                services.consumeDeals(dealStates.toList())
                services.consumeLinearStates(linearStates.toList())

                updates
            }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 10) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 3) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 1) {}
                    }
            )
        }
    }

    @Test
    fun trackDealStates() {
        val updates =
            database.transaction {

                services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                val linearStates = services.fillWithSomeTestLinearStates(10).states
                val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                // DOCSTART VaultQueryExample17
                val (snapshot, updates)  = vaultQuerySvc.trackBy<DealState>()
                // DOCEND VaultQueryExample17

                assertThat(snapshot.states).hasSize(3)
                assertThat(snapshot.statesMetadata).hasSize(3)

                // add more cash
                services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
                // add another deal
                services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))

                // consume stuff
                services.consumeCash(100.DOLLARS)
                services.consumeDeals(dealStates.toList())
                services.consumeLinearStates(linearStates.toList())

                updates
            }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 3) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 0) {}
                        require(produced.size == 1) {}
                    }
            )
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
