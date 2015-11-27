package contracts

import core.*
import core.testutils.*
import org.junit.Test
import java.time.Instant
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
                arg(DUMMY_PUBKEY_1) { CommercialPaper.Commands.Issue }
            }

            expectFailureOfTx(1, "signed by the claimed issuer")
        }
    }

    @Test
    fun `face value is not zero`() {
        transactionGroup {
            transaction {
                output { PAPER_1.copy(faceValue = 0.DOLLARS) }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue }
            }

            expectFailureOfTx(1, "face value is not zero")
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transactionGroup {
            transaction {
                output { PAPER_1.copy(maturityDate = TEST_TX_TIME - 10.days) }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue }
            }

            expectFailureOfTx(1, "maturity date is not in the past")
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
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue }
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

    // Generate a trade lifecycle with various parameters.
    private fun trade(redemptionTime: Instant = TEST_TX_TIME + 8.days,
                      aliceGetsBack: Amount = 1000.DOLLARS,
                      destroyPaperAtRedemption: Boolean = true): TransactionGroupForTest<CommercialPaper.State> {
        val someProfits = 1200.DOLLARS
        return transactionGroupFor() {
            roots {
                transaction(900.DOLLARS.CASH `owned by` ALICE label "alice's $900")
                transaction(someProfits.CASH `owned by` MEGA_CORP_PUBKEY label "some profits")
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction {
                output("paper") { PAPER_1 }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue }
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction {
                input("paper")
                input("alice's $900")
                output { 900.DOLLARS.CASH `owned by` MEGA_CORP_PUBKEY }
                output("alice's paper") { "paper".output `owned by` ALICE }
                arg(ALICE) { Cash.Commands.Move }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Move }
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction(time = redemptionTime) {
                input("alice's paper")
                input("some profits")

                output { aliceGetsBack.CASH `owned by` ALICE }
                output { (someProfits - aliceGetsBack).CASH `owned by` MEGA_CORP_PUBKEY }
                if (!destroyPaperAtRedemption)
                    output { "paper".output }

                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move }
                arg(ALICE) { CommercialPaper.Commands.Redeem }
            }
        }
    }

    @Test
    fun `issue move and then redeem`() {
        val issueTX: LedgerTransaction = run {
            val ptx = CommercialPaper().craftIssue(MINI_CORP.ref(123), 10000.DOLLARS, TEST_TX_TIME + 30.days)
            ptx.signWith(MINI_CORP_KEY)
            val stx = ptx.toSignedTransaction()
            stx.verify().toLedgerTransaction(TEST_TX_TIME, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        val moveTX: LedgerTransaction = run {
            val ptx = PartialTransaction()
            CommercialPaper().craftMove(ptx, issueTX.outRef(0), ALICE)
            ptx.signWith(MINI_CORP_KEY)
            val stx = ptx.toSignedTransaction()
            stx.verify().toLedgerTransaction(TEST_TX_TIME, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        // Won't be validated.
        val someCash = LedgerTransaction(emptyList(), listOf(
                9000.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY,
                4000.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY
        ),  emptyList(), TEST_TX_TIME, SecureHash.randomSHA256())
        val wallet = listOf<StateAndRef<Cash.State>>(someCash.outRef(0), someCash.outRef(1))


        fun makeRedeemTX(time: Instant): LedgerTransaction {
            val ptx = PartialTransaction()
            CommercialPaper().craftRedeem(ptx, moveTX.outRef(0), wallet)
            ptx.signWith(ALICE_KEY)
            ptx.signWith(MINI_CORP_KEY)
            return ptx.toSignedTransaction().verify().toLedgerTransaction(time, TEST_KEYS_TO_CORP_MAP, SecureHash.randomSHA256())
        }

        val tooEarlyRedemption = makeRedeemTX(TEST_TX_TIME + 10.days)
        val validRedemption = makeRedeemTX(TEST_TX_TIME + 31.days)

        val e = assertFailsWith(TransactionVerificationException::class) {
            TransactionGroup(setOf(issueTX, moveTX, tooEarlyRedemption), setOf(someCash)).verify(TEST_PROGRAM_MAP)
        }
        assertTrue(e.cause!!.message!!.contains("paper must have matured"))

        TransactionGroup(setOf(issueTX, moveTX, validRedemption), setOf(someCash)).verify(TEST_PROGRAM_MAP)
    }
}