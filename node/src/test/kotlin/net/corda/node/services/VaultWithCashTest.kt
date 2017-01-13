package net.corda.node.services

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.contracts.testing.fillWithSomeTestDeals
import net.corda.contracts.testing.fillWithSomeTestLinearStates
import net.corda.core.contracts.*
import net.corda.core.crypto.composite
import net.corda.core.node.recordTransactions
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.consumedStates
import net.corda.core.node.services.unconsumedStates
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.core.utilities.LogHelper
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.BOB_KEY
import net.corda.testing.BOB_PUBKEY
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
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
        LogHelper.setLevel(VaultWithCashTest::class)
        val dataSourceProps = makeTestDataSourceProperties()
        val dataSourceAndDatabase = configureDatabase(dataSourceProps)
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        databaseTransaction(database) {
            services = object : MockServices() {
                override val vaultService: VaultService = NodeVaultService(this, dataSourceProps)

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
        LogHelper.reset(VaultWithCashTest::class)
        dataSource.close()
    }

    @Test
    fun splits() {
        databaseTransaction(database) {
            // Fix the PRNG so that we get the same splits every time.
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

            val w = vault.unconsumedStates<Cash.State>()
            assertEquals(3, w.toList().size)

            val state = w.toList()[0].state.data
            assertEquals(30.45.DOLLARS `issued by` DUMMY_CASH_ISSUER, state.amount)
            assertEquals(services.key.public.composite, state.owner)

            assertEquals(34.70.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.toList()[2].state.data).amount)
            assertEquals(34.85.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.toList()[1].state.data).amount)
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
                addOutputState(net.corda.contracts.testing.DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                addOutputState(net.corda.contracts.testing.DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
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
                addOutputState(net.corda.contracts.testing.DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                signWith(freshKey)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyIssue)
            assertEquals(1, vault.unconsumedStates<net.corda.contracts.testing.DummyLinearContract.State>().size)

            // Move the same state
            val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(net.corda.contracts.testing.DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public.composite)))
                addInputState(dummyIssue.tx.outRef<LinearState>(0))
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()

            dummyIssue.toLedgerTransaction(services).verify()

            services.recordTransactions(dummyMove)
            assertEquals(1, vault.unconsumedStates<net.corda.contracts.testing.DummyLinearContract.State>().size)
        }
    }

    @Test
    fun `spending cash in vault of mixed state types works`() {

        val freshKey = services.keyManagementService.freshKey()
        databaseTransaction(database) {
            services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L), ownedBy = freshKey.public.composite)
            services.fillWithSomeTestCash(100.SWISS_FRANCS, DUMMY_NOTARY, 2, 2, Random(0L))
            services.fillWithSomeTestCash(100.POUNDS, DUMMY_NOTARY, 1, 1, Random(0L))
            val cash = vault.unconsumedStates<Cash.State>()
            cash.forEach { println(it.state.data.amount) }

            services.fillWithSomeTestDeals(listOf("123","456","789"))
            val deals = vault.unconsumedStates<net.corda.contracts.testing.DummyDealContract.State>()
            deals.forEach { println(it.state.data.ref) }
        }

        databaseTransaction(database) {
            // A tx that spends our money.
            val spendTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
                vault.generateSpend(this, 80.DOLLARS, BOB_PUBKEY)
                signWith(freshKey)
                signWith(DUMMY_NOTARY_KEY)
            }.toSignedTransaction()
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
        databaseTransaction(database) {

            services.fillWithSomeTestDeals(listOf("123","456","789"))
            val deals = vault.unconsumedStates<net.corda.contracts.testing.DummyDealContract.State>()
            deals.forEach { println(it.state.data.ref) }

            services.fillWithSomeTestLinearStates(3)
            val linearStates = vault.unconsumedStates<net.corda.contracts.testing.DummyLinearContract.State>()
            linearStates.forEach { println(it.state.data.linearId) }

            // Create a txn consuming different contract types
            val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
                addOutputState(net.corda.contracts.testing.DummyLinearContract.State(participants = listOf(freshKey.public.composite)))
                addOutputState(net.corda.contracts.testing.DummyDealContract.State(ref = "999", participants = listOf(freshKey.public.composite)))
                addInputState(linearStates[0])
                addInputState(deals[0])
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
