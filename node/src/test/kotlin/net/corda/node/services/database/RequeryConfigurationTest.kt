package net.corda.node.services.database

import io.requery.Persistable
import io.requery.kotlin.eq
import io.requery.sql.KotlinEntityDataStore
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.testing.NullPublicKey
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.Vault
import net.corda.core.serialization.serialize
import net.corda.core.serialization.storageKryo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_PUBKEY_1
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.vault.schemas.requery.Models
import net.corda.node.services.vault.schemas.requery.VaultCashBalancesEntity
import net.corda.node.services.vault.schemas.requery.VaultSchema
import net.corda.node.services.vault.schemas.requery.VaultStatesEntity
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*

class RequeryConfigurationTest {

    lateinit var database: CordaPersistence
    lateinit var transactionStorage: DBTransactionStorage
    lateinit var requerySession: KotlinEntityDataStore<Persistable>

    @Before
    fun setUp() {
        val dataSourceProperties = makeTestDataSourceProperties()
        database = configureDatabase(dataSourceProperties)
        newTransactionStorage()
        newRequeryStorage(dataSourceProperties)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun `transaction inserts in same DB transaction scope across two persistence engines`() {
        val txn = newTransaction()

        database.transaction {
            transactionStorage.addTransaction(txn)
            requerySession.withTransaction {
                insert(createVaultStateEntity(txn))
            }
        }

        database.transaction {
            Assertions.assertThat(transactionStorage.transactions).containsOnly(txn)
            requerySession.withTransaction {
                val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq txn.tx.inputs[0].txhash.toString())
                Assertions.assertThat(result.get().first().txId).isEqualTo(txn.tx.inputs[0].txhash.toString())
            }
        }
    }

    @Test
    fun `transaction operations in same DB transaction scope across two persistence engines`() {
        val txn = newTransaction()

        database.transaction {
            transactionStorage.addTransaction(txn)
            requerySession.withTransaction {
                upsert(createCashBalance())
                select(VaultSchema.VaultCashBalances::class).get().first()
                insert(createVaultStateEntity(txn))
            }
        }

        database.transaction {
            Assertions.assertThat(transactionStorage.transactions).containsOnly(txn)
            requerySession.withTransaction {
                val cashQuery = select(VaultSchema.VaultCashBalances::class) where (VaultSchema.VaultCashBalances::currency eq "GBP")
                assertEquals(12345, cashQuery.get().first().amount)
                val stateQuery = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq txn.tx.inputs[0].txhash.toString())
                Assertions.assertThat(stateQuery.get().first().txId).isEqualTo(txn.tx.inputs[0].txhash.toString())
            }
        }
    }

    @Test
    fun `transaction rollback in same DB transaction scope across two persistence engines`() {
        val txn = newTransaction()

        database.transaction {
            transactionStorage.addTransaction(txn)
            requerySession.withTransaction {
                insert(createVaultStateEntity(txn))
            }
            rollback()
        }

        database.transaction {
            Assertions.assertThat(transactionStorage.transactions).isEmpty()
            requerySession.withTransaction {
                val result = select(VaultSchema.VaultStates::class) where (VaultSchema.VaultStates::txId eq txn.tx.inputs[0].txhash.toString())
                Assertions.assertThat(result.get().count() == 0)
            }
        }
    }

    @Test
    fun `bounded iteration`() {
        // insert 100 entities
        database.transaction {
            requerySession.withTransaction {
                (1..100)
                        .map { newTransaction(it) }
                        .forEach { insert(createVaultStateEntity(it)) }
            }
        }

        // query entities 41..45
        database.transaction {
            requerySession.withTransaction {
                // Note: cannot specify a limit explicitly when using iterator skip & take
                val query = select(VaultSchema.VaultStates::class)
                val count = query.get().count()
                Assertions.assertThat(count).isEqualTo(100)
                val result = query.get().iterator(40, 5)
                Assertions.assertThat(result.asSequence().count()).isEqualTo(5)
            }
        }
    }

    @Test
    fun `test calling an arbitrary JDBC native query`() {
        val txn = newTransaction()

        database.transaction {
            transactionStorage.addTransaction(txn)
            requerySession.withTransaction {
                insert(createVaultStateEntity(txn))
            }
        }

        val dataSourceProperties = makeTestDataSourceProperties()
        val nativeQuery = "SELECT v.transaction_id, v.output_index FROM vault_states v WHERE v.state_status = 0"

        database.transaction {
            val configuration = RequeryConfiguration(dataSourceProperties, true)
            val jdbcSession = configuration.jdbcSession()
            val prepStatement = jdbcSession.prepareStatement(nativeQuery)
            val rs = prepStatement.executeQuery()
            assertTrue(rs.next())
            assertEquals(rs.getString(1), txn.tx.inputs[0].txhash.toString())
            assertEquals(rs.getInt(2), txn.tx.inputs[0].index)
        }
    }

    private fun createVaultStateEntity(txn: SignedTransaction): VaultStatesEntity {
        val txnState = txn.tx.inputs[0]
        val state = VaultStatesEntity().apply {
            txId = txnState.txhash.toString()
            index = txnState.index
            stateStatus = Vault.StateStatus.UNCONSUMED
            contractStateClassName = DummyContract.SingleOwnerState::class.java.name
            contractState = DummyContract.SingleOwnerState(owner = AnonymousParty(DUMMY_PUBKEY_1)).serialize(storageKryo()).bytes
            notaryName = txn.tx.notary!!.name.toString()
            notaryKey = txn.tx.notary!!.owningKey.toBase58String()
            recordedTime = Instant.now()
        }
        return state
    }

    private fun createCashBalance(): VaultCashBalancesEntity {
        val cashBalanceEntity = VaultCashBalancesEntity()
        cashBalanceEntity.currency = "GBP"
        cashBalanceEntity.amount = 12345
        return cashBalanceEntity
    }

    private fun newTransactionStorage() {
        database.transaction {
            transactionStorage = DBTransactionStorage()
        }
    }

    private fun newRequeryStorage(dataSourceProperties: Properties) {
        database.transaction {
            val configuration = RequeryConfiguration(dataSourceProperties, true)
            requerySession = configuration.sessionForModel(Models.VAULT)
        }
    }

    private fun newTransaction(index: Int = 0): SignedTransaction {
        val wtx = WireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), index)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = emptyList(),
                notary = DUMMY_NOTARY,
                signers = emptyList(),
                type = TransactionType.General,
                timeWindow = null
        )
        return SignedTransaction(wtx.serialized, listOf(DigitalSignature.WithKey(NullPublicKey, ByteArray(1))))
    }
}