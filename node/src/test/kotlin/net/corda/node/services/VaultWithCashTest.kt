package net.corda.node.services

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.composite
import net.corda.core.node.recordTransactions
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.LogHelper
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.*
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
        LogHelper.setLevel(NodeVaultService::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        databaseTransaction(database) {
            services = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this)

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
        LogHelper.reset(NodeVaultService::class)
        dataSource.close()
    }

    @Test
    fun splits() {
        databaseTransaction(database) {
            // Fix the PRNG so that we get the same splits every time.
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w = vault.currentVault
            assertEquals(3, w.states.toList().size)

            val state = w.states.toList()[0].state.data as Cash.State
            assertEquals(30.45.DOLLARS `issued by` DUMMY_CASH_ISSUER, state.amount)
            assertEquals(services.key.public.composite, state.owner)

            assertEquals(34.70.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.states.toList()[2].state.data as Cash.State).amount)
            assertEquals(34.85.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.states.toList()[1].state.data as Cash.State).amount)
        }
    }

    @Test
    fun `issue and spend total correctly and irrelevant ignored`() {
        databaseTransaction(database) {
            // A tx that sends us money.
            val freshKey = services.keyManagementService.freshKey()
            val usefulTX = TransactionType.General.Builder(null).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), freshKey.public.composite, DUMMY_NOTARY)
                signWith(MEGA_CORP_KEY)
            }.toSignedTransaction()

            assertNull(vault.cashBalances[USD])
            services.recordTransactions(usefulTX)

            // A tx that spends our money.
            val spendTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                vault.generateSpend(this, 80.DOLLARS, BOB_PUBKEY)
                signWith(freshKey)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            assertEquals(100.DOLLARS, vault.cashBalances[USD])

            // A tx that doesn't send us anything.
            val irrelevantTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), BOB_KEY.public.composite, DUMMY_NOTARY)
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
    fun `branching LinearStates fails to verify`() {
        databaseTransaction(database) {
            val freshKey = services.keyManagementService.freshKey()
            val linearId = UniqueIdentifier()

            // Issue a linear state
            val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                signWith(freshKey)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            assertThatThrownBy {
                dummyIssue.toLedgerTransaction(services).verify()
            }
        }
    }

    @Test
    fun `sequencing LinearStates works`() {
        databaseTransaction(database) {
            val freshKey = services.keyManagementService.freshKey()

            val linearId = UniqueIdentifier()

            // Issue a linear state
            val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                signWith(freshKey)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyIssue)
            assertEquals(1, vault.currentVault.states.toList().size)

            // Move the same state
            val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                addInputState(dummyIssue.tx.outRef<LinearState>(0))
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyMove)
            assertEquals(1, vault.currentVault.states.toList().size)
        }
    }
}
