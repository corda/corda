package contracts

import core.*
import core.testutils.*
import org.junit.Test
import java.time.Instant

class CommercialPaperTests {
    val PAPER_1 = CommercialPaper.State(
            issuance = InstitutionReference(MEGA_CORP, OpaqueBytes.of(123)),
            owner = MEGA_CORP_KEY,
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
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Issue }
            }

            expectFailureOfTx(1, "face value is not zero")
        }
    }

    @Test
    fun `maturity date not in the past`() {
        transactionGroup {
            transaction {
                output { PAPER_1.copy(maturityDate = TEST_TX_TIME - 10.days) }
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Issue }
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
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Issue }
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
                      destroyPaperAtRedemption: Boolean = true): TransactionGroupForTest {
        val someProfits = 1200.DOLLARS
        return transactionGroup {
            roots {
                transaction(900.DOLLARS.CASH `owned by` ALICE label "alice's $900")
                transaction(someProfits.CASH `owned by` MEGA_CORP_KEY label "some profits")
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction {
                output("paper") { PAPER_1 }
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Issue }
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction {
                input("paper")
                input("alice's $900")
                output { 900.DOLLARS.CASH `owned by` MEGA_CORP_KEY }
                output("alice's paper") { PAPER_1 `owned by` ALICE }
                arg(ALICE) { Cash.Commands.Move }
                arg(MEGA_CORP_KEY) { CommercialPaper.Commands.Move }
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction(time = redemptionTime) {
                input("alice's paper")
                input("some profits")

                output { aliceGetsBack.CASH `owned by` ALICE }
                output { (someProfits - aliceGetsBack).CASH `owned by` MEGA_CORP_KEY }
                if (!destroyPaperAtRedemption)
                    output { PAPER_1 `owned by` ALICE }

                arg(MEGA_CORP_KEY) { Cash.Commands.Move }
                arg(ALICE) { CommercialPaper.Commands.Redeem }
            }
        }
    }
}