/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.universal

import net.corda.finance.contracts.Frequency
import net.corda.finance.contracts.Tenor
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Swaption {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val notional = 50.M
    val currency = EUR

    val tradeDate: LocalDate = LocalDate.of(2016, 9, 1)

    val contractInitial = arrange {

        actions {

            acmeCorp may {
                "exercise" anytime {
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
            }

            highStreetBank may {
                "expire".givenThat(after("2016-09-01"))
                {
                    zero
                }
            }
        }

    }

    val stateInitial = UniversalContract.State(listOf(DUMMY_NOTARY), contractInitial)
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

    @Test @Ignore
    fun `pretty print`() {
        println ( prettyPrint(contractInitial) )
    }


}
