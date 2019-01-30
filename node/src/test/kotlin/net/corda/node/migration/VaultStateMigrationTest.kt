package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.hash
import net.corda.core.internal.packageName
import net.corda.core.node.services.Vault
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.vault.DUMMY_LINEAR_CONTRACT_PROGRAM_ID
import net.corda.testing.internal.vault.DummyLinearContract
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import net.corda.testing.node.makeTestIdentityService
import org.junit.*
import org.mockito.Mockito
import java.security.KeyPair
import java.time.Clock
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VaultStateMigrationTest {

    companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val bob = TestIdentity(BOB_NAME, 80)
        private val charlie = TestIdentity(CHARLIE_NAME, 90)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ALICE get() = alice.party
        val ALICE_IDENTITY get() = alice.identity
        val BOB get() = bob.party
        val BOB_IDENTITY get() = bob.identity
        val BOC_IDENTITY get() = bankOfCorda.identity
        val BOC_KEY get() = bankOfCorda.keyPair
        val CHARLIE get() = charlie.party
        val DUMMY_NOTARY get() = dummyNotary.party

        val clock: TestClock = TestClock(Clock.systemUTC())

        @ClassRule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()

        val logger = contextLogger()
    }

    val cordappPackages = listOf(
            "net.corda.finance.contracts",
            CashSchemaV1::class.packageName,
            DummyLinearStateSchemaV1::class.packageName)

    lateinit var liquibaseDB: Database
    lateinit var cordaDB: CordaPersistence
    lateinit var notaryServices: MockServices

    @Before
    fun setUp() {
        val identityService = makeTestIdentityService(dummyNotary.identity, BOB_IDENTITY, ALICE_IDENTITY)
        notaryServices = MockServices(cordappPackages, dummyNotary, identityService, dummyCashIssuer.keyPair, BOC_KEY)
        cordaDB = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), notaryServices.identityService::wellKnownPartyFromX500Name, notaryServices.identityService::wellKnownPartyFromAnonymous)
        val liquibaseConnection = Mockito.mock(JdbcConnection::class.java)
        Mockito.`when`(liquibaseConnection.url).thenReturn(cordaDB.jdbcUrl)
        Mockito.`when`(liquibaseConnection.wrappedConnection).thenReturn(cordaDB.dataSource.connection)
        liquibaseDB = Mockito.mock(Database::class.java)
        Mockito.`when`(liquibaseDB.connection).thenReturn(liquibaseConnection)

        saveOurIdentities(listOf(bob.keyPair))
        saveAllIdentities(listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity))
    }

    @After
    fun close() {
        cordaDB.close()
    }

    private fun createCashTransaction(cash: Cash, value: Amount<Currency>, owner: AbstractParty): SignedTransaction {
        val tx = TransactionBuilder(DUMMY_NOTARY)
        cash.generateIssue(tx, Amount(value.quantity, Issued(bankOfCorda.ref(1), value.token)), owner, DUMMY_NOTARY)
        return notaryServices.signInitialTransaction(tx, bankOfCorda.party.owningKey)
    }

    private fun createVaultStatesFromTransaction(tx: SignedTransaction) {
        cordaDB.transaction {
            tx.coreTransaction.outputs.forEachIndexed { index, state ->
                val constraintInfo = Vault.ConstraintInfo(state.constraint)
                val persistentState = VaultSchemaV1.VaultStates(
                        notary = state.notary,
                        contractStateClassName = state.data.javaClass.name,
                        stateStatus = Vault.StateStatus.UNCONSUMED,
                        recordedTime = clock.instant(),
                        relevancyStatus = Vault.RelevancyStatus.RELEVANT, //Always persist as relevant to mimic V3
                        constraintType = constraintInfo.type(),
                        constraintData = constraintInfo.data()
                )
                persistentState.stateRef = PersistentStateRef(tx.id.toString(), index)
                session.save(persistentState)
            }
        }
    }

    private fun saveOurIdentities(identities: List<KeyPair>) {
        cordaDB.transaction {
            identities.forEach {
                val persistentKey = BasicHSMKeyManagementService.PersistentKey(it.public, it.private)
                session.save(persistentKey)
            }
        }
    }

    private fun saveAllIdentities(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            identities.forEach {
                val persistentID = PersistentIdentityService.PersistentIdentity(it.owningKey.hash.toString(), it.certPath.encoded)
                val persistentName = PersistentIdentityService.PersistentIdentityNames(it.name.toString(), it.owningKey.hash.toString())
                session.save(persistentID)
                session.save(persistentName)
            }
        }
    }

    private fun storeTransaction(tx: SignedTransaction) {
        cordaDB.transaction {
            val persistentTx = DBTransactionStorage.DBTransaction(
                    txId = tx.id.toString(),
                    stateMachineRunId = null,
                    transaction = tx.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
            )
            session.save(persistentTx)
        }
    }

    private fun getVaultStateCount(relevancyStatus: Vault.RelevancyStatus = Vault.RelevancyStatus.ALL): Long {
        return cordaDB.transaction {
            val criteriaBuilder = cordaDB.entityManagerFactory.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRootStates))
            if (relevancyStatus != Vault.RelevancyStatus.ALL) {
                criteriaQuery.where(criteriaBuilder.equal(queryRootStates.get<Vault.RelevancyStatus>("relevancyStatus"), relevancyStatus))
            }
            val query = session.createQuery(criteriaQuery)
            query.singleResult
        }
    }

    private fun getStatePartyCount(): Long {
        return cordaDB.transaction {
            val criteriaBuilder = cordaDB.entityManagerFactory.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.PersistentParty::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRootStates))
            val query = session.createQuery(criteriaQuery)
            query.singleResult
        }
    }

    private fun addCashStates(statesToAdd: Int, owner: AbstractParty) {
        val cash = Cash()
        cordaDB.transaction {
            (1..statesToAdd).map { createCashTransaction(cash, it.DOLLARS, owner) }.forEach {
                storeTransaction(it)
                createVaultStatesFromTransaction(it)
            }
        }
    }

    private fun createLinearStateTransaction(idString: String,
                                             parties: List<AbstractParty> = listOf(),
                                             linearString: String = "foo",
                                             linearNumber: Long = 0L,
                                             linearBoolean: Boolean = false): SignedTransaction {
        val tx = TransactionBuilder(notary = dummyNotary.party).apply {
            addOutputState(DummyLinearContract.State(
                    linearId = UniqueIdentifier(idString),
                    participants = parties,
                    linearString = linearString,
                    linearNumber = linearNumber,
                    linearBoolean = linearBoolean,
                    linearTimestamp = clock.instant()), DUMMY_LINEAR_CONTRACT_PROGRAM_ID
            )
            addCommand(dummyCommand())
        }
        return notaryServices.signInitialTransaction(tx)
    }

    private fun addLinearStates(statesToAdd: Int, parties: List<AbstractParty>) {
        cordaDB.transaction {
            (1..statesToAdd).map { createLinearStateTransaction("A".repeat(it), parties)}.forEach {
                storeTransaction(it)
                createVaultStatesFromTransaction(it)
            }
        }
    }

    @Test
    fun `Check a simple migration works`() {
        addCashStates(10, BOB)
        addCashStates(10, ALICE)
        assertEquals(20, getVaultStateCount())
        assertEquals(0, getStatePartyCount())
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(20, getVaultStateCount())
        assertEquals(20, getStatePartyCount())
        assertEquals(10, getVaultStateCount(Vault.RelevancyStatus.RELEVANT))
    }

    @Test
    fun `Check state paging works`() {
        addCashStates(300, BOB)

        assertEquals(0, getStatePartyCount())
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(300, getStatePartyCount())
        assertEquals(300, getVaultStateCount())
        assertEquals(0, getVaultStateCount(Vault.RelevancyStatus.NOT_RELEVANT))
    }

    @Test
    fun `Check state fields are correct`() {
        fun <T> getState(clazz: Class<T>): T {
            return cordaDB.transaction {
                val criteriaBuilder = cordaDB.entityManagerFactory.criteriaBuilder
                val criteriaQuery = criteriaBuilder.createQuery(clazz)
                val queryRootStates = criteriaQuery.from(clazz)
                criteriaQuery.select(queryRootStates)
                val query = session.createQuery(criteriaQuery)
                query.singleResult
            }
        }
        val tx = createCashTransaction(Cash(), 100.DOLLARS, ALICE)
        storeTransaction(tx)
        createVaultStatesFromTransaction(tx)
        val expectedPersistentParty = VaultSchemaV1.PersistentParty(
                PersistentStateRef(tx.id.toString(), 0),
                ALICE
        )
        val state = tx.coreTransaction.outputs.first()
        val constraintInfo = Vault.ConstraintInfo(state.constraint)
        val expectedPersistentState = VaultSchemaV1.VaultStates(
                notary = state.notary,
                contractStateClassName = state.data.javaClass.name,
                stateStatus = Vault.StateStatus.UNCONSUMED,
                recordedTime = clock.instant(),
                relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT,
                constraintType = constraintInfo.type(),
                constraintData = constraintInfo.data()
        )

        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        val persistentStateParty = getState(VaultSchemaV1.PersistentParty::class.java)
        val persistentState = getState(VaultSchemaV1.VaultStates::class.java)
        assertEquals(expectedPersistentState.notary, persistentState.notary)
        assertEquals(expectedPersistentState.stateStatus, persistentState.stateStatus)
        assertEquals(expectedPersistentState.relevancyStatus, persistentState.relevancyStatus)
        assertEquals(expectedPersistentParty.x500Name, persistentStateParty.x500Name)
        assertEquals(expectedPersistentParty.compositeKey, persistentStateParty.compositeKey)
    }

    @Test
    fun `Check the connection is open post migration`() {
        // Liquibase automatically closes the database connection when doing an actual migration. This test ensures the custom migration
        // leaves it open.
        addCashStates(12, ALICE)

        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertFalse(cordaDB.dataSource.connection.isClosed)
    }

    @Test
    fun `All parties added to state party table`() {
        val stx = createLinearStateTransaction("test", parties = listOf(ALICE, BOB, CHARLIE))
        storeTransaction(stx)
        createVaultStatesFromTransaction(stx)

        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(3, getStatePartyCount())
        assertEquals(1, getVaultStateCount())
        assertEquals(0, getVaultStateCount(Vault.RelevancyStatus.NOT_RELEVANT))
    }

    @Test
    fun `State with corresponding transaction missing is skipped`() {
        val cash = Cash()
        val unknownTx = createCashTransaction(cash, 100.DOLLARS, BOB)
        createVaultStatesFromTransaction(unknownTx)

        addCashStates(10, BOB)
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(10, getStatePartyCount())
    }

    @Test
    fun `State with unknown ID is handled correctly`() {
        addCashStates(1, CHARLIE)
        addCashStates(10, BOB)
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(11, getStatePartyCount())
        assertEquals(1, getVaultStateCount(Vault.RelevancyStatus.NOT_RELEVANT))
        assertEquals(10, getVaultStateCount(Vault.RelevancyStatus.RELEVANT))
    }

    @Test
    fun `Null database causes migration to be ignored`() {
        val migration = VaultStateMigration()
        // Just check this does not throw an exception
        migration.execute(null)
    }

    // Used to test migration performance
    @Test
    @Ignore
    fun `Migrate large database`() {
        val statesAtOnce = 500L
        val stateMultiplier = 300L
        logger.info("Start adding states to vault")
        (1..stateMultiplier).forEach {
            addCashStates(statesAtOnce.toInt(), BOB)
        }
        logger.info("Finish adding states to vault")
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals((statesAtOnce * stateMultiplier), getStatePartyCount())
    }

    private fun makePersistentDataSourceProperties(): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:~/test/vault_query_persistence;DB_CLOSE_ON_EXIT=TRUE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }

    // Used to generate a persistent database for further testing.
    @Test
    @Ignore
    fun `Create persistent DB`() {
        val cashStatesToAdd = 100
        val linearStatesToAdd = 100
        val stateMultiplier = 1000

        cordaDB = configureDatabase(makePersistentDataSourceProperties(), DatabaseConfig(), notaryServices.identityService::wellKnownPartyFromX500Name, notaryServices.identityService::wellKnownPartyFromAnonymous)

        for (i in 1..stateMultiplier) {
            addCashStates(cashStatesToAdd, BOB)
            addLinearStates(linearStatesToAdd, listOf(BOB, ALICE))
        }
        saveOurIdentities(listOf(bob.keyPair))
        saveAllIdentities(listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity))
        cordaDB.close()
    }

    @Test
    @Ignore
    fun `Run on persistent DB`() {
        cordaDB = configureDatabase(makePersistentDataSourceProperties(), DatabaseConfig(), notaryServices.identityService::wellKnownPartyFromX500Name, notaryServices.identityService::wellKnownPartyFromAnonymous)
        val connection = (liquibaseDB.connection as JdbcConnection)
        Mockito.`when`(connection.url).thenReturn(cordaDB.jdbcUrl)
        Mockito.`when`(connection.wrappedConnection).thenReturn(cordaDB.dataSource.connection)
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        cordaDB.close()
    }
}

