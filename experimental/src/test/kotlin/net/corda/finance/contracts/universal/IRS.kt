package net.corda.finance.contracts.universal

import net.corda.finance.contracts.FixOf
import net.corda.finance.contracts.Frequency
import net.corda.finance.contracts.Tenor
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class IRS {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val testTxTime1: Instant = Instant.parse("2017-09-02T12:00:00.00Z")
    private val notional = 50.M
    private val currency = EUR

    private val tradeDate: LocalDate = LocalDate.of(2016, 9, 1)

    /*

    (roll-out "2016-09-01" "2018-09-01" Quarterly
      (actions
        (action "pay floating" (and (obligation highStreetBank acmeCorp) (next)))
        (action "pay fixed" (and (obligation highStreetBank acmeCorp) (next)))
      )
    )

     */

    private val contractInitial = arrange {
        rollOut("2016-09-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
            actions {
                (acmeCorp or highStreetBank) may {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                    "pay floating" anytime {
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                    "pay fixed" anytime {
                        highStreetBank.owes(acmeCorp, fixed - floating, currency)
                        next()
                    }
                }
            }
        }
    }

    private val contractAfterFixingFirst = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                val floating = interest(notional, "act/365", 1.0.bd, "2016-09-01", "2016-12-01")
                val fixed = interest(notional, "act/365", 0.5.bd, "2016-09-01", "2016-12-01")

                "pay floating" anytime {
                    highStreetBank.owes(acmeCorp, floating - fixed, currency)
                    rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                val nextFloating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                val nextFixed = interest(notional, "act/365", 0.5.bd, start, end)

                                "pay floating" anytime {
                                    highStreetBank.owes(acmeCorp, nextFloating - nextFixed, currency)
                                    next()
                                }
                                "pay fixed" anytime {
                                    highStreetBank.owes(acmeCorp, nextFixed - nextFloating, currency)
                                    next()
                                }
                            }
                        }
                    }
                }
                "pay fixed" anytime {
                    highStreetBank.owes(acmeCorp, fixed - floating, currency)
                    rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                val nextFloating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                val nextFixed = interest(notional, "act/365", 0.5.bd, start, end)

                                "pay floating" anytime {
                                    highStreetBank.owes(acmeCorp, nextFloating - nextFixed, currency)
                                    next()
                                }
                                "pay fixed" anytime {
                                    highStreetBank.owes(acmeCorp, nextFixed - nextFloating, currency)
                                    next()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val contractAfterExecutionFirst = arrange {
        rollOut("2016-12-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
            actions {
                (acmeCorp or highStreetBank) may {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                    "pay floating" anytime {
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                    "pay fixed" anytime {
                        highStreetBank.owes(acmeCorp, fixed - floating, currency)
                        next()
                    }
                }
            }
        }
    }

    private val paymentFirst = arrange { highStreetBank.owes(acmeCorp, 250.K, EUR) }

    private val stateInitial = UniversalContract.State(listOf(DUMMY_NOTARY), contractInitial)

    private val stateAfterFixingFirst = UniversalContract.State(listOf(DUMMY_NOTARY), contractAfterFixingFirst)
    private val stateAfterExecutionFirst = UniversalContract.State(listOf(DUMMY_NOTARY), contractAfterExecutionFirst)

    private val statePaymentFirst = UniversalContract.State(listOf(DUMMY_NOTARY), paymentFirst)

    @Test
    fun issue() {
        transaction {
            output(UNIVERSAL_PROGRAM_ID, stateInitial)
            timeWindow(testTxTime1)

            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Issue())
                this `fails with` "the transaction is signed by all liable parties"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Issue())
            this.verifies()
        }
    }

    @Test
    fun `first fixing`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, stateInitial)
            output(UNIVERSAL_PROGRAM_ID, stateAfterFixingFirst)
            timeWindow(testTxTime1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }

            tweak {
                // wrong source
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBORx", tradeDate, Tenor("3M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong date
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", tradeDate.plusYears(1), Tenor("3M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong tenor
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("9M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("3M")), 1.5.bd))))
                this `fails with` "output state does not reflect fix command"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", tradeDate, Tenor("3M")), 1.0.bd))))
            this.verifies()
        }
    }

    @Test
    fun `first execute`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, stateAfterFixingFirst)
            output(UNIVERSAL_PROGRAM_ID, stateAfterExecutionFirst)
            output(UNIVERSAL_PROGRAM_ID, statePaymentFirst)
            timeWindow(testTxTime1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("pay floating"))
            this.verifies()
        }
    }

    @Test @Ignore
    fun `pretty print`() {
        println ( prettyPrint(contractInitial) )

        println ( prettyPrint(contractAfterFixingFirst) )

        println ( prettyPrint(contractAfterExecutionFirst) )
    }

}
