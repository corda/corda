package net.corda.node.services.vault

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.*
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.QueryCriteria.*
import net.corda.core.utilities.*
import net.corda.finance.*
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.Commodity
import net.corda.finance.contracts.DealState
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.schemas.CashSchemaV1.PersistentCashState
import net.corda.finance.schemas.CommercialPaperSchemaV1
import net.corda.finance.schemas.SampleCashSchemaV3
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.*
import net.corda.testing.contracts.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseAndMockServices
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import net.corda.testing.node.MockServices.Companion.makeTestIdentityService
import net.corda.testing.schemas.DummyLinearStateSchemaV1
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    private lateinit var services: MockServices
    private lateinit var notaryServices: MockServices
    private val vaultService: VaultService get() = services.vaultService
    private val identitySvc: IdentityService = makeTestIdentityService()
    private lateinit var database: CordaPersistence

    // test cash notary
    private val CASH_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(21)) }
    private val CASH_NOTARY: Party get() = Party(CordaX500Name(organisation = "Cash Notary Service", locality = "Zurich", country = "CH"), CASH_NOTARY_KEY.public)
    private val CASH_NOTARY_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CASH_NOTARY.nameOrNull(), CASH_NOTARY_KEY.public)

    @Before
    fun setUp() {
        setCordappPackages("net.corda.testing.contracts", "net.corda.finance.contracts")

        // register additional identities
        identitySvc.verifyAndRegisterIdentity(CASH_NOTARY_IDENTITY)
        identitySvc.verifyAndRegisterIdentity(BOC_IDENTITY)
        val databaseAndServices = makeTestDatabaseAndMockServices(keys = listOf(MEGA_CORP_KEY, DUMMY_NOTARY_KEY),
                createIdentityService = { identitySvc },
                customSchemas = setOf(CashSchemaV1, CommercialPaperSchemaV1, DummyLinearStateSchemaV1))
        database = databaseAndServices.first
        services = databaseAndServices.second
        notaryServices = MockServices(DUMMY_NOTARY_KEY, DUMMY_CASH_ISSUER_KEY, BOC_KEY, MEGA_CORP_KEY)
    }

    @After
    fun tearDown() {
        database.close()
        unsetCordappPackages()
    }

    /**
     * Helper method for generating a Persistent H2 test database
     */
    @Ignore
    @Test
    fun createPersistentTestDb() {
        val database = configureDatabase(makePersistentDataSourceProperties(), makeTestDatabaseProperties(), createIdentityService = { identitySvc })

        setUpDb(database, 5000)

        database.close()
    }

    private fun setUpDb(_database: CordaPersistence, delay: Long = 0) {

        _database.transaction {

            // create new states
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 10, 10, Random(0L))
            val linearStatesXYZ = services.fillWithSomeTestLinearStates(1, "XYZ")
            val linearStatesJKL = services.fillWithSomeTestLinearStates(2, "JKL")
            services.fillWithSomeTestLinearStates(3, "ABC")
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            // Total unconsumed states = 10 + 1 + 2 + 3 + 3 = 19

            sleep(delay)

            // consume some states
            services.consumeLinearStates(linearStatesXYZ.states.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStatesJKL.states.toList(), DUMMY_NOTARY)
            services.consumeDeals(dealStates.states.filter { it.state.data.linearId.externalId == "456" }, DUMMY_NOTARY)
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)

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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            // DOCSTART VaultQueryExample1
            val result = vaultService.queryBy<ContractState>()

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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val result = vaultService.queryBy<ContractState>(criteria)

            assertThat(result.states).hasSize(16)
            assertThat(result.statesMetadata).hasSize(16)
        }
    }

    @Test
    fun `unconsumed states with count`() {
        database.transaction {
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val resultsBeforeConsume = vaultService.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(4)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(4)
        }
        database.transaction {
            services.consumeCash(75.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            val consumedCriteria = VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            val resultsAfterConsume = vaultService.queryBy<ContractState>(consumedCriteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(1)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(1)
        }
    }

    @Test
    fun `unconsumed cash states simple`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            val result = vaultService.queryBy<Cash.State>()

            assertThat(result.states).hasSize(3)
            assertThat(result.statesMetadata).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash states verbose`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            val criteria = VaultQueryCriteria() // default is UNCONSUMED
            val result = vaultService.queryBy<Cash.State>(criteria)

            assertThat(result.states).hasSize(3)
            assertThat(result.statesMetadata).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash states sorted by state ref`() {
        val stateRefs: MutableList<StateRef> = mutableListOf()
        database.transaction {
            val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 10, 10, Random(0L))
            val issuedStateRefs = issuedStates.states.map { it.ref }.toList()
            stateRefs.addAll(issuedStateRefs)
        }
        database.transaction {
            val spentStates = services.consumeCash(25.DOLLARS, notary = DUMMY_NOTARY)
            val consumedStateRefs = spentStates.consumed.map { it.ref }.toList()
            val producedStateRefs = spentStates.produced.map { it.ref }.toList()
            stateRefs.addAll(consumedStateRefs.plus(producedStateRefs))
        }
        database.transaction {
            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
            val criteria = VaultQueryCriteria()
            val results = vaultService.queryBy<Cash.State>(criteria, Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC))))

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
        val consumed = mutableSetOf<SecureHash>()
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 10, 10, Random(0L))
        }
        database.transaction {
            services.consumeCash(10.DOLLARS, notary = DUMMY_NOTARY).consumed.forEach { consumed += it.ref.txhash }
            services.consumeCash(10.DOLLARS, notary = DUMMY_NOTARY).consumed.forEach { consumed += it.ref.txhash }
        }
        database.transaction {
            val sortAttributeTxnId = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID)
            val sortAttributeIndex = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_INDEX)
            val sortBy = Sort(setOf(Sort.SortColumn(sortAttributeTxnId, Sort.Direction.ASC),
                    Sort.SortColumn(sortAttributeIndex, Sort.Direction.ASC)))
            val criteria = VaultQueryCriteria()
            val results = vaultService.queryBy<Cash.State>(criteria, sortBy)

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
        val stateRefs =
                database.transaction {
                    services.fillWithSomeTestLinearStates(8)
                    val issuedStates = services.fillWithSomeTestLinearStates(2)
                    issuedStates.states.map { it.ref }.toList()
                }
        database.transaction {
            // DOCSTART VaultQueryExample2
            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF_TXN_ID)
            val criteria = VaultQueryCriteria(stateRefs = listOf(stateRefs.first(), stateRefs.last()))
            val results = vaultService.queryBy<DummyLinearContract.State>(criteria, Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC))))
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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            // default State.Status is UNCONSUMED
            // DOCSTART VaultQueryExample3
            val criteria = VaultQueryCriteria(contractStateTypes = setOf(Cash.State::class.java, DealState::class.java))
            val results = vaultService.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample3
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `consumed states`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 states with same externalId
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList(), DUMMY_NOTARY)
            services.consumeDeals(dealStates.states.filter { it.state.data.linearId.externalId == "456" }, DUMMY_NOTARY)
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultService.queryBy<ContractState>(criteria)
            assertThat(results.states).hasSize(5)
        }
    }

    @Test
    fun `consumed states with count`() {
        database.transaction {
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(25.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val resultsBeforeConsume = vaultService.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(4)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(4)
        }
        database.transaction {
            services.consumeCash(75.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            val consumedCriteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val resultsAfterConsume = vaultService.queryBy<ContractState>(consumedCriteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(3)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(3)
        }
    }

    @Test
    fun `all states`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 results with same UID
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList(), DUMMY_NOTARY)
            services.consumeDeals(dealStates.states.filter { it.state.data.linearId.externalId == "456" }, DUMMY_NOTARY)
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY) // generates a new change state!
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultService.queryBy<ContractState>(criteria)
            assertThat(results.states).hasSize(17)
        }
    }

    @Test
    fun `all states with count`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        database.transaction {
            val resultsBeforeConsume = vaultService.queryBy<ContractState>(criteria, paging)
            assertThat(resultsBeforeConsume.states).hasSize(1)
            assertThat(resultsBeforeConsume.totalStatesAvailable).isEqualTo(1)
        }
        database.transaction {
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)    // consumed 100 (spent), produced 50 (change)
        }
        database.transaction {
            val resultsAfterConsume = vaultService.queryBy<ContractState>(criteria, paging)
            assertThat(resultsAfterConsume.states).hasSize(2)
            assertThat(resultsAfterConsume.totalStatesAvailable).isEqualTo(2)
        }
    }

    @Test
    fun `unconsumed states by notary`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, CASH_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            // DOCSTART VaultQueryExample4
            val criteria = VaultQueryCriteria(notary = listOf(CASH_NOTARY))
            val results = vaultService.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample4
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed linear states for single participant`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(BIG_CORP_IDENTITY)

            services.fillWithSomeTestLinearStates(2, "TEST", participants = listOf(MEGA_CORP, MINI_CORP))
            services.fillWithSomeTestDeals(listOf("456"), participants = listOf(MEGA_CORP, BIG_CORP))
            services.fillWithSomeTestDeals(listOf("123", "789"), participants = listOf(BIG_CORP))
        }
        database.transaction {
            val criteria = LinearStateQueryCriteria(participants = listOf(BIG_CORP))
            val results = vaultService.queryBy<ContractState>(criteria)
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed linear states for two participants`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(BIG_CORP_IDENTITY)

            services.fillWithSomeTestLinearStates(2, "TEST", participants = listOf(MEGA_CORP, MINI_CORP))
            services.fillWithSomeTestDeals(listOf("456"), participants = listOf(MEGA_CORP, BIG_CORP))
            services.fillWithSomeTestDeals(listOf("123", "789"), participants = listOf(MEGA_CORP))
        }
        database.transaction {
            // DOCSTART VaultQueryExample5
            val criteria = LinearStateQueryCriteria(participants = listOf(BIG_CORP, MINI_CORP))
            val results = vaultService.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample5

            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed states with soft locking`() {
        val (lockId1, lockId2) =
                database.transaction {
                    val issuedStates = services.fillWithSomeTestCash(100.DOLLARS, notaryServices, CASH_NOTARY, 10, 10, Random(0L)).states.toList()
                    vaultService.softLockReserve(UUID.randomUUID(), NonEmptySet.of(issuedStates[1].ref, issuedStates[2].ref, issuedStates[3].ref))
                    val lockId1 = UUID.randomUUID()
                    vaultService.softLockReserve(lockId1, NonEmptySet.of(issuedStates[4].ref, issuedStates[5].ref))
                    val lockId2 = UUID.randomUUID()
                    vaultService.softLockReserve(lockId2, NonEmptySet.of(issuedStates[6].ref))
                    Pair(lockId1, lockId2)
                }
        database.transaction {
            // excluding soft locked states
            val criteriaExclusive = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_ONLY))
            val resultsExclusive = vaultService.queryBy<ContractState>(criteriaExclusive)
            assertThat(resultsExclusive.states).hasSize(4)

            // only soft locked states
            val criteriaLockedOnly = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.LOCKED_ONLY))
            val resultsLockedOnly = vaultService.queryBy<ContractState>(criteriaLockedOnly)
            assertThat(resultsLockedOnly.states).hasSize(6)

            // soft locked states by single lock id
            val criteriaByLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(lockId1)))
            val resultsByLockId = vaultService.queryBy<ContractState>(criteriaByLockId)
            assertThat(resultsByLockId.states).hasSize(2)

            // soft locked states by multiple lock ids
            val criteriaByLockIds = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.SPECIFIED, listOf(lockId1, lockId2)))
            val resultsByLockIds = vaultService.queryBy<ContractState>(criteriaByLockIds)
            assertThat(resultsByLockIds.states).hasSize(3)

            // unlocked and locked by `lockId2`
            val criteriaUnlockedAndByLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId2)))
            val resultsUnlockedAndByLockIds = vaultService.queryBy<ContractState>(criteriaUnlockedAndByLockId)
            assertThat(resultsUnlockedAndByLockIds.states).hasSize(5)

            // missing lockId
            expectedEx.expect(IllegalArgumentException::class.java)
            expectedEx.expectMessage("Must specify one or more lockIds")
            val criteriaMissingLockId = VaultQueryCriteria(softLockingCondition = SoftLockingCondition(SoftLockingType.UNLOCKED_AND_SPECIFIED))
            vaultService.queryBy<ContractState>(criteriaMissingLockId)
        }
    }

    @Test
    fun `logical operator EQUAL`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator NOT EQUAL`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notEqual(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator GREATER_THAN`() {
        database.transaction {
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.greaterThan(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator GREATER_THAN_OR_EQUAL`() {
        database.transaction {
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.greaterThanOrEqual(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator LESS_THAN`() {
        database.transaction {
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.lessThan(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator LESS_THAN_OR_EQUAL`() {
        database.transaction {
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.lessThanOrEqual(1000L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator BETWEEN`() {
        database.transaction {
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::pennies.between(500L, 1500L) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator IN`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val currencies = listOf(CHF.currencyCode, GBP.currencyCode)
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.`in`(currencies) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator NOT IN`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val currencies = listOf(CHF.currencyCode, GBP.currencyCode)
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notIn(currencies) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator LIKE`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.like("%BP") }  // GPB
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `logical operator NOT LIKE`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.notLike("%BP") }  // GPB
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `logical operator IS_NULL`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::issuerParty.isNull() }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(0)
        }
    }

    @Test
    fun `logical operator NOT_NULL`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val logicalExpression = builder { CashSchemaV1.PersistentCashState::issuerParty.notNull() }
            val criteria = VaultCustomQueryCriteria(logicalExpression)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `aggregate functions without group clause`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, notaryServices, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
        }
        database.transaction {
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

            val results = vaultService.queryBy<FungibleAsset<*>>(sumCriteria
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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, notaryServices, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample22
            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val sumCriteria = VaultCustomQueryCriteria(sum)

            val max = builder { CashSchemaV1.PersistentCashState::pennies.max(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val maxCriteria = VaultCustomQueryCriteria(max)

            val min = builder { CashSchemaV1.PersistentCashState::pennies.min(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val minCriteria = VaultCustomQueryCriteria(min)

            val avg = builder { CashSchemaV1.PersistentCashState::pennies.avg(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val avgCriteria = VaultCustomQueryCriteria(avg)

            val results = vaultService.queryBy<FungibleAsset<*>>(sumCriteria
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
            identitySvc.verifyAndRegisterIdentity(BOC_IDENTITY)

            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = DUMMY_CASH_ISSUER)
            services.fillWithSomeTestCash(200.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L), issuedBy = BOC.ref(1))
            services.fillWithSomeTestCash(300.POUNDS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L), issuedBy = DUMMY_CASH_ISSUER)
            services.fillWithSomeTestCash(400.POUNDS, notaryServices, DUMMY_NOTARY, 4, 4, Random(0L), issuedBy = BOC.ref(2))
        }
        database.transaction {
            // DOCSTART VaultQueryExample23
            val sum = builder {
                CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::issuerParty,
                        CashSchemaV1.PersistentCashState::currency),
                        orderBy = Sort.Direction.DESC)
            }

            val results = vaultService.queryBy<FungibleAsset<*>>(VaultCustomQueryCriteria(sum))
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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, CASH_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestLinearStates(1, "XYZ")
            services.fillWithSomeTestLinearStates(2, "JKL")
            services.fillWithSomeTestLinearStates(3, "ABC")
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            // count fungible assets
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count)
            val fungibleStateCount = vaultService.queryBy<FungibleAsset<*>>(countCriteria).otherResults.single() as Long
            assertThat(fungibleStateCount).isEqualTo(10L)

            // count linear states
            val linearStateCount = vaultService.queryBy<LinearState>(countCriteria).otherResults.single() as Long
            assertThat(linearStateCount).isEqualTo(9L)

            // count deal states
            val dealStateCount = vaultService.queryBy<DealState>(countCriteria).otherResults.single() as Long
            assertThat(dealStateCount).isEqualTo(3L)
        }
    }

    @Test
    fun `aggregate functions count by contract type and state status`() {
        val (linearStatesJKL, linearStatesXYZ, dealStates) =
                database.transaction {
                    // create new states
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 10, 10, Random(0L))
                    val linearStatesXYZ = services.fillWithSomeTestLinearStates(1, "XYZ")
                    val linearStatesJKL = services.fillWithSomeTestLinearStates(2, "JKL")
                    services.fillWithSomeTestLinearStates(3, "ABC")
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))
                    Triple(linearStatesJKL, linearStatesXYZ, dealStates)
                }
        val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
        database.transaction {
            // count fungible assets
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.ALL)
            val fungibleStateCount = vaultService.queryBy<FungibleAsset<*>>(countCriteria).otherResults.single() as Long
            assertThat(fungibleStateCount).isEqualTo(10L)

            // count linear states
            val linearStateCount = vaultService.queryBy<LinearState>(countCriteria).otherResults.single() as Long
            assertThat(linearStateCount).isEqualTo(9L)

            // count deal states
            val dealStateCount = vaultService.queryBy<DealState>(countCriteria).otherResults.single() as Long
            assertThat(dealStateCount).isEqualTo(3L)
        }
        val cashUpdates =
                database.transaction {
                    // consume some states
                    services.consumeLinearStates(linearStatesXYZ.states.toList(), DUMMY_NOTARY)
                    services.consumeLinearStates(linearStatesJKL.states.toList(), DUMMY_NOTARY)
                    services.consumeDeals(dealStates.states.filter { it.state.data.linearId.externalId == "456" }, DUMMY_NOTARY)
                    services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)

                    // UNCONSUMED states (default)
                }
        database.transaction {
            // count fungible assets
            val countCriteriaUnconsumed = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.UNCONSUMED)
            val fungibleStateCountUnconsumed = vaultService.queryBy<FungibleAsset<*>>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(fungibleStateCountUnconsumed.toInt()).isEqualTo(10 - cashUpdates.consumed.size + cashUpdates.produced.size)

            // count linear states
            val linearStateCountUnconsumed = vaultService.queryBy<LinearState>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(linearStateCountUnconsumed).isEqualTo(5L)

            // count deal states
            val dealStateCountUnconsumed = vaultService.queryBy<DealState>(countCriteriaUnconsumed).otherResults.single() as Long
            assertThat(dealStateCountUnconsumed).isEqualTo(2L)

            // CONSUMED states

            // count fungible assets
            val countCriteriaConsumed = QueryCriteria.VaultCustomQueryCriteria(count, Vault.StateStatus.CONSUMED)
            val fungibleStateCountConsumed = vaultService.queryBy<FungibleAsset<*>>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(fungibleStateCountConsumed.toInt()).isEqualTo(cashUpdates.consumed.size)

            // count linear states
            val linearStateCountConsumed = vaultService.queryBy<LinearState>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(linearStateCountConsumed).isEqualTo(4L)

            // count deal states
            val dealStateCountConsumed = vaultService.queryBy<DealState>(countCriteriaConsumed).otherResults.single() as Long
            assertThat(dealStateCountConsumed).isEqualTo(1L)
        }
    }

    private val TODAY = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)

    @Test
    fun `unconsumed states recorded between two time intervals`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, CASH_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample6
            val start = TODAY
            val end = TODAY.plus(30, ChronoUnit.DAYS)
            val recordedBetweenExpression = TimeCondition(
                    QueryCriteria.TimeInstantType.RECORDED,
                    ColumnPredicate.Between(start, end))
            val criteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)
            val results = vaultService.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample6
            assertThat(results.states).hasSize(3)

            // Future
            val startFuture = TODAY.plus(1, ChronoUnit.DAYS)
            val recordedBetweenExpressionFuture = TimeCondition(
                    QueryCriteria.TimeInstantType.RECORDED, ColumnPredicate.Between(startFuture, end))
            val criteriaFuture = VaultQueryCriteria(timeCondition = recordedBetweenExpressionFuture)
            assertThat(vaultService.queryBy<ContractState>(criteriaFuture).states).isEmpty()
        }
    }

    @Test
    fun `states consumed after time`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            val asOfDateTime = TODAY
            val consumedAfterExpression = TimeCondition(
                    QueryCriteria.TimeInstantType.CONSUMED, ColumnPredicate.BinaryComparison(BinaryComparisonOperator.GREATER_THAN_OR_EQUAL, asOfDateTime))
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED,
                    timeCondition = consumedAfterExpression)
            val results = vaultService.queryBy<ContractState>(criteria)

            assertThat(results.states).hasSize(3)
        }
    }

    // pagination: first page
    @Test
    fun `all states with paging specification - first page`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 100, 100, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample7
            val pagingSpec = PageSpecification(DEFAULT_PAGE_NUM, 10)
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultService.queryBy<ContractState>(criteria, paging = pagingSpec)
            // DOCEND VaultQueryExample7
            assertThat(results.states).hasSize(10)
            assertThat(results.totalStatesAvailable).isEqualTo(100)
        }
    }

    // pagination: last page
    @Test
    fun `all states with paging specification  - last`() {
        database.transaction {
            services.fillWithSomeTestCash(95.DOLLARS, notaryServices, DUMMY_NOTARY, 95, 95, Random(0L))
        }
        database.transaction {
            // Last page implies we need to perform a row count for the Query first,
            // and then re-query for a given offset defined by (count - pageSize)
            val pagingSpec = PageSpecification(10, 10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultService.queryBy<ContractState>(criteria, paging = pagingSpec)
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
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 100, 100, Random(0L))
        }
        database.transaction {
            val pagingSpec = PageSpecification(0, 10)

            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultService.queryBy<ContractState>(criteria, paging = pagingSpec)
        }
    }

    // pagination: invalid page size
    @Test
    fun `invalid page size`() {
        expectedEx.expect(VaultQueryException::class.java)
        expectedEx.expectMessage("Page specification: invalid page size")

        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 100, 100, Random(0L))
        }
        database.transaction {
            @Suppress("EXPECTED_CONDITION")
            val pagingSpec = PageSpecification(DEFAULT_PAGE_NUM, @Suppress("INTEGER_OVERFLOW") MAX_PAGE_SIZE + 1)  // overflow = -2147483648
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultService.queryBy<ContractState>(criteria, paging = pagingSpec)
        }
    }

    // pagination not specified but more than DEFAULT_PAGE_SIZE results available (fail-fast test)
    @Test
    fun `pagination not specified but more than default results available`() {
        expectedEx.expect(VaultQueryException::class.java)
        expectedEx.expectMessage("Please specify a `PageSpecification`")

        database.transaction {
            services.fillWithSomeTestCash(201.DOLLARS, notaryServices, DUMMY_NOTARY, 201, 201, Random(0L))
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            vaultService.queryBy<ContractState>(criteria)
        }
    }

    // sorting
    @Test
    fun `sorting - all states sorted by contract type, state status, consumed time`() {

        setUpDb(database)

        database.transaction {

            val sortCol1 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.CONTRACT_STATE_TYPE), Sort.Direction.DESC)
            val sortCol2 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.STATE_STATUS), Sort.Direction.ASC)
            val sortCol3 = Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.CONSUMED_TIME), Sort.Direction.DESC)
            val sorting = Sort(setOf(sortCol1, sortCol2, sortCol3))
            val result = vaultService.queryBy<ContractState>(VaultQueryCriteria(status = Vault.StateStatus.ALL), sorting = sorting)

            val states = result.states
            val metadata = result.statesMetadata

            for (i in 0 until states.size) {
                println("${states[i].ref} : ${metadata[i].contractStateClassName}, ${metadata[i].status}, ${metadata[i].consumedTime}")
            }

            assertThat(states).hasSize(20)
            assertThat(metadata.first().contractStateClassName).isEqualTo("$DUMMY_LINEAR_CONTRACT_PROGRAM_ID\$State")
            assertThat(metadata.first().status).isEqualTo(Vault.StateStatus.UNCONSUMED) // 0 = UNCONSUMED
            assertThat(metadata.last().contractStateClassName).isEqualTo("net.corda.finance.contracts.asset.Cash\$State")
            assertThat(metadata.last().status).isEqualTo(Vault.StateStatus.CONSUMED)    // 1 = CONSUMED
        }
    }

    @Test
    fun `unconsumed fungible assets`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCommodity(Amount(100, Commodity.getInstance("FCOJ")!!), notaryServices)
            services.fillWithSomeTestLinearStates(10)
        }
        database.transaction {
            val results = vaultService.queryBy<FungibleAsset<*>>()
            assertThat(results.states).hasSize(4)
        }
    }

    @Test
    fun `consumed fungible assets`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            services.fillWithSomeTestCommodity(Amount(100, Commodity.getInstance("FCOJ")!!), notaryServices)
            services.fillWithSomeTestLinearStates(10)
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed cash fungible assets`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
        }
        database.transaction {
            val results = vaultService.queryBy<Cash.State>()
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash fungible assets after spending`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)
        }
        // should now have x2 CONSUMED + x2 UNCONSUMED (one spent + one change)
        database.transaction {
            val results = vaultService.queryBy<Cash.State>(FungibleAssetQueryCriteria())
            assertThat(results.statesMetadata).hasSize(2)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `consumed cash fungible assets`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)
        }
        val linearStates =
                database.transaction {
                    services.fillWithSomeTestLinearStates(10)
                }
        database.transaction {
            services.consumeLinearStates(linearStates.states.toList(), DUMMY_NOTARY)
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultService.queryBy<Cash.State>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            val results = vaultService.queryBy<LinearState>()
            assertThat(results.states).hasSize(13)
        }
    }

    @Test
    fun `consumed linear heads`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            val linearStates = services.fillWithSomeTestLinearStates(2, "TEST") // create 2 states with same externalId
            services.fillWithSomeTestLinearStates(8)
            val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789"))

            services.consumeLinearStates(linearStates.states.toList(), DUMMY_NOTARY)
            services.consumeDeals(dealStates.states.filter { it.state.data.linearId.externalId == "456" }, DUMMY_NOTARY)
            services.consumeCash(50.DOLLARS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val results = vaultService.queryBy<LinearState>(criteria)
            assertThat(results.states).hasSize(3)
        }
    }

    /** LinearState tests */

    @Test
    fun `unconsumed linear heads for linearId without external Id`() {
        val issuedStates =
                database.transaction {
                    services.fillWithSomeTestLinearStates(10)
                }
        database.transaction {
            // DOCSTART VaultQueryExample8
            val linearIds = issuedStates.states.map { it.state.data.linearId }.toList()
            val criteria = LinearStateQueryCriteria(linearId = listOf(linearIds.first(), linearIds.last()))
            val results = vaultService.queryBy<LinearState>(criteria)
            // DOCEND VaultQueryExample8
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads by linearId`() {
        val (linearState1, linearState3) =
                database.transaction {
                    val linearState1 = services.fillWithSomeTestLinearStates(1, "ID1")
                    services.fillWithSomeTestLinearStates(1, "ID2")
                    val linearState3 = services.fillWithSomeTestLinearStates(1, "ID3")
                    Pair(linearState1, linearState3)
                }
        database.transaction {
            val linearIds = listOf(linearState1.states.first().state.data.linearId, linearState3.states.first().state.data.linearId)
            val criteria = LinearStateQueryCriteria(linearId = linearIds)
            val results = vaultService.queryBy<LinearState>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads for linearId by external Id`() {
        val (linearState1, linearState3) =
                database.transaction {
                    val linearState1 = services.fillWithSomeTestLinearStates(1, "ID1")
                    services.fillWithSomeTestLinearStates(1, "ID2")
                    val linearState3 = services.fillWithSomeTestLinearStates(1, "ID3")
                    Pair(linearState1, linearState3)
                }
        database.transaction {
            val externalIds = listOf(linearState1.states.first().state.data.linearId.externalId!!, linearState3.states.first().state.data.linearId.externalId!!)
            val criteria = LinearStateQueryCriteria(externalId = externalIds)
            val results = vaultService.queryBy<LinearState>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `all linear states for a given linear id`() {
        val linearId =
                database.transaction {
                    val txns = services.fillWithSomeTestLinearStates(1, "TEST")
                    val linearState = txns.states.first()
                    services.evolveLinearState(linearState, DUMMY_NOTARY)  // consume current and produce new state reference
                    services.evolveLinearState(linearState, DUMMY_NOTARY)  // consume current and produce new state reference
                    services.evolveLinearState(linearState, DUMMY_NOTARY)  // consume current and produce new state reference
                    linearState.state.data.linearId
                }
        database.transaction {
            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            // DOCSTART VaultQueryExample9
            val linearStateCriteria = LinearStateQueryCriteria(linearId = listOf(linearId), status = Vault.StateStatus.ALL)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val results = vaultService.queryBy<LinearState>(linearStateCriteria and vaultCriteria)
            // DOCEND VaultQueryExample9
            assertThat(results.states).hasSize(4)
        }
    }

    @Test
    fun `all linear states for a given id sorted by uuid`() {
        val linearStates =
                database.transaction {
                    val txns = services.fillWithSomeTestLinearStates(2, "TEST")
                    val linearStates = txns.states.toList()
                    services.evolveLinearStates(linearStates, DUMMY_NOTARY)  // consume current and produce new state reference
                    services.evolveLinearStates(linearStates, DUMMY_NOTARY)  // consume current and produce new state reference
                    services.evolveLinearStates(linearStates, DUMMY_NOTARY)  // consume current and produce new state reference
                    linearStates
                }
        database.transaction {
            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            val linearStateCriteria = LinearStateQueryCriteria(uuid = linearStates.map { it.state.data.linearId.id }, status = Vault.StateStatus.ALL)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC)))

            val results = vaultService.queryBy<LinearState>(linearStateCriteria.and(vaultCriteria), sorting = sorting)
            results.states.forEach { println("${it.state.data.linearId.id}") }
            assertThat(results.states).hasSize(8)
        }
    }

    @Test
    fun `unconsumed linear states sorted by external id`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(1, externalId = "111")
            services.fillWithSomeTestLinearStates(2, externalId = "222")
            services.fillWithSomeTestLinearStates(3, externalId = "333")
        }
        database.transaction {
            val vaultCriteria = VaultQueryCriteria()
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.EXTERNAL_ID), Sort.Direction.DESC)))

            val results = vaultService.queryBy<DummyLinearContract.State>((vaultCriteria), sorting = sorting)
            results.states.forEach { println(it.state.data.linearString) }
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `unconsumed deal states sorted`() {
        val uid =
                database.transaction {
                    val linearStates = services.fillWithSomeTestLinearStates(10)
                    services.fillWithSomeTestDeals(listOf("123", "456", "789"))
                    linearStates.states.first().state.data.linearId.id
                }
        database.transaction {
            val linearStateCriteria = LinearStateQueryCriteria(uuid = listOf(uid))
            val dealStateCriteria = LinearStateQueryCriteria(externalId = listOf("123", "456", "789"))
            val compositeCriteria = linearStateCriteria or dealStateCriteria

            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.EXTERNAL_ID), Sort.Direction.DESC)))

            val results = vaultService.queryBy<LinearState>(compositeCriteria, sorting = sorting)
            assertThat(results.statesMetadata).hasSize(4)
            assertThat(results.states).hasSize(4)
        }
    }

    @Test
    fun `unconsumed linear states sorted by custom attribute`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(1, linearString = "111")
            services.fillWithSomeTestLinearStates(2, linearString = "222")
            services.fillWithSomeTestLinearStates(3, linearString = "333")
        }
        database.transaction {
            val vaultCriteria = VaultQueryCriteria()
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Custom(DummyLinearStateSchemaV1.PersistentDummyLinearState::class.java, "linearString"), Sort.Direction.DESC)))

            val results = vaultService.queryBy<DummyLinearContract.State>((vaultCriteria), sorting = sorting)
            results.states.forEach { println(it.state.data.linearString) }
            assertThat(results.states).hasSize(6)
        }
    }

    @Test
    fun `return consumed linear states for a given linear id`() {
        val txns =
                database.transaction {
                    val txns = services.fillWithSomeTestLinearStates(1, "TEST")
                    val linearState = txns.states.first()
                    val linearState2 = services.evolveLinearState(linearState, DUMMY_NOTARY)  // consume current and produce new state reference
                    val linearState3 = services.evolveLinearState(linearState2, DUMMY_NOTARY)  // consume current and produce new state reference
                    services.evolveLinearState(linearState3, DUMMY_NOTARY)  // consume current and produce new state reference
                    txns
                }
        database.transaction {
            // should now have 1 UNCONSUMED & 3 CONSUMED state refs for Linear State with "TEST"
            val linearStateCriteria = LinearStateQueryCriteria(linearId = txns.states.map { it.state.data.linearId }, status = Vault.StateStatus.CONSUMED)
            val vaultCriteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC)))
            val results = vaultService.queryBy<LinearState>(linearStateCriteria.and(vaultCriteria), sorting = sorting)
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
        }
        database.transaction {
            val results = vaultService.queryBy<DealState>()
            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed deals for ref`() {
        database.transaction {
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }
        database.transaction {
            // DOCSTART VaultQueryExample10
            val criteria = LinearStateQueryCriteria(externalId = listOf("456", "789"))
            val results = vaultService.queryBy<DealState>(criteria)
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
        }
        database.transaction {
            val all = vaultService.queryBy<DealState>()
            all.states.forEach { println(it.state) }

            val criteria = LinearStateQueryCriteria(externalId = listOf("456"))
            val results = vaultService.queryBy<DealState>(criteria)
            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `latest unconsumed deals with party`() {
        val parties = listOf(MINI_CORP)
        database.transaction {
            services.fillWithSomeTestLinearStates(2, "TEST")
            services.fillWithSomeTestDeals(listOf("456"), parties)
            services.fillWithSomeTestDeals(listOf("123", "789"))
        }
        database.transaction {
            // DOCSTART VaultQueryExample11
            val criteria = LinearStateQueryCriteria(participants = parties)
            val results = vaultService.queryBy<DealState>(criteria)
            // DOCEND VaultQueryExample11

            assertThat(results.states).hasSize(1)
        }
    }

    /** FungibleAsset tests */

    @Test
    fun `unconsumed fungible assets for specific issuer party and refs`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(BOC_IDENTITY)

            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), ref = OpaqueBytes.of(1))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(2)), ref = OpaqueBytes.of(2))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(3)), ref = OpaqueBytes.of(3))
        }
        database.transaction {
            val criteria = FungibleAssetQueryCriteria(issuer = listOf(BOC),
                    issuerRef = listOf(BOC.ref(1).reference, BOC.ref(2).reference))
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed fungible assets for selected issuer parties`() {
        // GBP issuer
        val gbpCashIssuerKey = entropyToKeyPair(BigInteger.valueOf(1001))
        val gbpCashIssuer = Party(CordaX500Name(organisation = "British Pounds Cash Issuer", locality = "London", country = "GB"), gbpCashIssuerKey.public).ref(1)
        val gbpCashIssuerServices = MockServices(gbpCashIssuerKey)
        // USD issuer
        val usdCashIssuerKey = entropyToKeyPair(BigInteger.valueOf(1002))
        val usdCashIssuer = Party(CordaX500Name(organisation = "US Dollars Cash Issuer", locality = "New York", country = "US"), usdCashIssuerKey.public).ref(1)
        val usdCashIssuerServices = MockServices(usdCashIssuerKey)
        // CHF issuer
        val chfCashIssuerKey = entropyToKeyPair(BigInteger.valueOf(1003))
        val chfCashIssuer = Party(CordaX500Name(organisation = "Swiss Francs Cash Issuer", locality = "Zurich", country = "CH"), chfCashIssuerKey.public).ref(1)
        val chfCashIssuerServices = MockServices(chfCashIssuerKey)

        database.transaction {

            services.fillWithSomeTestCash(100.POUNDS, gbpCashIssuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (gbpCashIssuer))
            services.fillWithSomeTestCash(100.DOLLARS, usdCashIssuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (usdCashIssuer))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, chfCashIssuerServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (chfCashIssuer))
        }
        database.transaction {
            val criteria = FungibleAssetQueryCriteria(issuer = listOf(gbpCashIssuer.party, usdCashIssuer.party))
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed fungible assets by owner`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = BOC.ref(1))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = MEGA_CORP.ref(0), ownedBy = (MINI_CORP))
        }
        database.transaction {
            val criteria = FungibleAssetQueryCriteria(owner = listOf(MEGA_CORP))
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            assertThat(results.states).hasSize(1)   // can only be 1 owner of a node (MEGA_CORP in this MockServices setup)
        }
    }

    @Test
    fun `unconsumed fungible states for owners`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, CASH_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = MEGA_CORP.ref(0), ownedBy = (MEGA_CORP))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L),
                    issuedBy = BOC.ref(0), ownedBy = (MINI_CORP))  // irrelevant to this vault
        }
        database.transaction {
            // DOCSTART VaultQueryExample5.2
            val criteria = FungibleAssetQueryCriteria(owner = listOf(MEGA_CORP, BOC))
            val results = vaultService.queryBy<ContractState>(criteria)
            // DOCEND VaultQueryExample5.2

            assertThat(results.states).hasSize(2)   // can only be 1 owner of a node (MEGA_CORP in this MockServices setup)
        }
    }

    /** Cash Fungible State specific */
    @Test
    fun `unconsumed fungible assets for single currency`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample12
            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(USD.currencyCode) }
            val criteria = VaultCustomQueryCriteria(ccyIndex)
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample12

            assertThat(results.states).hasSize(3)
        }
    }

    @Test
    fun `unconsumed cash balance for single currency`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L))
        }
        database.transaction {
            val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val sumCriteria = VaultCustomQueryCriteria(sum)

            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(USD.currencyCode) }
            val ccyCriteria = VaultCustomQueryCriteria(ccyIndex)

            val results = vaultService.queryBy<FungibleAsset<*>>(sumCriteria.and(ccyCriteria))

            assertThat(results.otherResults).hasSize(2)
            assertThat(results.otherResults[0]).isEqualTo(30000L)
            assertThat(results.otherResults[1]).isEqualTo("USD")
        }
    }

    @Test
    fun `unconsumed cash balances for all currencies`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(200.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(300.POUNDS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(400.POUNDS, notaryServices, DUMMY_NOTARY, 4, 4, Random(0L))
            services.fillWithSomeTestCash(500.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
            services.fillWithSomeTestCash(600.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 6, 6, Random(0L))
        }
        database.transaction {
            val ccyIndex = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
            val criteria = VaultCustomQueryCriteria(ccyIndex)
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)

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
            services.fillWithSomeTestCash(10.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
            services.fillWithSomeTestCash(25.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(50.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample13
            val fungibleAssetCriteria = FungibleAssetQueryCriteria(quantity = builder { greaterThan(2500L) })
            val results = vaultService.queryBy<Cash.State>(fungibleAssetCriteria)
            // DOCEND VaultQueryExample13

            assertThat(results.states).hasSize(4)  // POUNDS, SWISS_FRANCS
        }
    }

    @Test
    fun `unconsumed fungible assets for issuer party`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(BOC_IDENTITY)

            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)))
        }
        database.transaction {
            // DOCSTART VaultQueryExample14
            val criteria = FungibleAssetQueryCriteria(issuer = listOf(BOC))
            val results = vaultService.queryBy<FungibleAsset<*>>(criteria)
            // DOCEND VaultQueryExample14

            assertThat(results.states).hasSize(1)
        }
    }

    @Test
    fun `unconsumed fungible assets for single currency and quantity greater than`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(50.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(GBP.currencyCode) }
            val customCriteria = VaultCustomQueryCriteria(ccyIndex)
            val fungibleAssetCriteria = FungibleAssetQueryCriteria(quantity = builder { greaterThan(5000L) })
            val results = vaultService.queryBy<Cash.State>(fungibleAssetCriteria.and(customCriteria))

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
                    CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY).let { builder ->
                        builder.setTimeWindow(TEST_TX_TIME, 30.seconds)
                        val stx = services.signInitialTransaction(builder, MEGA_CORP_PUBKEY)
                        notaryServices.addSignature(stx, DUMMY_NOTARY_KEY.public)
                    }

            services.recordTransactions(commercialPaper)

            // MegaCorp™ now issues £10,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue2 = 10000.POUNDS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper2 =
                    CommercialPaper().generateIssue(issuance, faceValue2, TEST_TX_TIME + 30.days, DUMMY_NOTARY).let { builder ->
                        builder.setTimeWindow(TEST_TX_TIME, 30.seconds)
                        val stx = services.signInitialTransaction(builder, MEGA_CORP_PUBKEY)
                        notaryServices.addSignature(stx, DUMMY_NOTARY_KEY.public)
                    }
            services.recordTransactions(commercialPaper2)
        }
        database.transaction {
            val ccyIndex = builder { CommercialPaperSchemaV1.PersistentCommercialPaperState::currency.equal(USD.currencyCode) }
            val criteria1 = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)

            val result = vaultService.queryBy<CommercialPaper.State>(criteria1)

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
                    CommercialPaper().generateIssue(issuance, faceValue, TEST_TX_TIME + 30.days, DUMMY_NOTARY).let { builder ->
                        builder.setTimeWindow(TEST_TX_TIME, 30.seconds)
                        val stx = services.signInitialTransaction(builder, MEGA_CORP_PUBKEY)
                        notaryServices.addSignature(stx, DUMMY_NOTARY_KEY.public)
                    }
            commercialPaper.verifyRequiredSignatures()
            services.recordTransactions(commercialPaper)

            // MegaCorp™ now issues £5,000 of commercial paper, to mature in 30 days, owned by itself.
            val faceValue2 = 5000.POUNDS `issued by` DUMMY_CASH_ISSUER
            val commercialPaper2 =
                    CommercialPaper().generateIssue(issuance, faceValue2, TEST_TX_TIME + 30.days, DUMMY_NOTARY).let { builder ->
                        builder.setTimeWindow(TEST_TX_TIME, 30.seconds)
                        val stx = services.signInitialTransaction(builder, MEGA_CORP_PUBKEY)
                        notaryServices.addSignature(stx, DUMMY_NOTARY_KEY.public)
                    }
            commercialPaper2.verifyRequiredSignatures()
            services.recordTransactions(commercialPaper2)
        }
        database.transaction {
            val result = builder {

                val ccyIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::currency.equal(USD.currencyCode)
                val maturityIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::maturity.greaterThanOrEqual(TEST_TX_TIME + 30.days)
                val faceValueIndex = CommercialPaperSchemaV1.PersistentCommercialPaperState::faceValue.greaterThanOrEqual(10000L)

                val criteria1 = QueryCriteria.VaultCustomQueryCriteria(ccyIndex)
                val criteria2 = QueryCriteria.VaultCustomQueryCriteria(maturityIndex)
                val criteria3 = QueryCriteria.VaultCustomQueryCriteria(faceValueIndex)

                vaultService.queryBy<CommercialPaper.State>(criteria1.and(criteria3).and(criteria2))
            }
            Assertions.assertThat(result.states).hasSize(1)
            Assertions.assertThat(result.statesMetadata).hasSize(1)
        }
    }

    @Test
    fun `query attempting to use unregistered schema`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            // CashSchemaV3 NOT registered with NodeSchemaService
            val logicalExpression = builder { SampleCashSchemaV3.PersistentCashState::currency.equal(GBP.currencyCode) }
            val criteria = VaultCustomQueryCriteria(logicalExpression)

            assertThatThrownBy {
                vaultService.queryBy<Cash.State>(criteria)
            }.isInstanceOf(VaultQueryException::class.java).hasMessageContaining("Please register the entity")
        }
    }

    /** Chaining together different Query Criteria tests**/

    // specifying Query on Cash contract state attributes
    @Test
    fun `custom - all cash states with amount of currency greater or equal than`() {
        database.transaction {
            services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(10.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
            services.fillWithSomeTestCash(1.DOLLARS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
        }
        database.transaction {
            // DOCSTART VaultQueryExample20
            val generalCriteria = VaultQueryCriteria(Vault.StateStatus.ALL)

            val results = builder {
                val currencyIndex = PersistentCashState::currency.equal(USD.currencyCode)
                val quantityIndex = PersistentCashState::pennies.greaterThanOrEqual(10L)

                val customCriteria1 = VaultCustomQueryCriteria(currencyIndex)
                val customCriteria2 = VaultCustomQueryCriteria(quantityIndex)

                val criteria = generalCriteria.and(customCriteria1.and(customCriteria2))
                vaultService.queryBy<Cash.State>(criteria)
            }
            // DOCEND VaultQueryExample20

            assertThat(results.states).hasSize(3)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for linearId between two timestamps`() {
        val start = Instant.now()
        val end = start.plus(1, ChronoUnit.SECONDS)

        database.transaction {
            services.fillWithSomeTestLinearStates(1, "TEST")
            sleep(1000)
            services.fillWithSomeTestLinearStates(1, "TEST")

            // 2 unconsumed states with same external ID
        }
        database.transaction {
            val recordedBetweenExpression = TimeCondition(TimeInstantType.RECORDED, builder { between(start, end) })
            val basicCriteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)

            val results = vaultService.queryBy<LinearState>(basicCriteria)

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for a given external id`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1, "TEST2")
        }
        database.transaction {
            // 2 unconsumed states with same external ID
            val externalIdCondition = builder { VaultSchemaV1.VaultLinearStates::externalId.equal("TEST2") }
            val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

            val results = vaultService.queryBy<LinearState>(externalIdCustomCriteria)

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for linearId between two timestamps for a given external id`() {
        val start = Instant.now()
        val end = start.plus(1, ChronoUnit.SECONDS)

        database.transaction {
            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1, "TEST2")
            sleep(1000)
            services.fillWithSomeTestLinearStates(1, "TEST3")
        }
        database.transaction {
            // 2 unconsumed states with same external ID

            val results = builder {
                val linearIdCondition = VaultSchemaV1.VaultLinearStates::externalId.equal("TEST2")
                val customCriteria = VaultCustomQueryCriteria(linearIdCondition)

                val recordedBetweenExpression = TimeCondition(TimeInstantType.RECORDED, between(start, end))
                val basicCriteria = VaultQueryCriteria(timeCondition = recordedBetweenExpression)

                val criteria = basicCriteria.and(customCriteria)
                vaultService.queryBy<LinearState>(criteria)
            }

            assertThat(results.states).hasSize(1)
        }
    }

    // specifying Query on Linear state attributes
    @Test
    fun `unconsumed linear heads for a given external id or uuid`() {
        val uuid =
                database.transaction {
                    services.fillWithSomeTestLinearStates(1, "TEST1")
                    val aState = services.fillWithSomeTestLinearStates(1, "TEST2").states
                    services.consumeLinearStates(aState.toList(), DUMMY_NOTARY)
                    services.fillWithSomeTestLinearStates(1, "TEST1").states.first().state.data.linearId.id

                    // 2 unconsumed states with same external ID, 1 consumed with different external ID
                }
        database.transaction {
            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.equal("TEST1")
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                val uuidCondition = VaultSchemaV1.VaultLinearStates::uuid.equal(uuid)
                val uuidCustomCriteria = VaultCustomQueryCriteria(uuidCondition)

                val criteria = externalIdCustomCriteria or uuidCustomCriteria
                vaultService.queryBy<LinearState>(criteria)
            }
            assertThat(results.statesMetadata).hasSize(2)
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `unconsumed linear heads for single participant`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(ALICE_IDENTITY)
            services.fillWithSomeTestLinearStates(1, "TEST1", listOf(ALICE))
            services.fillWithSomeTestLinearStates(1)
            services.fillWithSomeTestLinearStates(1, "TEST3")
        }
        database.transaction {
            val linearStateCriteria = LinearStateQueryCriteria(participants = listOf(ALICE))
            val results = vaultService.queryBy<LinearState>(linearStateCriteria)

            assertThat(results.states).hasSize(1)
            assertThat(results.states[0].state.data.linearId.externalId).isEqualTo("TEST1")
        }
    }

    @Test
    fun `unconsumed linear heads for multiple participants`() {
        database.transaction {
            identitySvc.verifyAndRegisterIdentity(ALICE_IDENTITY)
            identitySvc.verifyAndRegisterIdentity(BOB_IDENTITY)
            identitySvc.verifyAndRegisterIdentity(CHARLIE_IDENTITY)

            services.fillWithSomeTestLinearStates(1, "TEST1", listOf(ALICE, BOB, CHARLIE))
            services.fillWithSomeTestLinearStates(1)
            services.fillWithSomeTestLinearStates(1, "TEST3")
        }
        database.transaction {
            val linearStateCriteria = LinearStateQueryCriteria(participants = listOf(ALICE, BOB, CHARLIE))
            val results = vaultService.queryBy<LinearState>(linearStateCriteria)

            assertThat(results.states).hasSize(1)
            assertThat(results.states[0].state.data.linearId.externalId).isEqualTo("TEST1")
        }
    }

    @Test
    fun `unconsumed linear heads where external id is null`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(1, "TEST1")
            services.fillWithSomeTestLinearStates(1)
            services.fillWithSomeTestLinearStates(1, "TEST3")

            // 3 unconsumed states (one without an external ID)
        }
        database.transaction {
            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.isNull()
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                vaultService.queryBy<LinearState>(externalIdCustomCriteria)
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
        }
        database.transaction {
            val results = builder {
                val externalIdCondition = VaultSchemaV1.VaultLinearStates::externalId.notNull()
                val externalIdCustomCriteria = VaultCustomQueryCriteria(externalIdCondition)

                vaultService.queryBy<LinearState>(externalIdCustomCriteria)
            }
            assertThat(results.states).hasSize(2)
        }
    }

    @Test
    fun `enriched and overridden composite query handles defaults correctly`() {
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCommodity(Amount(100, Commodity.getInstance("FCOJ")!!), notaryServices)
            services.fillWithSomeTestLinearStates(1, "ABC")
            services.fillWithSomeTestDeals(listOf("123"))
        }

        database.transaction {
            // Base criteria
            val baseCriteria = VaultQueryCriteria(notary = listOf(DUMMY_NOTARY),
                    status = Vault.StateStatus.CONSUMED)

            // Enrich and override QueryCriteria with additional default attributes (such as soft locks)
            val enrichedCriteria = VaultQueryCriteria(contractStateTypes = setOf(DealState::class.java), // enrich
                    softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(UUID.randomUUID())),
                    status = Vault.StateStatus.UNCONSUMED)  // override
            // Sorting
            val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
            val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))

            // Execute query
            val results = services.vaultService.queryBy<FungibleAsset<*>>(baseCriteria and enrichedCriteria, sorter).states
            assertThat(results).hasSize(4)
        }
    }

    /**
     * Dynamic trackBy() tests
     */

    @Test
    fun trackCashStates_unconsumed() {
        val updates =
                database.transaction {
                    // DOCSTART VaultQueryExample15
                    vaultService.trackBy<Cash.State>().updates     // UNCONSUMED default
                    // DOCEND VaultQueryExample15
                }
        val (linearStates, dealStates) =
                database.transaction {
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
                    val linearStates = services.fillWithSomeTestLinearStates(10).states
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states
                    // add more cash
                    services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
                    // add another deal
                    services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))
                    Pair(linearStates, dealStates)
                }
        database.transaction {
            // consume stuff
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
            services.consumeDeals(dealStates.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStates.toList(), DUMMY_NOTARY)
        }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 5) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 1) {}
                    }
            )
        }
    }

    @Test
    fun trackCashStates_consumed() {
        val updates =
                database.transaction {
                    val criteria = VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
                    vaultService.trackBy<Cash.State>(criteria).updates
                }
        val (linearStates, dealStates) =
                database.transaction {
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
                    val linearStates = services.fillWithSomeTestLinearStates(10).states
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states

                    // add more cash
                    services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
                    // add another deal
                    services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))
                    Pair(linearStates, dealStates)
                }
        database.transaction {
            // consume stuff
            services.consumeCash(100.POUNDS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            // consume more stuff
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
            services.consumeDeals(dealStates.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStates.toList(), DUMMY_NOTARY)
        }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 1) {}
                        require(produced.isEmpty()) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.size == 5) {}
                        require(produced.isEmpty()) {}
                    }
            )
        }
    }

    @Test
    fun trackCashStates_all() {
        val updates =
                database.transaction {
                    val criteria = VaultQueryCriteria(status = Vault.StateStatus.ALL)
                    vaultService.trackBy<Cash.State>(criteria).updates
                }
        val (linearStates, dealStates) =
                database.transaction {
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 5, 5, Random(0L))
                    val linearStates = services.fillWithSomeTestLinearStates(10).states
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states
                    // add more cash
                    services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
                    // add another deal
                    services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))
                    Pair(linearStates, dealStates)
                }
        database.transaction {
            // consume stuff
            services.consumeCash(99.POUNDS, notary = DUMMY_NOTARY)
        }
        database.transaction {
            // consume more stuff
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
            services.consumeDeals(dealStates.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStates.toList(), DUMMY_NOTARY)
        }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 5) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
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
                        require(produced.isEmpty()) {}
                    }
            )
        }
    }

    @Test
    fun trackLinearStates() {
        val updates =
                database.transaction {
                    // DOCSTART VaultQueryExample16
                    val (snapshot, updates) = vaultService.trackBy<LinearState>()
                    // DOCEND VaultQueryExample16
                    assertThat(snapshot.states).hasSize(0)
                    updates
                }
        val (linearStates, dealStates) =
                database.transaction {
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
                    val linearStates = services.fillWithSomeTestLinearStates(10).states
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states
                    // add more cash
                    services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
                    // add another deal
                    services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))
                    Pair(linearStates, dealStates)
                }
        database.transaction {
            // consume stuff
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
            services.consumeDeals(dealStates.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStates.toList(), DUMMY_NOTARY)
        }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 10) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 3) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 1) {}
                    }
            )
        }
    }

    @Test
    fun trackDealStates() {
        val updates =
                database.transaction {
                    // DOCSTART VaultQueryExample17
                    val (snapshot, updates) = vaultService.trackBy<DealState>()
                    // DOCEND VaultQueryExample17
                    assertThat(snapshot.states).hasSize(0)
                    updates
                }
        val (linearStates, dealStates) =
                database.transaction {
                    services.fillWithSomeTestCash(100.DOLLARS, notaryServices, DUMMY_NOTARY, 3, 3, Random(0L))
                    val linearStates = services.fillWithSomeTestLinearStates(10).states
                    val dealStates = services.fillWithSomeTestDeals(listOf("123", "456", "789")).states
                    // add more cash
                    services.fillWithSomeTestCash(100.POUNDS, notaryServices, DUMMY_NOTARY, 1, 1, Random(0L))
                    // add another deal
                    services.fillWithSomeTestDeals(listOf("SAMPLE DEAL"))
                    Pair(linearStates, dealStates)
                }
        database.transaction {
            // consume stuff
            services.consumeCash(100.DOLLARS, notary = DUMMY_NOTARY)
            services.consumeDeals(dealStates.toList(), DUMMY_NOTARY)
            services.consumeLinearStates(linearStates.toList(), DUMMY_NOTARY)
        }

        updates.expectEvents {
            sequence(
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
                        require(produced.size == 3) {}
                    },
                    expect { (consumed, produced, flowId) ->
                        require(flowId == null) {}
                        require(consumed.isEmpty()) {}
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
