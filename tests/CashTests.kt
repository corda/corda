import org.junit.Test

class CashTests {
    val inState = CashState(
            issuingInstitution = MEGA_CORP,
            depositReference = byteArrayOf(1),
            amount = 1000.DOLLARS,
            owner = DUMMY_PUBKEY_1
    )
    val inState2 = inState.copy(
            amount = 150.POUNDS,
            owner = DUMMY_PUBKEY_2
    )
    val outState = inState.copy(owner = DUMMY_PUBKEY_2)

    @Test
    fun trivial() {
        CashContract().let {
            transaction {
                it `fails requirement` "there is at least one cash input"
            }
            transaction {
                input { inState.copy(amount = 0.DOLLARS) }
                it `fails requirement` "some money is actually moving"
            }

            transaction {
                input { inState }
                it `fails requirement` "the amounts balance"

                transaction {
                    output { outState.copy(amount = 2000.DOLLARS )}
                    it `fails requirement` "the amounts balance"
                }
                transaction {
                    output { outState }
                    // No command arguments
                    it `fails requirement` "the owning keys are the same as the signing keys"
                }
                transaction {
                    output { outState }
                    arg(DUMMY_PUBKEY_2) { MoveCashCommand() }
                    it `fails requirement` "the owning keys are the same as the signing keys"
                }
                transaction {
                    output { outState }
                    arg(DUMMY_PUBKEY_1) { MoveCashCommand() }
                    it.accepts()
                }
            }
        }
    }

    @Test
    fun mismatches() {
        CashContract().let {
            transaction {
                input { inState }
                output { outState.copy(issuingInstitution = MINI_CORP) }
                it `fails requirement` "all outputs claim against the issuer of the inputs"
            }
            transaction {
                input { inState }
                output { outState.copy(issuingInstitution = MEGA_CORP) }
                output { outState.copy(issuingInstitution = MINI_CORP) }
                it `fails requirement` "all outputs claim against the issuer of the inputs"
            }
            transaction {
                input { inState }
                output { outState.copy(depositReference = byteArrayOf(0)) }
                output { outState.copy(depositReference = byteArrayOf(1)) }
                it `fails requirement` "all outputs use the same deposit reference as the inputs"
            }
            transaction {
                input { inState }
                output { outState.copy(amount = 800.DOLLARS) }
                output { outState.copy(amount = 200.POUNDS) }
                it `fails requirement` "all outputs use the currency of the inputs"
            }
            transaction {
                input { inState }
                input { inState2 }
                output { outState.copy(amount = 1150.DOLLARS) }
                it `fails requirement` "all inputs use the same currency"
            }
            transaction {
                input { inState }
                input { inState.copy(issuingInstitution = MINI_CORP) }
                output { outState }
                it `fails requirement` "all inputs come from the same issuer"
            }
        }
    }

    @Test
    fun exitLedger() {
        CashContract().let {
            transaction {
                input { inState }
                output { outState.copy(amount = inState.amount - 200.DOLLARS) }

                transaction {
                    arg(MEGA_CORP_KEY) {
                        ExitCashCommand(100.DOLLARS)
                    }
                    it `fails requirement` "the amounts balance"
                }

                transaction {
                    arg(MEGA_CORP_KEY) {
                        ExitCashCommand(200.DOLLARS)
                    }
                    it `fails requirement` "the owning keys are the same as the signing keys"   // No move command.

                    transaction {
                        arg(DUMMY_PUBKEY_1) { MoveCashCommand() }
                        it.accepts()
                    }
                }
            }
        }
    }
}
