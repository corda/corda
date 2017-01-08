package net.corda.contracts.universal

import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
import org.junit.Ignore
import org.junit.Test
import java.time.Instant

class FXFwdTimeOption
{
    // An FX Fwd with Time Option is an early exercise call option that must be exercised no later than maturity
    val initialContract = arrange {

        val swap = arrange {
            highStreetBank.owes(acmeCorp, 1070.K, EUR)
            acmeCorp.owes(highStreetBank, 1.M, USD)
        }
        val maturity = "2018-06-01".ld

        actions {
            acmeCorp may {
                "exercise".givenThat(before(maturity)) {
                    +swap // problem, swap (wo unary plus) also compiles, but with no effect.
                          // hopefully this can be solved using @DslMarker in Kotlin 1.1
                }
            }
            highStreetBank may {
                "expire".givenThat(after(maturity)) {
                    +swap
                }
            }
        }
    }

    val outContract1 = arrange {
        highStreetBank.owes(acmeCorp, 1070.K, EUR)
    }
    val outContract2 = arrange {
        acmeCorp.owes(highStreetBank, 1.M, USD)
    }

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")
    val TEST_TX_TIME_BEFORE_MATURITY: Instant get() = Instant.parse("2018-05-01T12:00:00.00Z")
    val TEST_TX_TIME_AFTER_MATURITY: Instant get() = Instant.parse("2018-06-02T12:00:00.00Z")

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), initialContract)
    val outState1 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), outContract1)
    val outState2 = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), outContract2)

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
    fun `maturity, bank exercise`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }

            timestamp(TEST_TX_TIME_AFTER_MATURITY)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }
            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("exercise") }
                this `fails with` "condition must be met"
            }
            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Action("exercise") }
                this `fails with` "condition must be met"
            }
            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Action("expire") }
                this `fails with` "condition must be met"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Action("expire") }

            this.verifies()
        }
    }

    @Test
    fun `maturity, corp exercise`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }

            timestamp(TEST_TX_TIME_BEFORE_MATURITY)

            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }
            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Action("expire") }
                this `fails with` "condition must be met"
            }
            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("expire") }
                this `fails with` "condition must be met"
            }
            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("exercise") }
                this `fails with` "condition must be met"
            }

            command(acmeCorp.owningKey) { UniversalContract.Commands.Action("exercise") }

            this.verifies()
        }
    }

    @Test @Ignore
    fun `pretty print`() {
        println ( prettyPrint(initialContract) )

        println ( prettyPrint(outContract1) )

        println ( prettyPrint(outContract2) )
    }

}