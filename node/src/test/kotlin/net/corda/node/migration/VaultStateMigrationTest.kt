package net.corda.node.migration

import com.nhaarman.mockito_kotlin.mock
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.Issued
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
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.test.SampleCashSchemaV3
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.VaultQueryTestsBase
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import net.corda.testing.node.makeTestIdentityService
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.Mockito
import java.security.KeyPair
import java.time.Clock
import java.util.*
import kotlin.test.assertEquals

class VaultStateMigrationTest {

    companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val bigCorp = TestIdentity(CordaX500Name("BigCorporation", "New York", "US"))
        val bob = TestIdentity(BOB_NAME, 80)
        val cashNotary = TestIdentity(CordaX500Name("Cash Notary Service", "Zurich", "CH"), 21)
        val charlie = TestIdentity(CHARLIE_NAME, 90)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val DUMMY_CASH_ISSUER = dummyCashIssuer.ref(1)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val DUMMY_OBLIGATION_ISSUER = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val ALICE get() = alice.party
        val ALICE_IDENTITY get() = alice.identity
        val BIG_CORP get() = bigCorp.party
        val BIG_CORP_IDENTITY get() = bigCorp.identity
        val BOB get() = bob.party
        val BOB_IDENTITY get() = bob.identity
        val BOC get() = bankOfCorda.party
        val BOC_IDENTITY get() = bankOfCorda.identity
        val BOC_KEY get() = bankOfCorda.keyPair
        val BOC_PUBKEY get() = bankOfCorda.publicKey
        val CASH_NOTARY get() = cashNotary.party
        val CASH_NOTARY_IDENTITY get() = cashNotary.identity
        val CHARLIE get() = charlie.party
        val CHARLIE_IDENTITY get() = charlie.identity
        val DUMMY_NOTARY get() = dummyNotary.party
        val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair
        val MEGA_CORP_IDENTITY get() = megaCorp.identity
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
        val MEGA_CORP_KEY get() = megaCorp.keyPair
        val MEGA_CORP get() = megaCorp.party
        val MINI_CORP_IDENTITY get() = miniCorp.identity
        val MINI_CORP get() = miniCorp.party

        val clock: TestClock = TestClock(Clock.systemUTC())

        @ClassRule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()
    }

    val cordappPackages = listOf(
            "net.corda.finance.contracts",
            CashSchemaV1::class.packageName)

    lateinit var liquibaseDB: Database
    lateinit var cordaDB: CordaPersistence
    lateinit var notaryServices: MockServices

    @Before
    fun setUp() {
        val identityService = makeTestIdentityService(BOC_IDENTITY, CASH_NOTARY_IDENTITY, MINI_CORP_IDENTITY, MEGA_CORP_IDENTITY)
        notaryServices = MockServices(cordappPackages, dummyNotary, identityService, dummyCashIssuer.keyPair, BOC_KEY, MEGA_CORP_KEY)
        (notaryServices.myInfo.legalIdentitiesAndCerts + BOC_IDENTITY + CASH_NOTARY_IDENTITY + MINI_CORP_IDENTITY + MEGA_CORP_IDENTITY).forEach { identity ->
            notaryServices.identityService.verifyAndRegisterIdentity(identity)
        }
        cordaDB = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), notaryServices.identityService::wellKnownPartyFromX500Name, notaryServices.identityService::wellKnownPartyFromAnonymous)
        val liquibaseConnection = Mockito.mock(JdbcConnection::class.java)
        Mockito.`when`(liquibaseConnection.url).thenReturn(cordaDB.jdbcUrl)
        Mockito.`when`(liquibaseConnection.wrappedConnection).thenReturn(cordaDB.dataSource.connection)
        liquibaseDB = Mockito.mock(Database::class.java)
        Mockito.`when`(liquibaseDB.connection).thenReturn(liquibaseConnection)


        val cash = Cash()
        (1..101).map { createCashTransaction(cash, it.DOLLARS, BOB) }.forEach {
            storeTransaction(it)
            createVaultStatesFromTransaction(it)
        }

        (1..101).map { createCashTransaction(cash, it.DOLLARS, ALICE) }.forEach {
            storeTransaction(it)
            createVaultStatesFromTransaction(it)
        }

        saveOurIdentities(listOf(bob.keyPair))
        saveAllIdentities(listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity))
    }

    @After
    fun close() {
        cordaDB.close()
    }

    fun createCashTransaction(cash: Cash, value: Amount<Currency>, owner: AbstractParty): SignedTransaction {
        val tx = TransactionBuilder(DUMMY_NOTARY)
        cash.generateIssue(tx, Amount(value.quantity, Issued(bankOfCorda.ref(1), value.token)), owner, DUMMY_NOTARY)
        return notaryServices.signInitialTransaction(tx, bankOfCorda.party.owningKey)
    }

    fun createVaultStatesFromTransaction(tx: SignedTransaction) {
        cordaDB.transaction {
            tx.coreTransaction.outputs.forEachIndexed { index, state ->
                val constraintInfo = Vault.ConstraintInfo(state.constraint)
                val persistentState = VaultSchemaV1.VaultStates(
                        notary = state.notary,
                        contractStateClassName = state.data.javaClass.name,
                        stateStatus = Vault.StateStatus.UNCONSUMED,
                        recordedTime = clock.instant(),
                        relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                        constraintType = constraintInfo.type(),
                        constraintData = constraintInfo.data()
                )
                persistentState.stateRef = PersistentStateRef(tx.id.toString(), index)
                session.save(persistentState)
            }
        }
    }

    fun saveOurIdentities(identities: List<KeyPair>) {
        cordaDB.transaction {
            identities.forEach {
                val persistentKey = BasicHSMKeyManagementService.PersistentKey(it.public, it.private)
                session.save(persistentKey)
            }
        }
    }

    fun saveAllIdentities(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            identities.forEach {
                val persistentID = PersistentIdentityService.PersistentIdentity(it.owningKey.toString(), it.certPath.encoded)
                val persistentName = PersistentIdentityService.PersistentIdentityNames(it.name.toString(), it.owningKey.hash.toString())
                session.save(persistentID)
                session.save(persistentName)
            }
        }
    }

    fun storeTransaction(tx: SignedTransaction) {
        cordaDB.transaction {
            val persistentTx = DBTransactionStorage.DBTransaction(
                    txId = tx.id.toString(),
                    stateMachineRunId = null,
                    transaction = tx.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
            )
            session.save(persistentTx)
        }
    }

    fun getVaultStateCount(): Long {
        //TODO: query by relevancy
        return cordaDB.transaction {
            val criteriaBuilder = cordaDB.entityManagerFactory.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.VaultStates::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRootStates))
            val query = session.createQuery(criteriaQuery)
            query.singleResult
        }
    }

    fun getStatePartyCount(): Long {
        return cordaDB.transaction {
            val criteriaBuilder = cordaDB.entityManagerFactory.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRootStates = criteriaQuery.from(VaultSchemaV1.PersistentParty::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRootStates))
            val query = session.createQuery(criteriaQuery)
            query.singleResult
        }
    }

    @Test
    fun `check a simple migration works`() {
        assertEquals(202, getVaultStateCount())
        assertEquals(0, getStatePartyCount())
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(202, getVaultStateCount())
        assertEquals(202, getStatePartyCount())
    }
}

