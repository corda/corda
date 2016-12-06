package net.corda.contracts.universal

import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
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

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), initialContract)
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
}