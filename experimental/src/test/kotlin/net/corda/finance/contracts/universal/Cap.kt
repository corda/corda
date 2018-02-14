package net.corda.finance.contracts.universal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.finance.contracts.BusinessCalendar
import net.corda.finance.contracts.FixOf
import net.corda.finance.contracts.Frequency
import net.corda.finance.contracts.Tenor
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

internal val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) = run {
    MockServices(listOf("net.corda.finance.contracts.universal"), CordaX500Name("MegaCorp", "London", "GB"),
            rigorousMock<IdentityServiceInternal>().also {
                listOf(acmeCorp, highStreetBank, momAndPop).forEach { party ->
                    doReturn(null).whenever(it).partyFromKey(party.owningKey)
                }
            }).transaction(DUMMY_NOTARY, script)
}

class Cap {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val notional = 50.M
    val currency = EUR

    val tradeDate: LocalDate = LocalDate.of(2016, 9, 1)

    val contractInitial = arrange {
        rollOut("2016-09-01".ld, "2017-09-01".ld, Frequency.SemiAnnual) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "exercise" anytime {
                        val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                        val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                }
                acmeCorp may {
                    "skip" anytime {
                        next()
                    }
                }
            }
        }
    }

    val contractAfterFixingFirst = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "exercise" anytime {
                    val floating1 = interest(notional, "act/365", 1.0.bd, "2016-09-01", "2017-03-01")
                    val fixed1 = interest(notional, "act/365", 0.5.bd, "2016-09-01", "2017-03-01")
                    highStreetBank.owes(acmeCorp, floating1 - fixed1, currency)
                    rollOut("2017-03-01".ld, "2017-09-01".ld, Frequency.SemiAnnual) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                "exercise" anytime {
                                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                                    highStreetBank.owes(acmeCorp, floating - fixed, currency)
                                    next()
                                }
                            }
                            acmeCorp may {
                                "skip" anytime {
                                    next()
                                }
                            }
                        }
                    }
                }
            }
            acmeCorp may {
                "skip" anytime {
                    rollOut("2017-03-01".ld, "2017-09-01".ld, Frequency.SemiAnnual) {
                        actions {
                            (acmeCorp or highStreetBank) may {
                                "exercise" anytime {
                                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                                    highStreetBank.owes(acmeCorp, floating - fixed, currency)
                                    next()
                                }
                            }
                            acmeCorp may {
                                "skip" anytime {
                                    next()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val contractAfterFixingFinal = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "exercise" anytime {
                    val floating1 = interest(notional, "act/365", 1.0.bd, "2017-03-01", "2017-09-01")
                    val fixed1 = interest(notional, "act/365", 0.5.bd, "2017-03-01", "2017-09-01")
                    highStreetBank.owes(acmeCorp, floating1 - fixed1, currency)
                }
            }
            acmeCorp may {
                "skip" anytime {
                }
            }
        }
    }

    val contractAfterExecutionFirst = arrange {
        rollOut("2017-03-01".ld, "2017-09-01".ld, Frequency.SemiAnnual) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "exercise" anytime {
                        val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                        val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                        highStreetBank.owes(acmeCorp, floating - fixed, currency)
                        next()
                    }
                }
                acmeCorp may {
                    "skip" anytime {
                        next()
                    }
                }
            }
        }
    }

    val paymentFirst = arrange { highStreetBank.owes(acmeCorp, 250.K, EUR) }
    val paymentFinal = arrange { highStreetBank.owes(acmeCorp, 250.K, EUR) }


    val stateInitial = UniversalContract.State(listOf(DUMMY_NOTARY), contractInitial)

    val stateAfterFixingFirst = UniversalContract.State(listOf(DUMMY_NOTARY), contractAfterFixingFirst)

    val stateAfterExecutionFirst = UniversalContract.State(listOf(DUMMY_NOTARY), contractAfterExecutionFirst)
    val statePaymentFirst = UniversalContract.State(listOf(DUMMY_NOTARY), paymentFirst)

    val stateAfterFixingFinal = UniversalContract.State(listOf(DUMMY_NOTARY), contractAfterFixingFinal)
    val statePaymentFinal = UniversalContract.State(listOf(DUMMY_NOTARY), paymentFinal)

    val contractLimitedCap = arrange {
        rollOut("2016-04-01".ld, "2017-04-01".ld, Frequency.SemiAnnual, object {
            val limit = variable(150.K)
        }) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "exercise" anytime {
                        val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                        val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                        val payout = min(floating - fixed)
                        highStreetBank.owes(acmeCorp, payout, currency)
                        next(vars.limit to vars.limit - payout)
                    }
                }
                acmeCorp may {
                    "skip" anytime {
                        next()
                    }
                }
            }
        }
    }

    @Test
    fun issue() {
        transaction {
            output(UNIVERSAL_PROGRAM_ID, stateInitial)
            timeWindow(TEST_TX_TIME_1)

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
            timeWindow(TEST_TX_TIME_1)

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
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("exercise"))
            this.verifies()
        }
    }

    @Test
    fun `final execute`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, stateAfterFixingFinal)
            output(UNIVERSAL_PROGRAM_ID, statePaymentFinal)
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("exercise"))
            this.verifies()
        }
    }

    @Test
    fun `second fixing`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, stateAfterExecutionFirst)
            output(UNIVERSAL_PROGRAM_ID, stateAfterFixingFinal)
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }

            tweak {
                // wrong source
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBORx", BusinessCalendar.parseDateFromString("2017-03-01"), Tenor("3M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong date
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", BusinessCalendar.parseDateFromString("2017-03-01").plusYears(1), Tenor("3M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                // wrong tenor
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", BusinessCalendar.parseDateFromString("2017-03-01"), Tenor("9M")), 1.0.bd))))
                this `fails with` "relevant fixing must be included"
            }

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", BusinessCalendar.parseDateFromString("2017-03-01"), Tenor("3M")), 1.5.bd))))
                this `fails with` "output state does not reflect fix command"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Fix(listOf(net.corda.finance.contracts.Fix(FixOf("LIBOR", BusinessCalendar.parseDateFromString("2017-03-01"), Tenor("3M")), 1.0.bd))))
            this.verifies()
        }
    }

    @Test
    @Ignore
    fun `pretty print`() {
        println(prettyPrint(contractInitial))

        println(prettyPrint(contractAfterFixingFirst))

        println(prettyPrint(contractAfterExecutionFirst))

        println(prettyPrint(contractAfterFixingFinal))
    }
}
