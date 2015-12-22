/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import core.testutils.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommercialPaperTests {
    val PAPER_1 = CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP_PUBKEY,
            faceValue = 1000.DOLLARS,
            maturityDate = TEST_TX_TIME + 7.days
    )

    @Test
    fun ok() {
        trade().verify()
    }

    @Test
    fun `not matured at redemption`() {
        trade(redemptionTime = TEST_TX_TIME + 2.days).expectFailureOfTx(3, "must have matured")
    }

    @Test
    fun `key mismatch at issue`() {
        transactionGroup {
            transaction {
                output { PAPER_1 }
                arg(DUMMY_PUBKEY_1) { CommercialPaper.Commands.Issue() }
                timestamp(TEST_TX_TIME)
            }

            expectFailureOfTx(1, "signed by the claimed issuer")
        }
    }

    @Test
    fun `face value is not zero`() {
        transactionGroup {
            transaction {
                output { PAPER_1.copy(faceValue = 0.DOLLARS) }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
                timestamp(TEST_TX_TIME)
            }

            expectFailureOfTx(1, "face value is not zero")
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transactionGroup {
            transaction {
                output { PAPER_1.copy(maturityDate = TEST_TX_TIME - 10.days) }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
                timestamp(TEST_TX_TIME)
            }

            expectFailureOfTx(1, "maturity date is not in the past")
        }
    }

    @Test
    fun `timestamp out of range`() {
        // Check what happens if the timestamp on the transaction itself defines a range that doesn't include true
        // time as measured by a TSA (which is running five hours ahead in this test).
        CommercialPaper().craftIssue(MINI_CORP.ref(123), 10000.DOLLARS, TEST_TX_TIME + 30.days).apply {
            setTime(TEST_TX_TIME, DummyTimestampingAuthority.identity, 30.seconds)
            signWith(MINI_CORP_KEY)
            assertFailsWith(NotOnTimeException::class) {
                timestamp(DummyTimestamper(Clock.fixed(TEST_TX_TIME + 5.hours, ZoneOffset.UTC)))
            }
        }
        // Check that it also fails if true time is before the threshold (we are trying to timestamp too early).
        CommercialPaper().craftIssue(MINI_CORP.ref(123), 10000.DOLLARS, TEST_TX_TIME + 30.days).apply {
            setTime(TEST_TX_TIME, DummyTimestampingAuthority.identity, 30.seconds)
            signWith(MINI_CORP_KEY)
            assertFailsWith(NotOnTimeException::class) {
                val tsaClock = Clock.fixed(TEST_TX_TIME - 5.hours, ZoneOffset.UTC)
                timestamp(DummyTimestamper(tsaClock), Clock.fixed(TEST_TX_TIME, ZoneOffset.UTC))
            }
        }
    }

    @Test
    fun `issue cannot replace an existing state`() {
        transactionGroup {
            roots {
                transaction(PAPER_1 label "paper")
            }
            transaction {
                input("paper")
                output { PAPER_1 }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
                timestamp(TEST_TX_TIME)
            }

            expectFailureOfTx(1, "there is no input state")
        }
    }

    @Test
    fun `did not receive enough money at redemption`() {
        trade(aliceGetsBack = 700.DOLLARS).expectFailureOfTx(3, "received amount equals the face value")
    }

    @Test
    fun `paper must be destroyed by redemption`() {
        trade(destroyPaperAtRedemption = false).expectFailureOfTx(3, "must be destroyed")
    }

    fun cashOutputsToWallet(vararg states: Cash.State): Pair<LedgerTransaction, List<StateAndRef<Cash.State>>> {
        val ltx = LedgerTransaction(emptyList(), listOf(*states), emptyList(), SecureHash.randomSHA256())
        return Pair(ltx, states.mapIndexed { index, state -> StateAndRef(state, ContractStateRef(ltx.hash, index)) })
    }

    @Test
    fun `issue move and then redeem`() {
        // MiniCorp issues $10,000 of commercial paper, to mature in 30 days, owned initially by itself.
        val issueTX: LedgerTransaction = run {
            val ptx = CommercialPaper().craftIssue(MINI_CORP.ref(123), 10000.DOLLARS, TEST_TX_TIME + 30.days).apply {
                setTime(TEST_TX_TIME, DummyTimestampingAuthority.identity, 30.seconds)
                signWith(MINI_CORP_KEY)
                timestamp(DUMMY_TIMESTAMPER)
            }
            val stx = ptx.toSignedTransaction()
            stx.verifyToLedgerTransaction(MockIdentityService)
        }

        val (alicesWalletTX, alicesWallet) = cashOutputsToWallet(
                3000.DOLLARS.CASH `owned by` ALICE,
                3000.DOLLARS.CASH `owned by` ALICE,
                3000.DOLLARS.CASH `owned by` ALICE
        )

        // Alice pays $9000 to MiniCorp to own some of their debt.
        val moveTX: LedgerTransaction = run {
            val ptx = TransactionBuilder()
            Cash().craftSpend(ptx, 9000.DOLLARS, MINI_CORP_PUBKEY, alicesWallet)
            CommercialPaper().craftMove(ptx, issueTX.outRef(0), ALICE)
            ptx.signWith(MINI_CORP_KEY)
            ptx.signWith(ALICE_KEY)
            val stx = ptx.toSignedTransaction()
            stx.verifyToLedgerTransaction(MockIdentityService)
        }

        // Won't be validated.
        val (corpWalletTX, corpWallet) = cashOutputsToWallet(
                9000.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY,
                4000.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY
        )

        fun makeRedeemTX(time: Instant): LedgerTransaction {
            val ptx = TransactionBuilder()
            ptx.setTime(time, DummyTimestampingAuthority.identity, 30.seconds)
            CommercialPaper().craftRedeem(ptx, moveTX.outRef(1), corpWallet)
            ptx.signWith(ALICE_KEY)
            ptx.signWith(MINI_CORP_KEY)
            ptx.timestamp(DUMMY_TIMESTAMPER)
            return ptx.toSignedTransaction().verifyToLedgerTransaction(MockIdentityService)
        }

        val tooEarlyRedemption = makeRedeemTX(TEST_TX_TIME + 10.days)
        val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days)

        val e = assertFailsWith(TransactionVerificationException::class) {
            TransactionGroup(setOf(issueTX, moveTX, tooEarlyRedemption), setOf(corpWalletTX, alicesWalletTX)).verify(TEST_PROGRAM_MAP)
        }
        assertTrue(e.cause!!.message!!.contains("paper must have matured"))

        TransactionGroup(setOf(issueTX, moveTX, validRedemption), setOf(corpWalletTX, alicesWalletTX)).verify(TEST_PROGRAM_MAP)
    }

    // Generate a trade lifecycle with various parameters.
    fun trade(redemptionTime: Instant = TEST_TX_TIME + 8.days,
              aliceGetsBack: Amount = 1000.DOLLARS,
              destroyPaperAtRedemption: Boolean = true): TransactionGroupDSL<CommercialPaper.State> {
        val someProfits = 1200.DOLLARS
        return transactionGroupFor() {
            roots {
                transaction(900.DOLLARS.CASH `owned by` ALICE label "alice's $900")
                transaction(someProfits.CASH `owned by` MEGA_CORP_PUBKEY label "some profits")
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output("paper") { PAPER_1 }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
                timestamp(TEST_TX_TIME)
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output("borrowed $900") { 900.DOLLARS.CASH `owned by` MEGA_CORP_PUBKEY }
                output("alice's paper") { "paper".output `owned by` ALICE }
                arg(ALICE) { Cash.Commands.Move() }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Move() }
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption") {
                input("alice's paper")
                input("some profits")

                output("Alice's profit") { aliceGetsBack.CASH `owned by` ALICE }
                output("Change") { (someProfits - aliceGetsBack).CASH `owned by` MEGA_CORP_PUBKEY }
                if (!destroyPaperAtRedemption)
                    output { "paper".output }

                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                arg(ALICE) { CommercialPaper.Commands.Redeem() }

                timestamp(redemptionTime)
            }
        }
    }
}

fun main(args: Array<String>) {
    CommercialPaperTests().trade().visualise()
}
