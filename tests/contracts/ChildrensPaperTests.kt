package contracts

import core.*
import org.junit.Test

// TODO: Finish this off.

class ChildrensPaperTests {
    val contract = ChildrensPaper

    val PAPER_1 = ChildrensPaperState(
            issuance = InstitutionReference(MEGA_CORP, OpaqueBytes.of(123)),
            owner = DUMMY_PUBKEY_1,
            faceValue = 1000.DOLLARS,
            maturityDate = TEST_TX_TIME + 7.days
    )
    val PAPER_2 = PAPER_1.copy(owner = DUMMY_PUBKEY_2)

    @Test
    fun move() {
        // One entity sells the paper to another (e.g. the issuer sells it to a first time buyer)
        transaction {
            input { PAPER_1 }
            output { PAPER_2 }

            contract.rejects()

            transaction {
                arg(DUMMY_PUBKEY_2) { CPCommands.MoveCommand() }
                contract `fails requirement` "is signed by the owner"
            }

            transaction {
                arg(DUMMY_PUBKEY_1) { CPCommands.MoveCommand() }
                contract.accepts()
            }
        }
    }
}