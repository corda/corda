package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Fix
import com.r3corda.core.contracts.FixOf
import com.r3corda.core.contracts.Tenor
import com.r3corda.testing.*
import com.r3corda.core.utilities.DUMMY_NOTARY
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Created by sofusmortensen on 25/08/16.
 */

class Caplet {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val dt = LocalDate.of(2016, 9, 1)

    val fx = Fix(FixOf("LIBOR", dt, Tenor("6M")), BigDecimal.valueOf(0.31207))

    val notional = 50.M*EUR

    val contract =
            (roadRunner or wileECoyote).may {
                "exercise".anytime() {
                    val floating = interest(notional, "act/365", fix("LIBOR", dt, Tenor("6M")), "2016-04-01", "2016-10-01" )
                    val fixed = interest(notional, "act/365", BigDecimal.valueOf(1.0), "2016-04-01", "2016-10-01")
                    wileECoyote.gives(roadRunner, (floating - fixed).plus() )
                }
            }

    val contractFixed =
            (roadRunner or wileECoyote).may {
                "exercise".anytime() {
                    val floating = interest(notional, "act/365", BigDecimal.valueOf(.01), "2016-04-01", "2016-10-01" )
                    val fixed = interest(notional, "act/365", BigDecimal.valueOf(1.0), "2016-04-01", "2016-10-01")
                    wileECoyote.gives(roadRunner, (floating - fixed).plus() )
                }
            }

    val inState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract)

    val outStateFixed = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contractFixed)

    val transfer = arrange { wileECoyote.gives(roadRunner, 100.K*EUR )}
    val outState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer )

    @Test
    fun issue() {
        transaction {
            output { inState }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(roadRunner.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

    @Test
    fun `execute - missing fixing `() {
        transaction {
            input { inState }
            output { outState }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(wileECoyote.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Action("exercise") }

            this `fails with` "fixing must be included"
        }
    }

    @Test
    fun `fixing`() {
        transaction {
            input { inState }
            output { outStateFixed }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(wileECoyote.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            tweak {
                // wrong source
                command(wileECoyote.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBORx", dt, Tenor("6M")), BigDecimal.valueOf(.01)))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong date
                command(wileECoyote.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt.plusYears(1), Tenor("6M")), BigDecimal.valueOf(.01)))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong tenor
                command(wileECoyote.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt, Tenor("3M")), BigDecimal.valueOf(.01)))) }

                this `fails with` "relevant fixing must be included"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Fix(listOf(com.r3corda.core.contracts.Fix(FixOf("LIBOR", dt, Tenor("6M")), BigDecimal.valueOf(.01)))) }

            this.verifies()
        }
    }

}