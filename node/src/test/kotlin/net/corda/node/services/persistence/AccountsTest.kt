package net.corda.node.services.persistence

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.toStringShort
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Serializable
import java.security.PublicKey
import java.util.*
import javax.persistence.*
import kotlin.test.assertEquals

object AccountsSchema

@CordaSerializable
object AccountsSchemaV1 : MappedSchema(
        schemaFamily = AccountsSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentAccount::class.java, PersistentKeyToAccountMap::class.java)
) {

    @Entity
    @Table(name = "accounts")
    class PersistentAccount(
            @Id
            @Column(name = "account_id", nullable = false)
            var accountId: String,

            @Column(name = "account_name", nullable = false)
            var name: String
    ) : Serializable

    @Entity
    @Table(name = "accounts_to_public_key")
    class PersistentKeyToAccountMap(
            @Id
            @GeneratedValue(strategy = GenerationType.AUTO)
            var id: Int?,

            @Column(name = "account_id", nullable = false)
            var accountId: String,

            @Column(name = "public_key", nullable = false)
            var publicKey: PublicKey
    ) {
        constructor(accountId: UUID, publicKey: PublicKey) : this(null, accountId.toString(), publicKey)
    }

}
// TODO: cannot use composite keys with accounts for the reason that kostas stated.

/**
 * Represents an account and the stuff you can do with accounts.
 */
@CordaSerializable
data class Account(val name: String, val accountId: UniqueIdentifier = UniqueIdentifier()) {

    /**
     * Create a fresh key and assign it to the specified account.
     */
    fun freshKey(services: ServiceHub): PublicKey {
        val publicKey = services.keyManagementService.freshKey()
        // TODO: Need to make sure the account actually exists.
        services.withEntityManager {
            persist(AccountsSchemaV1.PersistentKeyToAccountMap(accountId.id, publicKey))
            flush()
        }
        return publicKey
    }

}

fun Account.toPersistentAccount(): AccountsSchemaV1.PersistentAccount {
    return AccountsSchemaV1.PersistentAccount(accountId.id.toString(), name)
}

/**
 * Accounts service.
 *
 * Composite keys cannot be used with the accounts maangement service. They can be deserialised but if there is one
 * composite key which has two leafs, where each leaf represnts one account then this model cannot deal with that
 * scenario, so it must be noted that it is out of scope for the time being.
 */

/**
 * Example implementation of an accounts service
 */
@CordaService
class AccountsService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    /** Returns all the public keys which have been assigned to the supplied accountId. */
    fun keysForAccount(accountId: UniqueIdentifier): List<PublicKey> {
        return services.withEntityManager {
            val builder = criteriaBuilder
            val criteriaQuery = builder.createQuery(AccountsSchemaV1.PersistentKeyToAccountMap::class.java).apply {
                val root = from(AccountsSchemaV1.PersistentKeyToAccountMap::class.java)
                select(root)
                where(builder.equal(root.get<String>("accountId"), accountId.id.toString()))
            }
            createQuery(criteriaQuery).resultList.map { it.publicKey }
        }
    }

    // TODO: Figure out how to query for states.
    // See deriveContractStateTypes
    // See that in the vualt states table that the class name is stored as a string.
//    fun <T : ContractState>statesForAccount(accountId: UniqueIdentifier, clazz: Class<T>): List<StateAndRef> {
//
//    }

    /** Checks whether the supplied account exists, or not. */
    fun accountExists(accountId: UniqueIdentifier): Boolean {
        return services.withEntityManager {
            find(AccountsSchemaV1.PersistentAccount::class.java, accountId.id.toString()) != null
        }
    }

    /**
     * Adds a new account.
     * TODO: Change this to take an Account as parameter.
     */
    fun addAccount(name: String): Account {
        val account = Account(name)
        services.withEntityManager {
            persist(account.toPersistentAccount())
            flush()
        }
        return account
    }

}

val ServiceHub.accountsService get() = cordaService(AccountsService::class.java)

class AccountsTest {

    val cordapps = listOf("net.corda.node.services.persistence")
    lateinit var mockNetwork: MockNetwork
    lateinit var mockNode: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(cordapps)
        mockNode = mockNetwork.createNode()
    }

    @After
    fun shutdown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun `add account and check it exists`() {
        mockNode.transaction {
            val accountsService = mockNode.services.accountsService
            val rogersAccount = accountsService.addAccount("Roger")
            val exists = accountsService.accountExists(rogersAccount.accountId)
            assertEquals(true, exists)
        }
    }

    @Test
    fun `add account and assign new public keys to account`() {
        val accountsService = mockNode.services.accountsService

        // Create account.
        val account = mockNode.transaction {
            accountsService.addAccount("Roger")
        }

        // Create key for account.
        val key = mockNode.transaction {
            account.freshKey(mockNode.services)
        }

        // Get keys for account.
        val result = mockNode.transaction {
            accountsService.keysForAccount(account.accountId).single()
        }
        println("${result.toStringShort()}=${key.toStringShort()}")
        assertEquals(key, result)
    }

}