package net.corda.node.services.persistence

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.toBase58String
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.finance.contracts.asset.*
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.schemas.SampleCashSchemaV2
import net.corda.finance.schemas.SampleCashSchemaV3
import net.corda.finance.utils.sumCash
import net.corda.node.services.config.DatabaseConfig
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.*
import net.corda.testing.contracts.consumeCash
import net.corda.testing.contracts.fillWithSomeTestCash
import net.corda.testing.contracts.fillWithSomeTestDeals
import net.corda.testing.contracts.fillWithSomeTestLinearStates
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.schemas.DummyLinearStateSchemaV1
import net.corda.testing.schemas.DummyLinearStateSchemaV2
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.Tuple
import javax.persistence.criteria.CriteriaBuilder

class HibernateConfigurationTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    lateinit var services: MockServices
    lateinit var bankServices: MockServices
    lateinit var issuerServices: MockServices
    lateinit var notaryServices: MockServices
    lateinit var database: CordaPersistence
    val vault: VaultService get() = services.vaultService

    // Hibernate configuration objects
    lateinit var hibernateConfig: HibernateConfiguration
    lateinit var hibernatePersister: HibernateObserver
    lateinit var sessionFactory: SessionFactory
    lateinit var entityManager: EntityManager
    lateinit var criteriaBuilder: CriteriaBuilder

    // Identities used
    private lateinit var identity: Party
    private lateinit var issuer: Party
    private lateinit var notary: Party

    // test States
    lateinit var cashStates: List<StateAndRef<Cash.State>>

    @Before
    fun setUp() {
        val cordappPackages = listOf("net.corda.testing.contracts", "net.corda.finance.contracts.asset")
        bankServices = MockServices(cordappPackages, BOC.name, BOC_KEY)
        issuerServices = MockServices(cordappPackages, DUMMY_CASH_ISSUER_NAME, DUMMY_CASH_ISSUER_KEY)
        notaryServices = MockServices(cordappPackages, DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        val dataSourceProps = makeTestDataSourceProperties()
        val identityService = rigorousMock<IdentityService>().also { mock ->
            doReturn(null).whenever(mock).wellKnownPartyFromAnonymous(any<AbstractParty>())
            listOf(DUMMY_CASH_ISSUER_IDENTITY.party, DUMMY_NOTARY).forEach {
                doReturn(it).whenever(mock).wellKnownPartyFromAnonymous(it)
                doReturn(it).whenever(mock).wellKnownPartyFromX500Name(it.name)
            }
        }
        database = configureDatabase(dataSourceProps, DatabaseConfig(), identityService)
        database.transaction {
            hibernateConfig = database.hibernateConfig
            // `consumeCash` expects we can self-notarise transactions
            services = object : MockServices(cordappPackages, BOB_NAME, generateKeyPair(), DUMMY_NOTARY_KEY) {
                override val vaultService = makeVaultService(database.hibernateConfig)
                override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
                    }
                    // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                    vaultService.notifyAll(statesToRecord, txs.map { it.tx })
                }

                override fun jdbcSession() = database.createSession()
            }
            hibernatePersister = services.hibernatePersister
        }

        identity = services.myInfo.singleIdentity()
        issuer = issuerServices.myInfo.singleIdentity()
        notary = notaryServices.myInfo.singleIdentity()

        database.transaction {
            val numStates = 10
            cashStates = services.fillWithSomeTestCash(100.DOLLARS, issuerServices, notary, numStates, numStates, Random(0L), issuedBy = issuer.ref(1))
                    .states.toList()
        }

        sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, CashSchemaV1, SampleCashSchemaV2, SampleCashSchemaV3)
        entityManager = sessionFactory.createEntityManager()
        criteriaBuilder = sessionFactory.criteriaBuilder
    }

    private fun sessionFactoryForSchemas(vararg schemas: MappedSchema) = hibernateConfig.sessionFactoryForSchemas(schemas.toSet())

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun `count rows`() {
        // structure query
        val countQuery = criteriaBuilder.createQuery(Long::class.java)
        countQuery.select(criteriaBuilder.count(countQuery.from(VaultSchemaV1.VaultStates::class.java)))

        // execute query
        val countResult = entityManager.createQuery(countQuery).singleResult

        assertThat(countResult).isEqualTo(10)
    }

    @Test
    fun `consumed states`() {
        database.transaction {
            services.consumeCash(50.DOLLARS, notary = notary)
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.where(criteriaBuilder.equal(
                vaultStates.get<Vault.StateStatus>("stateStatus"), Vault.StateStatus.CONSUMED))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        val coins = queryResults.map {
            (services.loadState(toStateRef(it.stateRef!!)) as TransactionState<Cash.State>).data
        }.sumCash()
        assertThat(coins.toDecimal() >= BigDecimal("50.00"))
    }

    @Test
    fun `select by composite primary key`() {
        val issuedStates =
                database.transaction {
                    services.fillWithSomeTestLinearStates(8)
                    services.fillWithSomeTestLinearStates(2)
                }
        val persistentStateRefs = issuedStates.states.map { PersistentStateRef(it.ref) }.toList()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val compositeKey = vaultStates.get<PersistentStateRef>("stateRef")
        criteriaQuery.where(criteriaBuilder.and(compositeKey.`in`(persistentStateRefs)))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        assertThat(queryResults).hasSize(2)
        assertThat(queryResults.first().stateRef?.txId).isEqualTo(issuedStates.states.first().ref.txhash.toString())
        assertThat(queryResults.first().stateRef?.index).isEqualTo(issuedStates.states.first().ref.index)
        assertThat(queryResults.last().stateRef?.txId).isEqualTo(issuedStates.states.last().ref.txhash.toString())
        assertThat(queryResults.last().stateRef?.index).isEqualTo(issuedStates.states.last().ref.index)
    }

    @Test
    fun `distinct contract types`() {
        database.transaction {
            // add 2 more contract types
            services.fillWithSomeTestLinearStates(10)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(String::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.select(vaultStates.get("contractStateClassName")).distinct(true)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        Assertions.assertThat(queryResults.size).isEqualTo(3)
    }

    @Test
    fun `with sorting`() {
        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

        // order by DESC
        criteriaQuery.orderBy(criteriaBuilder.desc(vaultStates.get<Instant>("recordedTime")))
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        queryResults.map { println(it.recordedTime) }

        // order by ASC
        criteriaQuery.orderBy(criteriaBuilder.asc(vaultStates.get<Instant>("recordedTime")))
        val queryResultsAsc = entityManager.createQuery(criteriaQuery).resultList
        queryResultsAsc.map { println(it.recordedTime) }
    }

    @Test
    fun `with sorting by state ref desc and asc`() {
        // generate additional state ref indexes
        database.transaction {
            services.consumeCash(1.DOLLARS, notary = notary)
            services.consumeCash(2.DOLLARS, notary = notary)
            services.consumeCash(3.DOLLARS, notary = notary)
            services.consumeCash(4.DOLLARS, notary = notary)
            services.consumeCash(5.DOLLARS, notary = notary)
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

        val sortByStateRef = vaultStates.get<PersistentStateRef>("stateRef")

        // order by DESC
        criteriaQuery.orderBy(criteriaBuilder.desc(sortByStateRef))
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        println("DESC by stateRef")
        queryResults.map { println(it.stateRef) }

        // order by ASC
        criteriaQuery.orderBy(criteriaBuilder.asc(sortByStateRef))
        val queryResultsAsc = entityManager.createQuery(criteriaQuery).resultList
        println("ASC by stateRef")
        queryResultsAsc.map { println(it.stateRef) }
    }

    @Test
    fun `with sorting by state ref index and txId desc and asc`() {
        // generate additional state ref indexes
        database.transaction {
            services.consumeCash(1.DOLLARS, notary = notary)
            services.consumeCash(2.DOLLARS, notary = notary)
            services.consumeCash(3.DOLLARS, notary = notary)
            services.consumeCash(4.DOLLARS, notary = notary)
            services.consumeCash(5.DOLLARS, notary = notary)
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

        val sortByIndex = vaultStates.get<PersistentStateRef>("stateRef").get<String>("index")
        val sortByTxId = vaultStates.get<PersistentStateRef>("stateRef").get<String>("txId")

        // order by DESC
        criteriaQuery.orderBy(criteriaBuilder.desc(sortByIndex), criteriaBuilder.desc(sortByTxId))
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        println("DESC by index txId")
        queryResults.map { println(it.stateRef) }

        // order by ASC
        criteriaQuery.orderBy(criteriaBuilder.asc(sortByIndex), criteriaBuilder.asc(sortByTxId))
        val queryResultsAsc = entityManager.createQuery(criteriaQuery).resultList
        println("ASC by index txId")
        queryResultsAsc.map { println(it.stateRef) }
    }

    @Test
    fun `with pagination`() {
        // add 100 additional cash entries
        database.transaction {
            services.fillWithSomeTestCash(1000.POUNDS, issuerServices, notary, 100, 100, Random(0L), issuedBy = issuer.ref(1))
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)

        // set pagination
        val query = entityManager.createQuery(criteriaQuery)
        query.firstResult = 10
        query.maxResults = 15

        // execute query
        val queryResults = query.resultList
        Assertions.assertThat(queryResults.size).isEqualTo(15)

        // try towards end
        query.firstResult = 100
        query.maxResults = 15

        val lastQueryResults = query.resultList

        Assertions.assertThat(lastQueryResults.size).isEqualTo(10)
    }

    /**
     *  VaultLinearState is a concrete table, extendible by any Contract extending a LinearState
     */
    @Test
    fun `select by composite primary key on LinearStates`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(10)
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)

        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)

        criteriaQuery.select(vaultStates)
        criteriaQuery.where(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef")))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    /**
     *  VaultFungibleState is an abstract entity, which should be extended any Contract extending a FungibleAsset
     */

    /**
     *  CashSchemaV1 = original Cash schema (extending PersistentState)
     */
    @Test
    fun `count CashStates`() {
        // structure query
        val countQuery = criteriaBuilder.createQuery(Long::class.java)
        countQuery.select(criteriaBuilder.count(countQuery.from(CashSchemaV1.PersistentCashState::class.java)))

        // execute query
        val countResult = entityManager.createQuery(countQuery).singleResult

        assertThat(countResult).isEqualTo(10)
    }

    @Test
    fun `select by composite primary key on CashStates`() {
        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        vaultStates.join<VaultSchemaV1.VaultStates, CashSchemaV1.PersistentCashState>("stateRef")

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    @Test
    fun `select and join by composite primary key on CashStates`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(5)

            // structure query
            val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
            val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            val vaultCashStates = criteriaQuery.from(CashSchemaV1.PersistentCashState::class.java)

            criteriaQuery.select(vaultStates)
            criteriaQuery.where(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultCashStates.get<PersistentStateRef>("stateRef")))

            // execute query
            val queryResults = entityManager.createQuery(criteriaQuery).resultList
            assertThat(queryResults).hasSize(10)
        }
    }

    @Test
    fun `calculate cash balances`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, notary, 10, issuer.ref(1))        // +$100 = $200
            services.fillWithSomeTestCash(50.POUNDS, issuerServices, notary, 5, issuer.ref(1))            // £50 = £50
            services.fillWithSomeTestCash(25.POUNDS, issuerServices, notary, 5, issuer.ref(1))            // +£25 = £175
            services.fillWithSomeTestCash(500.SWISS_FRANCS, issuerServices, notary, 10, issuer.ref(1))   // CHF500 = CHF500
            services.fillWithSomeTestCash(250.SWISS_FRANCS, issuerServices, notary, 5, issuer.ref(1))     // +CHF250 = CHF750
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
        val cashStates = criteriaQuery.from(CashSchemaV1.PersistentCashState::class.java)

        // aggregate function
        criteriaQuery.multiselect(cashStates.get<String>("currency"),
                criteriaBuilder.sum(cashStates.get<Long>("pennies")))
        // group by
        criteriaQuery.groupBy(cashStates.get<String>("currency"))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        queryResults.forEach { tuple -> println("${tuple.get(0)} = ${tuple.get(1)}") }

        assertThat(queryResults[0].get(0)).isEqualTo("CHF")
        assertThat(queryResults[0].get(1)).isEqualTo(75000L)
        assertThat(queryResults[1].get(0)).isEqualTo("GBP")
        assertThat(queryResults[1].get(1)).isEqualTo(7500L)
        assertThat(queryResults[2].get(0)).isEqualTo("USD")
        assertThat(queryResults[2].get(1)).isEqualTo(20000L)
    }

    @Test
    fun `calculate cash balance for single currency`() {
        database.transaction {
            services.fillWithSomeTestCash(50.POUNDS, issuerServices, notary, 5, issuer.ref(1))            // £50 = £50
            services.fillWithSomeTestCash(25.POUNDS, issuerServices, notary, 5, issuer.ref(1))            // +£25 = £175
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
        val cashStates = criteriaQuery.from(CashSchemaV1.PersistentCashState::class.java)

        // aggregate function
        criteriaQuery.multiselect(cashStates.get<String>("currency"),
                criteriaBuilder.sum(cashStates.get<Long>("pennies")))

        // where
        criteriaQuery.where(criteriaBuilder.equal(cashStates.get<String>("currency"), "GBP"))

        // group by
        criteriaQuery.groupBy(cashStates.get<String>("currency"))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        queryResults.forEach { tuple -> println("${tuple.get(0)} = ${tuple.get(1)}") }

        assertThat(queryResults[0].get(0)).isEqualTo("GBP")
        assertThat(queryResults[0].get(1)).isEqualTo(7500L)
    }

    @Test
    fun `calculate and order by cash balance for owner and currency`() {
        database.transaction {
            val bank = bankServices.myInfo.legalIdentities.single()
            services.fillWithSomeTestCash(200.DOLLARS, bankServices, notary, 2, bank.ref(1))
            services.fillWithSomeTestCash(300.POUNDS, issuerServices, notary, 3, issuer.ref(1))
            services.fillWithSomeTestCash(400.POUNDS, bankServices, notary, 4, bank.ref(2))
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
        val cashStates = criteriaQuery.from(CashSchemaV1.PersistentCashState::class.java)

        // aggregate function
        criteriaQuery.multiselect(cashStates.get<String>("currency"),
                criteriaBuilder.sum(cashStates.get<Long>("pennies")))

        // group by
        criteriaQuery.groupBy(cashStates.get<String>("issuerPartyHash"), cashStates.get<String>("currency"))

        // order by
        criteriaQuery.orderBy(criteriaBuilder.desc(criteriaBuilder.sum(cashStates.get<Long>("pennies"))))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        queryResults.forEach { tuple -> println("${tuple.get(0)} = ${tuple.get(1)}") }

        assertThat(queryResults).hasSize(4)
        assertThat(queryResults[0].get(0)).isEqualTo("GBP")
        assertThat(queryResults[0].get(1)).isEqualTo(40000L)
        assertThat(queryResults[1].get(0)).isEqualTo("GBP")
        assertThat(queryResults[1].get(1)).isEqualTo(30000L)
        assertThat(queryResults[2].get(0)).isEqualTo("USD")
        assertThat(queryResults[2].get(1)).isEqualTo(20000L)
        assertThat(queryResults[3].get(0)).isEqualTo("USD")
        assertThat(queryResults[3].get(1)).isEqualTo(10000L)
    }

    /**
     *  CashSchemaV2 = optimised Cash schema (extending FungibleState)
     */
    @Test
    fun `count CashStates in V2`() {
        database.transaction {
            // persist cash states explicitly with V2 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV2)
            }
        }

        // structure query
        val countQuery = criteriaBuilder.createQuery(Long::class.java)
        countQuery.select(criteriaBuilder.count(countQuery.from(SampleCashSchemaV2.PersistentCashState::class.java)))

        // execute query
        val countResult = entityManager.createQuery(countQuery).singleResult

        assertThat(countResult).isEqualTo(10)
    }

    @Test
    fun `select by composite primary key on CashStates in V2`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(5)

            // persist cash states explicitly with V2 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV2)
            }
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultCashStates = criteriaQuery.from(SampleCashSchemaV2.PersistentCashState::class.java)

        criteriaQuery.select(vaultStates)
        criteriaQuery.where(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultCashStates.get<PersistentStateRef>("stateRef")))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    /**
     *  Represents a 3-way join between:
     *      - VaultStates
     *      - VaultLinearStates
     *      - a concrete LinearState implementation (eg. DummyLinearState)
     */

    /**
     *  DummyLinearStateV1 = original DummyLinearState schema (extending PersistentState)
     */
    @Test
    fun `select by composite primary between VaultStates, VaultLinearStates and DummyLinearStates`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
            services.fillWithSomeTestLinearStates(2)
        }
        val sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, DummyLinearStateSchemaV1)
        val criteriaBuilder = sessionFactory.criteriaBuilder
        val entityManager = sessionFactory.createEntityManager()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)
        val dummyLinearStates = criteriaQuery.from(DummyLinearStateSchemaV1.PersistentDummyLinearState::class.java)

        criteriaQuery.select(vaultStates)
        val joinPredicate1 = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        val joinPredicate2 = criteriaBuilder.and(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), dummyLinearStates.get<PersistentStateRef>("stateRef")))
        criteriaQuery.where(joinPredicate1, joinPredicate2)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    /**
     *  DummyLinearSchemaV2 = optimised DummyLinear schema (extending LinearState)
     */

    @Test
    fun `three way join by composite primary between VaultStates, VaultLinearStates and DummyLinearStates`() {
        database.transaction {
            services.fillWithSomeTestLinearStates(8)
            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
            services.fillWithSomeTestLinearStates(2)
        }
        val sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, DummyLinearStateSchemaV2)
        val criteriaBuilder = sessionFactory.criteriaBuilder
        val entityManager = sessionFactory.createEntityManager()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)
        val dummyLinearStates = criteriaQuery.from(DummyLinearStateSchemaV2.PersistentDummyLinearState::class.java)

        criteriaQuery.select(vaultStates)
        val joinPredicate1 = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        val joinPredicate2 = criteriaBuilder.and(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), dummyLinearStates.get<PersistentStateRef>("stateRef")))
        criteriaQuery.where(joinPredicate1, joinPredicate2)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    /**
     *  Test a OneToOne table mapping
     */
    @Test
    fun `select fungible states by owner party`() {
        database.transaction {
            // persist original cash states explicitly with V3 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
            }
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(SampleCashSchemaV3.PersistentCashState::class.java)
        criteriaQuery.from(SampleCashSchemaV3.PersistentCashState::class.java)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        assertThat(queryResults).hasSize(10)
    }

    /**
     *  Test Query by Party (OneToOne table mapping)
     */
    @Test
    fun `query fungible states by owner party`() {
        database.transaction {
            // persist original cash states explicitly with V3 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
            }

            services.fillWithSomeTestCash(100.DOLLARS, issuerServices, notary, 2, 2, Random(0L),
                    issuedBy = issuer.ref(1), owner = ALICE)
            val cashStates = services.fillWithSomeTestCash(100.DOLLARS, services, notary, 2,  identity.ref(0)).states
            // persist additional cash states explicitly with V3 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
            }
        }
        val sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, CommonSchemaV1, SampleCashSchemaV3)
        val criteriaBuilder = sessionFactory.criteriaBuilder
        val entityManager = sessionFactory.createEntityManager()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)

        // select
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.select(vaultStates)

        // search predicate
        val cashStatesSchema = criteriaQuery.from(SampleCashSchemaV3.PersistentCashState::class.java)

        val queryOwner = identity.name.toString()
        criteriaQuery.where(criteriaBuilder.equal(cashStatesSchema.get<String>("owner"), queryOwner))

        val joinVaultStatesToCash = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), cashStatesSchema.get<PersistentStateRef>("stateRef"))
        criteriaQuery.where(joinVaultStatesToCash)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        queryResults.forEach {
            val cashState = (services.loadState(toStateRef(it.stateRef!!)) as TransactionState<Cash.State>).data
            println("${it.stateRef} with owner: ${cashState.owner.owningKey.toBase58String()}")
        }

        assertThat(queryResults).hasSize(12)
    }

    /**
     *  Test a OneToMany table mapping
     */
    @Test
    fun `select fungible states by participants`() {
        database.transaction {
            // persist cash states explicitly with V2 schema
            cashStates.forEach {
                val cashState = it.state.data
                val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
            }
        }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(SampleCashSchemaV3.PersistentCashState::class.java)
        criteriaQuery.from(SampleCashSchemaV3.PersistentCashState::class.java)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList

        assertThat(queryResults).hasSize(10)
    }

    /**
     *  Test Query by participants (OneToMany table mapping)
     */
    @Test
    fun `query fungible states by participants`() {
        val firstCashState =
                database.transaction {
                    // persist original cash states explicitly with V3 schema
                    cashStates.forEach {
                        val cashState = it.state.data
                        val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                        hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
                    }

                    val moreCash = services.fillWithSomeTestCash(100.DOLLARS, services, notary, 2, 2, Random(0L),
                            issuedBy = identity.ref(0), owner = identity).states
                    // persist additional cash states explicitly with V3 schema
                    moreCash.forEach {
                        val cashState = it.state.data
                        val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                        hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
                    }

                    val cashStates = services.fillWithSomeTestCash(100.DOLLARS, issuerServices, notary, 2, 2, Random(0L), owner = ALICE, issuedBy = issuer.ref(1)).states
                    // persist additional cash states explicitly with V3 schema
                    cashStates.forEach {
                        val cashState = it.state.data
                        val dummyFungibleState = DummyFungibleContract.State(cashState.amount, cashState.owner)
                        hibernatePersister.persistStateWithSchema(dummyFungibleState, it.ref, SampleCashSchemaV3)
                    }
                    cashStates.first()
                }

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(VaultSchemaV1.VaultStates::class.java)

        // select
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        criteriaQuery.select(vaultStates)

        // search predicate
        val cashStatesSchema = criteriaQuery.from(SampleCashSchemaV3.PersistentCashState::class.java)

        val queryParticipants = firstCashState.state.data.participants.map { it.nameOrNull().toString() }
        val joinCashStateToParty = cashStatesSchema.joinSet<SampleCashSchemaV3.PersistentCashState, String>("participants")
        criteriaQuery.where(criteriaBuilder.and(joinCashStateToParty.`in`(queryParticipants)))

        val joinVaultStatesToCash = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), cashStatesSchema.get<PersistentStateRef>("stateRef"))
        criteriaQuery.where(joinVaultStatesToCash)

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        queryResults.forEach {
            val cashState = (services.loadState(toStateRef(it.stateRef!!)) as TransactionState<Cash.State>).data
            println("${it.stateRef} with owner ${cashState.owner.owningKey.toBase58String()} and participants ${cashState.participants.map { it.owningKey.toBase58String() }}")
        }

        assertThat(queryResults).hasSize(12)
    }

    /**
     * Query with sorting on Common table attribute
     */
    @Test
    fun `with sorting on attribute from common table`() {

        database.transaction {
            services.fillWithSomeTestLinearStates(1, externalId = "111")
            services.fillWithSomeTestLinearStates(2, externalId = "222")
            services.fillWithSomeTestLinearStates(3, externalId = "333")
        }
        val sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, DummyLinearStateSchemaV2)
        val criteriaBuilder = sessionFactory.criteriaBuilder
        val entityManager = sessionFactory.createEntityManager()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)

        // join
        criteriaQuery.multiselect(vaultStates, vaultLinearStates)
        val joinPredicate = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        criteriaQuery.where(joinPredicate)

        // order by DESC
        criteriaQuery.orderBy(criteriaBuilder.desc(vaultLinearStates.get<String>("externalId")))
        criteriaQuery.orderBy(criteriaBuilder.desc(vaultLinearStates.get<UUID>("uuid")))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        queryResults.map {
            val vaultState = it[0] as VaultSchemaV1.VaultStates
            val vaultLinearState = it[1] as VaultSchemaV1.VaultLinearStates
            println("${vaultState.stateRef} : ${vaultLinearState.externalId} ${vaultLinearState.uuid}")
        }

        // order by ASC
        criteriaQuery.orderBy(criteriaBuilder.asc(vaultLinearStates.get<String>("externalId")))
        criteriaQuery.orderBy(criteriaBuilder.asc(vaultLinearStates.get<UUID>("uuid")))

        // execute query
        val queryResultsAsc = entityManager.createQuery(criteriaQuery).resultList
        queryResultsAsc.map {
            val vaultState = it[0] as VaultSchemaV1.VaultStates
            val vaultLinearState = it[1] as VaultSchemaV1.VaultLinearStates
            println("${vaultState.stateRef} : ${vaultLinearState.externalId} ${vaultLinearState.uuid}")
        }

        assertThat(queryResults).hasSize(6)
    }

    /**
     * Query with sorting on Custom table attribute
     */
    @Test
    fun `with sorting on attribute from custom table`() {

        database.transaction {
            services.fillWithSomeTestLinearStates(1, externalId = "111")
            services.fillWithSomeTestLinearStates(2, externalId = "222")
            services.fillWithSomeTestLinearStates(3, externalId = "333")
        }
        val sessionFactory = sessionFactoryForSchemas(VaultSchemaV1, DummyLinearStateSchemaV1)
        val criteriaBuilder = sessionFactory.criteriaBuilder
        val entityManager = sessionFactory.createEntityManager()

        // structure query
        val criteriaQuery = criteriaBuilder.createQuery(Tuple::class.java)
        val vaultStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
        val vaultLinearStates = criteriaQuery.from(VaultSchemaV1.VaultLinearStates::class.java)
        val dummyLinearStates = criteriaQuery.from(DummyLinearStateSchemaV1.PersistentDummyLinearState::class.java)

        // join
        criteriaQuery.multiselect(vaultStates, vaultLinearStates, dummyLinearStates)
        val joinPredicate1 = criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), vaultLinearStates.get<PersistentStateRef>("stateRef"))
        val joinPredicate2 = criteriaBuilder.and(criteriaBuilder.equal(vaultStates.get<PersistentStateRef>("stateRef"), dummyLinearStates.get<PersistentStateRef>("stateRef")))
        criteriaQuery.where(joinPredicate1, joinPredicate2)

        // order by DESC
        criteriaQuery.orderBy(criteriaBuilder.desc(dummyLinearStates.get<String>("externalId")))
        criteriaQuery.orderBy(criteriaBuilder.desc(dummyLinearStates.get<UUID>("uuid")))

        // execute query
        val queryResults = entityManager.createQuery(criteriaQuery).resultList
        queryResults.map {
            val vaultState = it[0] as VaultSchemaV1.VaultStates
            val _vaultLinearStates = it[1] as VaultSchemaV1.VaultLinearStates
            val _dummyLinearStates = it[2] as DummyLinearStateSchemaV1.PersistentDummyLinearState
            println("${vaultState.stateRef} : [${_dummyLinearStates.externalId} ${_dummyLinearStates.uuid}] : [${_vaultLinearStates.externalId} ${_vaultLinearStates.uuid}]")
        }

        // order by ASC
        criteriaQuery.orderBy(criteriaBuilder.asc(dummyLinearStates.get<String>("externalId")))
        criteriaQuery.orderBy(criteriaBuilder.asc(dummyLinearStates.get<UUID>("uuid")))

        // execute query
        val queryResultsAsc = entityManager.createQuery(criteriaQuery).resultList
        queryResultsAsc.map {
            val vaultState = it[0] as VaultSchemaV1.VaultStates
            val _vaultLinearStates = it[1] as VaultSchemaV1.VaultLinearStates
            val _dummyLinearStates = it[2] as DummyLinearStateSchemaV1.PersistentDummyLinearState
            println("${vaultState.stateRef} : [${_dummyLinearStates.externalId} ${_dummyLinearStates.uuid}] : [${_vaultLinearStates.externalId} ${_vaultLinearStates.uuid}]")
        }

        assertThat(queryResults).hasSize(6)
    }

    /**
     *  Test invoking SQL query using JDBC connection (session)
     */
    @Test
    fun `test calling an arbitrary JDBC native query`() {
        // DOCSTART JdbcSession
        val nativeQuery = "SELECT v.transaction_id, v.output_index FROM vault_states v WHERE v.state_status = 0"

        database.transaction {
            val jdbcSession = services.jdbcSession()
            val prepStatement = jdbcSession.prepareStatement(nativeQuery)
            val rs = prepStatement.executeQuery()
            // DOCEND JdbcSession
            var count = 0
            while (rs.next()) {
                val stateRef = StateRef(SecureHash.parse(rs.getString(1)), rs.getInt(2))
                Assert.assertTrue(cashStates.map { it.ref }.contains(stateRef))
                count++
            }
            Assert.assertEquals(cashStates.count(), count)
        }
    }

    private fun toStateRef(pStateRef: PersistentStateRef): StateRef {
        return StateRef(SecureHash.parse(pStateRef.txId!!), pStateRef.index!!)
    }
}