package net.corda.node.services.persistence

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.createMockCordaService
import org.junit.Rule
import org.junit.Test
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.test.assertEquals

/** Example implementation of a persistent accounts service. */
@CordaService
class AccountsService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    @CordaSerializable
    @Entity
    @Table(name = "account")
    data class Account(
            @Column(name = "account_name", nullable = false)
            val name: String,

            @Id
            @Column(name = "account_id", nullable = false)
            val accountId: UUID = UUID.randomUUID()
    )

    object AccountsSchema

    @CordaSerializable
    object AccountsSchemaV1 : MappedSchema(
            schemaFamily = AccountsSchema.javaClass,
            version = 1,
            mappedTypes = listOf(Account::class.java)
    )

    /** Returns key hashes for a specified account. Useful for testing. */
    fun keysForAccount(accountId: UUID): List<String> {
        return services.withEntityManager {
            val builder = criteriaBuilder
            val criteriaQuery = builder.createQuery(VaultSchemaV1.PublicKeyHashToExternalIdMapping::class.java).apply {
                val root = from(VaultSchemaV1.PublicKeyHashToExternalIdMapping::class.java)
                select(root)
                where(builder.equal(root.get<UUID>("externalId"), accountId))
            }
            createQuery(criteriaQuery).resultList.map { it.publicKeyHash }
        }
    }

    /** Checks whether the supplied account exists, or not. */
    fun accountExists(accountId: UUID): Boolean {
        return services.withEntityManager {
            find(Account::class.java, accountId) != null
        }
    }

    /** Adds a new account. */
    fun addAccount(name: String): Account {
        val account = Account(name)
        services.withEntityManager {
            persist(account)
            flush()
        }
        return account
    }

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. It also maps the key to the supplied
     * external ID. It returns the public part of the [KeyPair]. It is assumed that the external ID has previously been
     * generated.
     */
    fun freshKeyForAccount(accountId: UUID): PublicKey {
        val publicKey = services.keyManagementService.freshKey()
        services.withEntityManager {
            val mapping = VaultSchemaV1.PublicKeyHashToExternalIdMapping(accountId, publicKey)
            persist(mapping)
            flush()
        }
        return publicKey
    }

}

class AccountsTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val cordapps = listOf(
            "net.corda.node.services.persistence",
            "net.corda.testing.contracts",
            "net.corda.finance.contracts.asset"
    )

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(CordaX500Name("NotaryService", "London", "GB"), 1337L)
    private val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = cordapps,
            identityService = rigorousMock<IdentityServiceInternal>().also {
                doReturn(notary.party).whenever(it).partyFromKey(notary.publicKey)
                doReturn(notary.party).whenever(it).wellKnownPartyFromAnonymous(notary.party)
                doReturn(notary.party).whenever(it).wellKnownPartyFromX500Name(notary.name)
            },
            initialIdentity = myself,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    private val services: MockServices = databaseAndServices.second
    // Having to do this is not obvious at all. Very annoying.
    private val accountsService = createMockCordaService(services, ::AccountsService)
    private val database: CordaPersistence = databaseAndServices.first

    @Test
    fun `add account and check it exists`() {
        database.transaction {
            val rogersAccount = accountsService.addAccount("Roger")
            val exists = accountsService.accountExists(rogersAccount.accountId)
            assertEquals(true, exists)
        }
    }

    @Test
    fun `add account and assign new public keys to account`() {
        // Create account.
        val account = database.transaction { accountsService.addAccount("Roger") }

        // Create key for account.
        val key = database.transaction { accountsService.freshKeyForAccount(account.accountId).toStringShort() }

        // Keys for account.
        val result = database.transaction { accountsService.keysForAccount(account.accountId).single() }

        // Check the key was stored correctly.
        // NOTE: Only the hash is stored.
        println("$result=$key")
        assertEquals(key, result)
    }

    @Test
    fun `vault query with accounts`() {
        val me = myself.party
        val vaultService = services.vaultService
        // Create account.
        val account = database.transaction { accountsService.addAccount("Roger") }
        // Create key for account.
        val key = database.transaction { accountsService.freshKeyForAccount(account.accountId) }

        // Create state with a public key assigned to Roger's account.
        val stx = services.signInitialTransaction(TransactionBuilder(notary = notary.party).apply {
            val anonymousMe = AnonymousParty(key)
            addOutputState(DummyState(1, listOf(anonymousMe)), DummyContract.PROGRAM_ID)
            addCommand(DummyContract.Commands.Create(), listOf(me.owningKey))
        })

        // Record the transaction and extract the dummy state.
        database.transaction { services.recordTransactions(stx) }
        val dummyState = stx.tx.outputsOfType<DummyState>().single()

        // This query should return one state.
        val result = database.transaction {
            val externalId = builder { VaultSchemaV1.ExtIdToPubKeyView::externalId.equal(account.accountId) }
            val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(externalId)
            vaultService.queryBy<DummyState>(queryCriteria).states.single().state.data
        }

        println(result)
        assertEquals(dummyState, result)
    }

}