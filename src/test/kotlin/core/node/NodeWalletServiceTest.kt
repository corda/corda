/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import contracts.Cash
import core.*
import core.testutils.*
import core.utilities.BriefLogFormatter
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeWalletServiceTest {
    val kms = MockKeyManagementService(ALICE_KEY)
    val services: ServiceHub = MockServices(keyManagement = kms)

    @Before
    fun setUp() {
        BriefLogFormatter.loggingOn(NodeWalletService::class)
    }

    @After
    fun tearDown() {
        BriefLogFormatter.loggingOff(NodeWalletService::class)
    }

    @Test
    fun splits() {
        val wallet = NodeWalletService(services)
        kms.nextKeys += Array(3) { ALICE_KEY }
        // Fix the PRNG so that we get the same splits every time.
        wallet.fillWithSomeTestCash(100.DOLLARS, 3, 3, Random(0L))

        val w = wallet.currentWallet
        assertEquals(3, w.states.size)

        val state = w.states[0].state as Cash.State
        assertEquals(services.storageService.myLegalIdentity, state.deposit.party)
        assertEquals(services.storageService.myLegalIdentityKey.public, state.deposit.party.owningKey)
        assertEquals(29.01.DOLLARS, state.amount)
        assertEquals(ALICE, state.owner)

        assertEquals(33.34.DOLLARS, (w.states[2].state as Cash.State).amount)
        assertEquals(35.61.DOLLARS, (w.states[1].state as Cash.State).amount)
    }

    @Test
    fun basics() {
        val wallet = NodeWalletService(services)

        // A tx that sends us money.
        val freshKey = services.keyManagementService.freshKey()
        val usefulTX = TransactionBuilder().apply {
            Cash().generateIssue(this, 100.DOLLARS, MEGA_CORP.ref(1), freshKey.public)
            signWith(MEGA_CORP_KEY)
        }.toSignedTransaction()
        val myOutput = usefulTX.verifyToLedgerTransaction(MockIdentityService, MockStorageService().attachments).outRef<Cash.State>(0)

        // A tx that spends our money.
        val spendTX = TransactionBuilder().apply {
            Cash().generateSpend(this, 80.DOLLARS, BOB, listOf(myOutput))
            signWith(freshKey)
        }.toSignedTransaction()

        // A tx that doesn't send us anything.
        val irrelevantTX = TransactionBuilder().apply {
            Cash().generateIssue(this, 100.DOLLARS, MEGA_CORP.ref(1), BOB_KEY.public)
            signWith(MEGA_CORP_KEY)
        }.toSignedTransaction()

        assertNull(wallet.cashBalances[USD])
        wallet.notify(usefulTX.tx)
        assertEquals(100.DOLLARS, wallet.cashBalances[USD])
        wallet.notify(irrelevantTX.tx)
        assertEquals(100.DOLLARS, wallet.cashBalances[USD])
        wallet.notify(spendTX.tx)
        assertEquals(20.DOLLARS, wallet.cashBalances[USD])

        // TODO: Flesh out these tests as needed.
    }
}
