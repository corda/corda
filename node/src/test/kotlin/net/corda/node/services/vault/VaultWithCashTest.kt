package net.corda.node.services.vault

import net.corda.contracts.DummyDealContract
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.contracts.testing.fillWithSomeTestDeals
import net.corda.contracts.testing.fillWithSomeTestLinearStates
import net.corda.core.contracts.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.consumedStates
import net.corda.core.node.services.unconsumedStates
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.LogHelper
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.transaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

// TODO: Move this to the cash contract tests once mock services are further split up.

class VaultWithCashTest {
    lateinit var services: MockServices
    val vault: VaultService get() = services.vaultService
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(VaultWithCashTest::class)
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
        LogHelper.reset(VaultWithCashTest::class)
        dataSource.close()
    }

    @Test
    fun splits() {
        database.transaction {
            // Fix the PRNG so that we get the same splits every time.
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w = vault.unconsumedStates<Cash.State>().toList()
            assertEquals(3, w.size)

            val state = w[0].state.data
            assertEquals(30.45.DOLLARS `issued by` DUMMY_CASH_ISSUER, state.amount)
            assertEquals(services.key.public, state.owner.owningKey)

            assertEquals(34.70.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w[2].state.data).amount)
            assertEquals(34.85.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w[1].state.data).amount)
        }
    }

    @Test
    fun `issue and spend total correctly and irrelevant ignored`() {
        database.transaction {
            // A tx that sends us money.
            val freshKey = services.keyManagementService.freshKey()
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), AnonymousParty(freshKey), DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            assertNull(vault.cashBalances[USD])
            services.recordTransactions(usefulTX)

            // A tx that spends our money.
            val spendTXBuilder = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                vault.generateSpend(this, 80.DOLLARS, BOB)
                signWith(DUMMY_NOTARY_KEY)
            }
            val spendTX = services.signInitialTransaction(spendTXBuilder, freshKey)

            assertEquals(100.DOLLARS, vault.cashBalances[USD])

            // A tx that doesn't send us anything.
            val irrelevantTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), BOB, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            services.recordTransactions(irrelevantTX)
            assertEquals(100.DOLLARS, vault.cashBalances[USD])
            services.recordTransactions(spendTX)

            assertEquals(20.DOLLARS, vault.cashBalances[USD])

            // TODO: Flesh out these tests as needed.
        }
    }

    @Test
    fun `issue and attempt double spend`() {
        val freshKey = services.keyManagementService.freshKey()

        database.transaction {
            // A tx that sends us money.
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 10, 10, Random(0L),
                    issuedBy = MEGA_CORP.ref(1),
                    issuerKey = MEGA_CORP_KEY,
                    ownedBy = AnonymousParty(freshKey))
            println("Cash balance: ${vault.cashBalances[USD]}")

            assertThat(vault.unconsumedStates<Cash.State>()).hasSize(10)
            assertThat(vault.softLockedStates<Cash.State>()).hasSize(0)
        }

        val backgroundExecutor = Executors.newFixedThreadPool(2)
        val countDown = CountDownLatch(2)
        // 1st tx that spends our money.
        backgroundExecutor.submit {
            database.transaction {
                try {
                    val txn1Builder =
                            TransactionType.General.Builder(DUMMY_NOTARY).apply {
                                vault.generateSpend(this, 60.DOLLARS, BOB)
                                signWith(DUMMY_NOTARY_KEY)
                            }
                    val txn1 = services.signInitialTransaction(txn1Builder, freshKey)
                    println("txn1: ${txn1.id} spent ${((txn1.tx.outputs[0].data) as Cash.State).amount}")
                    println("""txn1 states:
                                UNCONSUMED: ${vault.unconsumedStates<Cash.State>().count()} : ${vault.unconsumedStates<Cash.State>()},
                                CONSUMED: ${vault.consumedStates<Cash.State>().count()} : ${vault.consumedStates<Cash.State>()},
                                LOCKED: ${vault.softLockedStates<Cash.State>().count()} : ${vault.softLockedStates<Cash.State>()}
                    """)
                    services.recordTransactions(txn1)
                    println("txn1: Cash balance: ${vault.cashBalances[USD]}")
                    println("""txn1 states:
                                UNCONSUMED: ${vault.unconsumedStates<Cash.State>().count()} : ${vault.unconsumedStates<Cash.State>()},
                                CONSUMED: ${vault.consumedStates<Cash.State>().count()} : ${vault.consumedStates<Cash.State>()},
                                LOCKED: ${vault.softLockedStates<Cash.State>().count()} : ${vault.softLockedStates<Cash.State>()}
                    """)
                    txn1
                } catch(e: Exception) {
                    println(e)
                }
            }
            println("txn1 COMMITTED!")
            countDown.countDown()
        }

        // 2nd tx that attempts to spend same money
        backgroundExecutor.submit {
            database.transaction {
                try {
                    val txn2Builder =
                            TransactionType.General.Builder(DUMMY_NOTARY).apply {
                                vault.generateSpend(this, 80.DOLLARS, BOB)
                                signWith(DUMMY_NOTARY_KEY)
                            }
                    val txn2 = services.signInitialTransaction(txn2Builder, freshKey)
                    println("txn2: ${txn2.id} spent ${((txn2.tx.outputs[0].data) as Cash.State).amount}")
                    println("""txn2 states:
                                UNCONSUMED: ${vault.unconsumedStates<Cash.State>().count()} : ${vault.unconsumedStates<Cash.State>()},
                                CONSUMED: ${vault.consumedStates<Cash.State>().count()} : ${vault.consumedStates<Cash.State>()},
                                LOCKED: ${vault.softLockedStates<Cash.State>().count()} : ${vault.softLockedStates<Cash.State>()}
                    """)
                    services.recordTransactions(txn2)
                    println("txn2: Cash balance: ${vault.cashBalances[USD]}")
                    println("""txn2 states:
                                UNCONSUMED: ${vault.unconsumedStates<Cash.State>().count()} : ${vault.unconsumedStates<Cash.State>()},
                                CONSUMED: ${vault.consumedStates<Cash.State>().count()} : ${vault.consumedStates<Cash.State>()},
                                LOCKED: ${vault.softLockedStates<Cash.State>().count()} : ${vault.softLockedStates<Cash.State>()}
                    """)
                    txn2
                } catch(e: Exception) {
                    println(e)
                }
            }
            println("txn2 COMMITTED!")

            countDown.countDown()
        }

        countDown.await()
        database.transaction {
            println("Cash balance: ${vault.cashBalances[USD]}")
            assertThat(vault.cashBalances[USD]).isIn(DOLLARS(20), DOLLARS(40))
        }
    }

    @Test
    fun `branching LinearStates fails to verify`() {
        database.transaction {
            val freshKey = services.keyManagementService.freshKey()
            val freshIdentity = AnonymousParty(freshKey)
            val linearId = UniqueIdentifier()

            // Issue a linear state
            val dummyIssueBuilder = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)))
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)))
                signWith(DUMMY_NOTARY_KEY)
            }
            val dummyIssue = services.signInitialTransaction(dummyIssueBuilder)

            assertThatThrownBy {
                dummyIssue.toLedgerTransaction(services).verify()
            }
        }
    }

    @Test
    fun `sequencing LinearStates works`() {
        database.transaction {
            val freshKey = services.keyManagementService.freshKey()
            val freshIdentity = AnonymousParty(freshKey)

            val linearId = UniqueIdentifier()

            // Issue a linear state
            val dummyIssueBuilder = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)))
                signWith(DUMMY_NOTARY_KEY)
            }
            val dummyIssue = services.signInitialTransaction(dummyIssueBuilder, services.legalIdentityKey)

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyIssue)
            assertThat(vault.unconsumedStates<DummyLinearContract.State>()).hasSize(1)

            // Move the same state
            val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshIdentity)))
                addInputState(dummyIssue.tx.outRef<LinearState>(0))
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyMove)
            assertThat(vault.unconsumedStates<DummyLinearContract.State>()).hasSize(1)
        }
    }

    @Test
    fun `spending cash in vault of mixed state types works`() {

        val freshKey = services.keyManagementService.freshKey()
        database.transaction {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L), ownedBy = AnonymousParty(freshKey))
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            val cash = vault.unconsumedStates<Cash.State>()
            cash.forEach { println(it.state.data.amount) }

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
            val deals = vault.unconsumedStates<DummyDealContract.State>()
            deals.forEach { println(it.state.data.ref) }
        }

        database.transaction {
            // A tx that spends our money.
            val spendTXBuilder = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                vault.generateSpend(this, 80.DOLLARS, BOB)
                signWith(DUMMY_NOTARY_KEY)
            }
            val spendTX = services.signInitialTransaction(spendTXBuilder, freshKey)
            services.recordTransactions(spendTX)

            val consumedStates = vault.consumedStates<ContractState>()
            assertEquals(3, consumedStates.count())

            val unconsumedStates = vault.unconsumedStates<ContractState>()
            assertEquals(7, unconsumedStates.count())
        }
    }

    @Test
    fun `consuming multiple contract state types in same transaction`() {

        val freshKey = services.keyManagementService.freshKey()
        val freshIdentity = AnonymousParty(freshKey)
        database.transaction {

            services.fillWithSomeTestDeals(listOf("123", "456", "789"))
            val deals = vault.unconsumedStates<DummyDealContract.State>().toList()
            deals.forEach { println(it.state.data.ref) }

            services.fillWithSomeTestLinearStates(3)
            val linearStates = vault.unconsumedStates<DummyLinearContract.State>().toList()
            linearStates.forEach { println(it.state.data.linearId) }

            // Create a txn consuming different contract types
            val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(participants = listOf(freshIdentity)))
                addOutputState(DummyDealContract.State(ref = "999", participants = listOf(freshIdentity)))
                addInputState(linearStates.first())
                addInputState(deals.first())
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyMove.toLedgerTransaction(services).verify()
            services.recordTransactions(dummyMove)

            val consumedStates = vault.consumedStates<ContractState>()
            assertEquals(2, consumedStates.count())

            val unconsumedStates = vault.unconsumedStates<ContractState>()
            assertEquals(6, unconsumedStates.count())
        }
    }
}
