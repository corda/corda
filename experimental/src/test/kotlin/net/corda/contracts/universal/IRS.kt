package net.corda.contracts.universal

import net.corda.core.contracts.FixOf
import net.corda.core.contracts.Frequency
import net.corda.core.contracts.Tenor
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class IRS {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val notional = 50.M
    val currency = EUR

    val tradeDate: LocalDate = LocalDate.of(2016, 9, 1)


    /*

    (roll-out "2016-09-01" "2018-09-01" Quarterly
      (actions
        (action "pay floating" (and (obligation highStreetBank acmeCorp) (next)))
        (action "pay fixed" (and (obligation highStreetBank acmeCorp) (next)))
      )
    )

     */

    val contractInitial = arrange {
        rollOut("2016-09-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
            actions {
                (acmeCorp or highStreetBank).may {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                    "pay floating".anytime {
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                    "pay fixed".anytime {
                        highStreetBank.owes(acmeCorp, fixed - floating, currency)
                        next()
                    }
                }
            }
        }
    }
    val contractAfterFixingFirst = arrange {
        actions {
            (acmeCorp or highStreetBank).may {
                val floating = interest(notional, "act/365", 1.0.bd, "2016-09-01", "2016-12-01")
                val fixed = interest(notional, "act/365", 0.5.bd, "2016-09-01", "2016-12-01")

                "pay floating".anytime {
                    highStreetBank.owes(acmeCorp, floating - fixed, currency)
                    rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                val nextFloating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                val nextFixed = interest(notional, "act/365", 0.5.bd, start, end)

                                "pay floating".anytime {
                                    highStreetBank.owes(acmeCorp, nextFloating - nextFixed, currency)
                                    next()
                                }
                                "pay fixed".anytime {
                                    highStreetBank.owes(acmeCorp, nextFixed - nextFloating, currency)
                                    next()
                                }
                            }
                        }
                    }
                }
                "pay fixed".anytime {
                    highStreetBank.owes(acmeCorp, fixed - floating, currency)
                    rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                                "pay floating".anytime {
                                    highStreetBank.owes(acmeCorp, floating - fixed, currency)
                                    next()
                                }
                                "pay fixed".anytime {
                                    highStreetBank.owes(acmeCorp, fixed - floating, currency)
                                    next()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val contractAfterExecutionFirst = arrange {
        rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
            actions {
                (acmeCorp or highStreetBank).may {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                    "pay floating".anytime {
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                    "pay fixed".anytime {
                        highStreetBank.owes(acmeCorp, fixed - floating, currency)
                        next()
                    }
                }
            }
        }
    }

    val paymentFirst = arrange { highStreetBank.owes(acmeCorp, 250.K, EUR) }

    val stateInitial = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractInitial)

    val stateAfterFixingFirst = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractAfterFixingFirst)
    val stateAfterExecutionFirst = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractAfterExecutionFirst)

    val statePaymentFirst = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), paymentFirst)


    @Test
    fun issue() {
        transaction {
            output { stateInitial }
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
    fun `first fixing`() {
        transaction {
            input { stateInitial }
            output { stateAfterFixingFirst }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            tweak {
                // wrong source
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(net.corda.core.contracts.Fix(FixOf("LIBORx", tradeDate, Tenor("3M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong date
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(net.corda.core.contracts.Fix(FixOf("LIBOR", tradeDate.plusYears(1), Tenor("3M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong tenor
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(net.corda.core.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("9M")), 1.0.bd))) }

                this `fails with` "relevant fixing must be included"
            }

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(net.corda.core.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("3M")), 1.5.bd))) }

                this `fails with` "output state does not reflect fix command"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Fix(listOf(net.corda.core.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("3M")), 1.0.bd))) }

            this.verifies()
        }
    }

    @Test
    fun `first execute`() {
        transaction {
            input { stateAfterFixingFirst }
            output { stateAfterExecutionFirst }
            output { statePaymentFirst }

            timestamp(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Action("pay floating") }

            this.verifies()
        }
    }


}
