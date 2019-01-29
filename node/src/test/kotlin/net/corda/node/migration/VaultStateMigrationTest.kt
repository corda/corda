package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.contracts.Amount
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
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
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
        val bob = TestIdentity(BOB_NAME, 80)
        val cashNotary = TestIdentity(CordaX500Name("Cash Notary Service", "Zurich", "CH"), 21)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val ALICE get() = alice.party
        val ALICE_IDENTITY get() = alice.identity
        val BOB get() = bob.party
        val BOB_IDENTITY get() = bob.identity
        val BOC_IDENTITY get() = bankOfCorda.identity
        val BOC_KEY get() = bankOfCorda.keyPair
        val CASH_NOTARY_IDENTITY get() = cashNotary.identity
        val DUMMY_NOTARY get() = dummyNotary.party
        val MEGA_CORP_IDENTITY get() = megaCorp.identity
        val MEGA_CORP_KEY get() = megaCorp.keyPair
        val MINI_CORP_IDENTITY get() = miniCorp.identity

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
                        relevancyStatus = Vault.RelevancyStatus.RELEVANT,
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
        (1..statesToAdd).map { createCashTransaction(cash, it.DOLLARS, owner) }.forEach {
            storeTransaction(it)
            createVaultStatesFromTransaction(it)
        }
    }

    @Test
    fun `check a simple migration works`() {
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
    fun `check state paging works`() {
        addCashStates(300, BOB)

        assertEquals(0, getStatePartyCount())
        val migration = VaultStateMigration()
        migration.execute(liquibaseDB)
        assertEquals(300, getStatePartyCount())
        assertEquals(300, getVaultStateCount())
        assertEquals(0, getVaultStateCount(Vault.RelevancyStatus.NOT_RELEVANT))
    }
}

