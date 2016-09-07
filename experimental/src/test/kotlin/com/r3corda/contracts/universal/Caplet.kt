package com.r3corda.contracts.universal

import com.r3corda.core.contracts.FixOf
import com.r3corda.core.contracts.Tenor
import com.r3corda.testing.*
import com.r3corda.core.utilities.DUMMY_NOTARY
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Created by sofusmortensen on 25/08/16.
 */

class Caplet {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val dt: LocalDate = LocalDate.of(2016, 9, 1)

    val notional = 50.M
    val currency = EUR

    val contract = arrange {
        (acmeCorp or highStreetBank).may {
            "exercise".anytime() {
                val floating = interest(notional, "act/365", fix("LIBOR", dt, Tenor("6M")), "2016-04-01", "2016-10-01")
                val fixed = interest(notional, "act/365", 0.5.bd, "2016-04-01", "2016-10-01")
                highStreetBank.gives(acmeCorp, (floating - fixed).plus(), currency)
            }
        }
    }

    val contractFixed = arrange {
        (acmeCorp or highStreetBank).may {
            "exercise".anytime() {
                val floating = interest(notional, "act/365", 1.0.bd, "2016-04-01", "2016-10-01")
                val fixed = interest(notional, "act/365", 0.5.bd, "2016-04-01", "2016-10-01")
                highStreetBank.gives(acmeCorp, (floating - fixed).plus(), currency)
            }
        }
    }

    val contractFinal = arrange { highStreetBank.gives(acmeCorp, 250.K, EUR) }

    val stateStart = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract)

    val stateFixed = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contractFixed)

    val stateFinal = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contractFinal )

    @Test
    fun issue() {
        transaction {
            output { stateStart }
            timestamp(TEST_TX_TIME_1)

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
            input { stateFixed }
            output { stateFinal }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Action("exercise") }

            this.verifies()
        }
    }

    @Test
    fun `fixing`() {
        transaction {
            input { stateStart }
            output { stateFixed }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            tweak {
                // wrong source
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBORx", dt, Tenor("6M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong date
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt.plusYears(1), Tenor("6M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong tenor
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt, Tenor("3M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt, Tenor("6M")), 1.5.bd))) }

                this `fails with` "output state does not reflect fix command"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt, Tenor("6M")), 1.0.bd))) }

            this.verifies()
        }
    }

}