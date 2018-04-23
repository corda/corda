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

import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FXFwdTimeOption {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
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

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY), initialContract)
    val outState1 = UniversalContract.State(listOf(DUMMY_NOTARY), outContract1)
    val outState2 = UniversalContract.State(listOf(DUMMY_NOTARY), outContract2)
    @Test
    fun `issue - signature`() {
        transaction {
            output(UNIVERSAL_PROGRAM_ID, inState)
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Issue())
                this `fails with` "the transaction is signed by all liable parties"
            }
            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Issue())
                this `fails with` "the transaction is signed by all liable parties"
            }
            command(listOf(highStreetBank.owningKey, acmeCorp.owningKey), UniversalContract.Commands.Issue())
            this.verifies()
        }
    }

    @Test
    fun `maturity, bank exercise`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_AFTER_MATURITY)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("exercise"))
                this `fails with` "condition must be met"
            }
            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Action("exercise"))
                this `fails with` "condition must be met"
            }
            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Action("expire"))
                this `fails with` "condition must be met"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("expire"))
            this.verifies()
        }
    }

    @Test
    fun `maturity, corp exercise`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_BEFORE_MATURITY)

            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Action("expire"))
                this `fails with` "condition must be met"
            }
            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("expire"))
                this `fails with` "condition must be met"
            }
            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("exercise"))
                this `fails with` "condition must be met"
            }
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("exercise"))
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