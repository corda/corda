package net.corda.node.services.vault

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.unconsumedStates
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.transaction
import net.corda.testing.BOC
import net.corda.testing.BOC_KEY
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
    val vaultSvc: VaultService get() = services.vaultService
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeVaultService::class)
        val dataSourceProps = makeTestDataSourceProperties()
        val dataSourceAndDatabase = configureDatabase(dataSourceProps)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        database.transaction {
            services = object : MockServices() {
                override val vaultService: VaultService = makeVaultService(dataSourceProps)

                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
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
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = vaultSvc.unconsumedStates<Cash.State>()
            assertThat(w1).hasSize(3)

            val originalVault = vaultSvc
            val services2 = object : MockServices() {
                override val vaultService: VaultService get() = originalVault
                override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                    for (stx in txs) {
                        validatedTransactions.addTransaction(stx)
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
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w1 = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(w1).hasSize(3)

            val stateRefs = listOf(w1[1].ref, w1[2].ref)
            val states = vaultSvc.statesForRefs(stateRefs)
            assertThat(states).hasSize(2)
        }
    }

    @Test
    fun `states soft locking reserve and release`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val unconsumedStates = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(3)

            val stateRefsToSoftLock = setOf(unconsumedStates[1].ref, unconsumedStates[2].ref)

            // soft lock two of the three states
            val softLockId = UUID.randomUUID()
            vaultSvc.softLockReserve(softLockId, stateRefsToSoftLock)

            // all softlocked states
            assertThat(vaultSvc.softLockedStates<Cash.State>()).hasSize(2)
            // my softlocked states
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId)).hasSize(2)

            // excluding softlocked states
            val unlockedStates1 = vaultSvc.unconsumedStates<Cash.State>(includeSoftLockedStates = false).toList()
            assertThat(unlockedStates1).hasSize(1)

            // soft lock release one of the states explicitly
            vaultSvc.softLockRelease(softLockId, setOf(unconsumedStates[1].ref))
            val unlockedStates2 = vaultSvc.unconsumedStates<Cash.State>(includeSoftLockedStates = false).toList()
            assertThat(unlockedStates2).hasSize(2)

            // soft lock release the rest by id
            vaultSvc.softLockRelease(softLockId)
            val unlockedStates = vaultSvc.unconsumedStates<Cash.State>(includeSoftLockedStates = false).toList()
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
                database.transaction {
                    assertNull(vaultSvc.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // 1st tx locks states
        backgroundExecutor.submit {
            try {
                database.transaction {
                    vaultSvc.softLockReserve(softLockId1, stateRefsToSoftLock)
                    assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
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
                database.transaction {
                    vaultSvc.softLockReserve(softLockId2, stateRefsToSoftLock)
                    assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId2)).hasSize(3)
                }
                println("SOFT LOCK STATES #2 succeeded")
            } catch(e: Throwable) {
                println("SOFT LOCK STATES #2 failed")
            } finally {
                countDown.countDown()
            }
        }

        countDown.await()
        database.transaction {
            val lockStatesId1 = vaultSvc.softLockedStates<Cash.State>(softLockId1)
            println("SOFT LOCK #1 final states: $lockStatesId1")
            assertThat(lockStatesId1.size).isIn(0, 3)
            val lockStatesId2 = vaultSvc.softLockedStates<Cash.State>(softLockId2)
            println("SOFT LOCK #2 final states: $lockStatesId2")
            assertThat(lockStatesId2.size).isIn(0, 3)
        }
    }

    @Test
    fun `soft locking partial reserve states fails`() {

        val softLockId1 = UUID.randomUUID()
        val softLockId2 = UUID.randomUUID()

        val vaultStates =
                database.transaction {
                    assertNull(vaultSvc.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock 1st state with LockId1
        database.transaction {
            vaultSvc.softLockReserve(softLockId1, setOf(stateRefsToSoftLock.first()))
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(1)
        }

        // attempt to lock all 3 states with LockId2
        database.transaction {
            assertThatExceptionOfType(StatesNotAvailableException::class.java).isThrownBy(
                    { vaultSvc.softLockReserve(softLockId2, stateRefsToSoftLock) }
            ).withMessageContaining("only 2 rows available").withNoCause()
        }
    }

    @Test
    fun `attempt to lock states already soft locked by me`() {

        val softLockId1 = UUID.randomUUID()

        val vaultStates =
                database.transaction {
                    assertNull(vaultSvc.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        database.transaction {
            vaultSvc.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }

        // attempt to relock same states with LockId1
        database.transaction {
            vaultSvc.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }
    }

    @Test
    fun `lock additional states to some already soft locked by me`() {

        val softLockId1 = UUID.randomUUID()

        val vaultStates =
                database.transaction {
                    assertNull(vaultSvc.cashBalances[USD])
                    services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))
                }
        val stateRefsToSoftLock = vaultStates.states.map { it.ref }.toSet()
        println("State Refs:: $stateRefsToSoftLock")

        // lock states with LockId1
        database.transaction {
            vaultSvc.softLockReserve(softLockId1, setOf(stateRefsToSoftLock.first()))
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(1)
        }

        // attempt to lock all states with LockId1 (including previously already locked one)
        database.transaction {
            vaultSvc.softLockReserve(softLockId1, stateRefsToSoftLock)
            assertThat(vaultSvc.softLockedStates<Cash.State>(softLockId1)).hasSize(3)
        }
    }

    @Test
    fun `unconsumedStatesForSpending exact amount`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val unconsumedStates = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = (vaultSvc as NodeVaultService).unconsumedStatesForSpending<Cash.State>(100.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isEqualTo(100L * 100)
            assertThat(vaultSvc.softLockedStates<Cash.State>()).hasSize(1)
        }
    }

    @Test
    fun `unconsumedStatesForSpending from two issuer parties`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY)

            val spendableStatesUSD = vaultSvc.unconsumedStatesForSpending<Cash.State>(200.DOLLARS, lockId = UUID.randomUUID(),
                    onlyFromIssuerParties = setOf(DUMMY_CASH_ISSUER.party, BOC)).toList()
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(2)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer).isEqualTo(DUMMY_CASH_ISSUER)
            assertThat(spendableStatesUSD[1].state.data.amount.token.issuer).isEqualTo(BOC.ref(1))
        }
    }

    @Test
    fun `unconsumedStatesForSpending from specific issuer party and refs`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (DUMMY_CASH_ISSUER))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(1)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(1))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(2)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(2))
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L), issuedBy = (BOC.ref(3)), issuerKey = BOC_KEY, ref = OpaqueBytes.of(3))

            val unconsumedStates = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(4)

            val spendableStatesUSD = vaultSvc.unconsumedStatesForSpending<Cash.State>(200.DOLLARS, lockId = UUID.randomUUID(),
                    onlyFromIssuerParties = setOf(BOC), withIssuerRefs = setOf(OpaqueBytes.of(1), OpaqueBytes.of(2))).toList()
            assertThat(spendableStatesUSD).hasSize(2)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer.party).isEqualTo(BOC)
            assertThat(spendableStatesUSD[0].state.data.amount.token.issuer.reference).isEqualTo(BOC.ref(1).reference)
            assertThat(spendableStatesUSD[1].state.data.amount.token.issuer.reference).isEqualTo(BOC.ref(2).reference)
        }
    }

    @Test
    fun `unconsumedStatesForSpending insufficient amount`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 1, 1, Random(0L))

            val unconsumedStates = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(1)

            val spendableStatesUSD = (vaultSvc as NodeVaultService).unconsumedStatesForSpending<Cash.State>(110.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(vaultSvc.softLockedStates<Cash.State>()).hasSize(0)
        }
    }

    @Test
    fun `unconsumedStatesForSpending small amount`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 2, 2, Random(0L))

            val unconsumedStates = vaultSvc.unconsumedStates<Cash.State>().toList()
            assertThat(unconsumedStates).hasSize(2)

            val spendableStatesUSD = (vaultSvc as NodeVaultService).unconsumedStatesForSpending<Cash.State>(1.DOLLARS, lockId = UUID.randomUUID())
            spendableStatesUSD.forEach(::println)
            assertThat(spendableStatesUSD).hasSize(1)
            assertThat(spendableStatesUSD[0].state.data.amount.quantity).isGreaterThanOrEqualTo(100L)
            assertThat(vaultSvc.softLockedStates<Cash.State>()).hasSize(1)
        }
    }

    @Test
    fun `states soft locking query granularity`() {
        database.transaction {

            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 10, 10, Random(0L))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 10, 10, Random(0L))

            val allStates = vaultSvc.unconsumedStates<Cash.State>()
            assertThat(allStates).hasSize(30)

            for (i in 1..5) {
                val spendableStatesUSD = (vaultSvc as NodeVaultService).unconsumedStatesForSpending<Cash.State>(20.DOLLARS, lockId = UUID.randomUUID())
                spendableStatesUSD.forEach(::println)
            }
            // note only 3 spend attempts succeed with a total of 8 states
            assertThat(vaultSvc.softLockedStates<Cash.State>()).hasSize(8)
        }
    }

    @Test
    fun addNoteToTransaction() {
        database.transaction {

            val freshKey = services.legalIdentityKey

            // Issue a txn to Send us some Money
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(usefulTX))

            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 1")
            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 2")
            vaultSvc.addNoteToTransaction(usefulTX.id, "USD Sample Note 3")
            assertEquals(3, vaultSvc.getTransactionNotes(usefulTX.id).count())

            // Issue more Money (GBP)
            val anotherTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 200.POUNDS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            services.recordTransactions(listOf(anotherTX))

            vaultSvc.addNoteToTransaction(anotherTX.id, "GPB Sample Note 1")
            assertEquals(1, vaultSvc.getTransactionNotes(anotherTX.id).count())
        }
    }
}
