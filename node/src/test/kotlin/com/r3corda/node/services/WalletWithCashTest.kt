package com.r3corda.node.services

import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER
import com.r3corda.contracts.asset.cashBalances
import com.r3corda.contracts.testing.fillWithSomeTestCash
import com.r3corda.core.contracts.*
import com.r3corda.core.node.services.WalletService
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.services.wallet.NodeWalletService
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.testing.node.MockServices
import com.r3corda.testing.node.makeTestDataSourceProperties
import com.r3corda.testing.DummyLinearContract
import com.r3corda.testing.*
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

// TODO: Move this to the cash contract tests once mock services are further split up.

class WalletWithCashTest {
    lateinit var services: MockServices
    val wallet: WalletService get() = services.walletService
    lateinit var dataSource: Closeable

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeWalletService::class)
        dataSource = configureDatabase(makeTestDataSourceProperties()).first
        services = object : MockServices() {
            override val walletService: WalletService = NodeWalletService(this)

            override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                for (stx in txs) {
                    storageService.validatedTransactions.addTransaction(stx)
                    walletService.notify(stx.tx)
                }
            }
        }
    }

    @After
    fun tearDown() {
        LogHelper.reset(NodeWalletService::class)
        dataSource.close()
    }

    @Test
    fun splits() {
        // Fix the PRNG so that we get the same splits every time.
        services.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

        val w = wallet.currentWallet
        assertEquals(3, w.states.toList().size)

        val state = w.states.toList()[0].state.data as Cash.State
        assertEquals(30.45.DOLLARS `issued by` DUMMY_CASH_ISSUER, state.amount)
        assertEquals(services.key.public, state.owner)

        assertEquals(34.70.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.states.toList()[2].state.data as Cash.State).amount)
        assertEquals(34.85.DOLLARS `issued by` DUMMY_CASH_ISSUER, (w.states.toList()[1].state.data as Cash.State).amount)
    }

    @Test
    fun basics() {
        // A tx that sends us money.
        val freshKey = services.keyManagementService.freshKey()
        val usefulTX = TransactionType.General.Builder(null).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), freshKey.public, DUMMY_NOTARY)
            signWith(MEGA_CORP_KEY)
        }.toSignedTransaction()
        val myOutput = usefulTX.toLedgerTransaction(services).outRef<Cash.State>(0)

        // A tx that spends our money.
        val spendTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Cash().generateSpend(this, 80.DOLLARS, BOB_PUBKEY, listOf(myOutput))
            signWith(freshKey)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        // A tx that doesn't send us anything.
        val irrelevantTX = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            Cash().generateIssue(this, 100.DOLLARS `issued by` MEGA_CORP.ref(1), BOB_KEY.public, DUMMY_NOTARY)
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        assertNull(wallet.currentWallet.cashBalances[USD])
        wallet.notify(usefulTX.tx)
        assertEquals(100.DOLLARS, wallet.currentWallet.cashBalances[USD])
        wallet.notify(irrelevantTX.tx)
        assertEquals(100.DOLLARS, wallet.currentWallet.cashBalances[USD])
        wallet.notify(spendTX.tx)
        assertEquals(20.DOLLARS, wallet.currentWallet.cashBalances[USD])

        // TODO: Flesh out these tests as needed.
    }


    @Test
    fun branchingLinearStatesFailsToVerify() {
        val freshKey = services.keyManagementService.freshKey()
        val linearId = UniqueIdentifier()

        // Issue a linear state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public)))
            addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public)))
            signWith(freshKey)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        assertThatThrownBy {
            dummyIssue.toLedgerTransaction(services).verify()
        }
    }

    @Test
    fun sequencingLinearStatesWorks() {
        val freshKey = services.keyManagementService.freshKey()

        val linearId = UniqueIdentifier()

        // Issue a linear state
        val dummyIssue = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public)))
            signWith(freshKey)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        dummyIssue.toLedgerTransaction(services).verify()

        wallet.notify(dummyIssue.tx)
        assertEquals(1, wallet.currentWallet.states.toList().size)

        // Move the same state
        val dummyMove = TransactionType.General.Builder(notary = DUMMY_NOTARY).apply {
            addOutputState(DummyLinearContract.State(linearId = linearId, participants = listOf(freshKey.public)))
            addInputState(dummyIssue.tx.outRef<LinearState>(0))
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction()

        dummyIssue.toLedgerTransaction(services).verify()

        wallet.notify(dummyMove.tx)
        assertEquals(1, wallet.currentWallet.states.toList().size)
    }
}
