package contracts

import core.DOLLARS
import core.InstitutionReference
import core.OpaqueBytes
import core.days
import core.testutils.*
import org.junit.Test

// TODO: Finish this off.

class CommercialPaperTests {
    val PAPER_1 = CommercialPaper.State(
            issuance = InstitutionReference(MEGA_CORP, OpaqueBytes.of(123)),
            owner = DUMMY_PUBKEY_1,
            faceValue = 1000.DOLLARS,
            maturityDate = TEST_TX_TIME + 7.days
    )
    val PAPER_2 = PAPER_1.copy(owner = DUMMY_PUBKEY_2)

    val CASH_1 = Cash.State(InstitutionReference(MINI_CORP, OpaqueBytes.of(1)), 1000.DOLLARS, DUMMY_PUBKEY_1)
    val CASH_2 = CASH_1.copy(owner = DUMMY_PUBKEY_2)
    val CASH_3 = CASH_1.copy(owner = DUMMY_PUBKEY_1)

    @Test
    fun move() {
        transaction {
            // One entity sells the paper to another (e.g. the issuer sells it to a first time buyer)
            input { PAPER_1 }
            input { CASH_1 }
            output("a") { PAPER_2 }
            output { CASH_2 }

            this.rejects()

            transaction {
                arg(DUMMY_PUBKEY_2) { CommercialPaper.Commands.Move }
                this `fails requirement` "is signed by the owner"
            }

            arg(DUMMY_PUBKEY_1) { CommercialPaper.Commands.Move }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }
            this.accepts()
        }.chain("a") {
            arg(DUMMY_PUBKEY_2, MINI_CORP_KEY) { CommercialPaper.Commands.Redeem }

            // No cash output, can't redeem like that!
            this.rejects("no cash being redeemed")

            input { CASH_3 }
            output { CASH_2 }
            arg(DUMMY_PUBKEY_1) { Cash.Commands.Move }

            // Time passes, but not enough. An attempt to redeem is made.
            this.rejects("must have matured")

            // Try again at the right time.
            this.accepts(TEST_TX_TIME + 10.days)
        }
    }
}