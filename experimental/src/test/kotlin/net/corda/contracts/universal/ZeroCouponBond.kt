package net.corda.contracts.universal

import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
import org.junit.Test
import java.time.Instant

class ZeroCouponBond {

    val contract = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 100.K, GBP)
                }
            }
        }
    }

    val contractMove = arrange {
        actions {
            (momAndPop or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(momAndPop, 100.K, GBP)
                }
            }
        }
    }

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val transfer = arrange { highStreetBank.owes(acmeCorp, 100.K, GBP) }
    val transferWrong = arrange { highStreetBank.owes(acmeCorp, 80.K, GBP) }

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contract)

    val outState = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transfer)
    val outStateWrong = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transferWrong)

    val outStateMove = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractMove)

    @Test
    fun basic() {
        assert(Zero().equals(Zero()))
    }


    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }

            this `fails with` "transaction has a single command"

            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { inState }
            output { outState }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Action("execute") }

            this.verifies()
        }
    }

    @Test
    fun `execute - not authorized`() {
        transaction {
            input { inState }
            output { outState }
            timestamp(TEST_TX_TIME_1)

            command(momAndPop.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input { inState }
            output { outStateWrong }
            timestamp(TEST_TX_TIME_1)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun move() {
        transaction {
            input { inState }

            tweak {
                output { outStateMove }
                command(acmeCorp.owningKey) {
                    UniversalContract.Commands.Move(acmeCorp, momAndPop)
                }
                this `fails with` "the transaction is signed by all liable parties"
            }

            tweak {
                output { inState }
                command(acmeCorp.owningKey, momAndPop.owningKey, highStreetBank.owningKey) {
                    UniversalContract.Commands.Move(acmeCorp, momAndPop)
                }
                this `fails with` "output state does not reflect move command"
            }

            output { outStateMove }

            command(acmeCorp.owningKey, momAndPop.owningKey, highStreetBank.owningKey) {
                UniversalContract.Commands.Move(acmeCorp, momAndPop)
            }
            this.verifies()
        }
    }

}
