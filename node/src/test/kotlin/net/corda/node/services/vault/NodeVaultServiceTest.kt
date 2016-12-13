package net.corda.node.services.vault

import net.corda.contracts.asset.Cash
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.composite
import net.corda.core.flows.FlowException
import net.corda.core.node.services.TxWritableStorageService
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeVaultServiceTest {
    lateinit var services: MockServices
    val vault: VaultService get() = services.vaultService
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
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
        LogHelper.reset(NodeVaultService::class)
    }

    @Test
    fun `states not local to instance`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = services.vaultService.unconsumedStates<Cash.State>()
            assertThat(w1).hasSize(3)

            val originalStorage = services.storageService
            val originalVault = services.vaultService
            val services2 = object : MockServices() {
                override val vaultService: VaultService get() = originalVault

                // We need to be able to find the same transactions as before, too.
                override val storageService: TxWritableStorageService get() = originalStorage

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        storageService.validatedTransactions.addTransaction(stx)
                        vaultService.notify(stx.tx)
                    }
                }
            }

            val w2 = services2.vaultService.unconsumedStates<Cash.State>()
            assertThat(w2).hasSize(3)
        }
    }

    @Test
    fun `states for refs`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = services.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(w1).hasSize(3)

            val stateRefs = listOf(w1[1].ref, w1[2].ref)
            val states = services.vaultService.statesForRefs(stateRefs)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `states soft locking reserve and release`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val unconsumedStates = services.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(3)

            val stateRefsToSoftLock = setOf(unconsumedStates[1].ref, unconsumedStates[2].ref)

            // soft lock two of the three states
            val softLockId = UUID.randomUUID()
            services.vaultService.softLockReserve(softLockId, stateRefsToSoftLock)

            // all softlocked states
            assertThat(services.vaultService.softLockedStates<Cash.State>()).hasSize(2)
            // my softlocked states
            assertThat(services.vaultService.softLockedStates<Cash.State>(softLockId)).hasSize(2)

            // excluding softlocked states
            val unlockedStates1 = services.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false)
            assertThat(unlockedStates1).hasSize(1)

            // soft lock release one of the states explicitly
            services.vaultService.softLockRelease(softLockId, setOf(unconsumedStates[1].ref))
            val unlockedStates2 = services.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false)
            assertThat(unlockedStates2).hasSize(2)

            // soft lock release the rest by id
            services.vaultService.softLockRelease(softLockId)
            val unlockedStates = services.vaultService.unconsumedStates<Cash.State>(includeSoftLockedStates = false).toList()
            assertThat(unlockedStates).hasSize(3)

            // should be back to original states
            assertThat(unlockedStates).isEqualTo(unconsumedStates)
        }
    }

    @Test
    fun `soft locking attempt concurrent reserve`() {

        val backgroundExecutor = Executors.newFixedThreadPool(2)
        val countDown = CountDownLatch(2)

        val softLockId1 = UUID.randomUUID()
        val softLockId2 = UUID.randomUUID()

        val vaultStates =
                databaseTransaction(database) {
                    assertNull(vault.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // 1st tx locks states
        backgroundExecutor.submit {
            try {
                databaseTransaction(database) {
                    vault.softLockReserve(softLockId1, stateRefsToSoftLock)
                    assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
                }
                println("SOFT LOCK STATES #1 succeeded")
            } catch(e: Throwable) {
                println("SOFT LOCK STATES #1 failed")
            } finally {
                countDown.countDown()
            }
        }

        // 2nd tx attempts to lock same states
        backgroundExecutor.submit {
            try {
                Thread.sleep(100)   // let 1st thread soft lock them 1st
                databaseTransaction(database) {
                    vault.softLockReserve(softLockId2, stateRefsToSoftLock)
                    assertThat(vault.softLockedStates<Cash.State>(softLockId2)).hasSize(3)
                }
                println("SOFT LOCK STATES #2 succeeded")
            } catch(e: Throwable) {
                println("SOFT LOCK STATES #2 failed")
            } finally {
                countDown.countDown()
            }
        }

        countDown.await()
        databaseTransaction(database) {
            val lockStatesId1 = vault.softLockedStates<Cash.State>(softLockId1)
            println("SOFT LOCK #1 final states: $lockStatesId1")
            assertThat(lockStatesId1.size).isIn(0, 3)
            val lockStatesId2 = vault.softLockedStates<Cash.State>(softLockId2)
            println("SOFT LOCK #2 final states: $lockStatesId2")
            assertThat(lockStatesId2.size).isIn(0, 3)
        }
    }

    @Test
    fun `soft locking partial reserve states fails`() {

        val softLockId1 = UUID.randomUUID()
        val softLockId2 = UUID.randomUUID()

        val vaultStates =
                databaseTransaction(database) {
                    assertNull(vault.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock 1st state with LockId1
        databaseTransaction(database) {
            vault.softLockReserve(softLockId1, setOf(stateRefsToSoftLock.first()))
            assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(1)
        }

        // attempt to lock all 3 states with LockId2
        databaseTransaction(database) {
            assertThatExceptionOfType(FlowException::class.java).isThrownBy(
                    { vault.softLockReserve(softLockId2, stateRefsToSoftLock) }
            ).withMessageContaining("only 2 rows available").withNoCause()
        }
    }

    @Test
    fun `attempt to lock states already soft locked by me`() {

        val softLockId1 = UUID.randomUUID()

        val vaultStates =
                databaseTransaction(database) {
                    assertNull(vault.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        databaseTransaction(database) {
            vault.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }

        // attempt to relock same states with LockId1
        databaseTransaction(database) {
            vault.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }
    }

    @Test
    fun `lock additional states to some already soft locked by me`() {

        val softLockId1 = UUID.randomUUID()

        val vaultStates =
                databaseTransaction(database) {
                    assertNull(vault.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        databaseTransaction(database) {
            vault.softLockReserve(softLockId1, setOf(stateRefsToSoftLock.first()))
            assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(1)
        }

        // attempt to lock all states with LockId1 (including previously already locked one)
        databaseTransaction(database) {
            vault.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vault.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }
    }

    @Test
    fun `unconsumedStatesForSpending exact amount`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val unconsumedStates = services.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = (services.vaultService as NodeVaultService).unconsumedStatesForSpending<Cash.State>(100.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isEqualTo(100L*100)
            assertThat(services.vaultService.softLockedStates<Cash.State>()).hasSize(1)
        }
    }

    @Test
    fun `unconsumedStatesForSpending insufficient amount`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val unconsumedStates = services.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = (services.vaultService as NodeVaultService).unconsumedStatesForSpending<Cash.State>(110.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(services.vaultService.softLockedStates<Cash.State>()).hasSize(0)
        }
    }

    @Test
    fun `unconsumedStatesForSpending small amount`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))

            val unconsumedStates = services.vaultService.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(2)

            val spendableStatesUSD = (services.vaultService as NodeVaultService).unconsumedStatesForSpending<Cash.State>(1.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isGreaterThanOrEqualTo(1L*100)
            assertThat(services.vaultService.softLockedStates<Cash.State>()).hasSize(1)
        }
    }

    @Test
    fun `states soft locking query granularity`() {
        databaseTransaction(database) {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 10, 10, Random(0L))

            val allStates = services.vaultService.unconsumedStates<Cash.State>()
            assertThat(allStates).hasSize(30)

            for (i in 1..5) {
                val spendableStatesUSD = (services.vaultService as NodeVaultService).unconsumedStatesForSpending<Cash.State>(20.DOLLARS, lockId = UUID.randomUUID())
                spendableStatesUSD.forEach(::println)
            }
            // note only 3 spend attempts succeed with a total of 8 states
            assertThat(services.vaultService.softLockedStates<Cash.State>()).hasSize(8)
        }
    }

    @Test
    fun addNoteToTransaction() {
        databaseTransaction(database) {

            val freshKey = services.legalIdentityKey

            // Issue a txn to Send us some Money
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(usefulTX))

            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 1")
            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 2")
            services.vaultService.addNoteToTransaction(usefulTX.id, "USD Sample Note 3")
            assertEquals(3, services.vaultService.getTransactionNotes(usefulTX.id).count())

            // Issue more Money (GBP)
            val anotherTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 200.POUNDS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(anotherTX))

            services.vaultService.addNoteToTransaction(anotherTX.id, "GPB Sample Note 1")
            assertEquals(1, services.vaultService.getTransactionNotes(anotherTX.id).count())
        }
    }
}
