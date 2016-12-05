package net.corda.contracts.universal

import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
import org.junit.Test
import java.time.Instant

class FXSwap {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")
    val TEST_TX_TIME_TOO_EARLY: Instant get() = Instant.parse("2017-08-31T12:00:00.00Z")

    val contract = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1200.K, USD)
                    acmeCorp.owes(highStreetBank, 1.M, EUR)
                }
            }
        }
    }

    val transfer1 = arrange { highStreetBank.owes(acmeCorp, 1200.K, USD) }
    val transfer2 = arrange { acmeCorp.owes(highStreetBank, 1.M, EUR) }

    val outState1 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transfer1)
    val outState2 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transfer2)

    val transferBad1 = arrange { highStreetBank.owes(acmeCorp, 1200.K, GBP) } // wrong currency
    val transferBad2 = arrange { acmeCorp.owes(highStreetBank, 900.K, EUR) } // wrong amount
    val transferBad3 = arrange { highStreetBank.owes(highStreetBank, 1.M, EUR) } // wrong party

    val outStateBad1 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transferBad1)
    val outStateBad2 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transferBad2)
    val outStateBad3 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), transferBad3)

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contract)

    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }
            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(highStreetBank.owningKey, acmeCorp.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }
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
    fun `execute - reversed order`() {
        transaction {
            input { inState }
            output { outState2 }
            output { outState1 }
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
            output { outState1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_1)

            command(momAndPop.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "action must be authorized"
        }
    }

    @Test
    fun `execute - before maturity`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_TOO_EARLY)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - outState mismatch 1`() {
        transaction {
            input { inState }
            output { outState1 }
            timestamp(TEST_TX_TIME_1)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 2`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outStateBad2 }
            timestamp(TEST_TX_TIME_1)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 3`() {
        transaction {
            input { inState }
            output { outStateBad1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_1)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 4`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outStateBad3 }
            timestamp(TEST_TX_TIME_1)

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }
}
