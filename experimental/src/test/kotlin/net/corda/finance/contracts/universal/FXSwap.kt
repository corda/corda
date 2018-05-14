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

class FXSwap {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")
    val TEST_TX_TIME_TOO_EARLY: Instant get() = Instant.parse("2017-08-31T12:00:00.00Z")

    val contract = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1070.K, EUR)
                    acmeCorp.owes(highStreetBank, 1.M, USD)
                }
            }
        }
    }

    val transfer1 = arrange { highStreetBank.owes(acmeCorp, 1070.K, EUR) }
    val transfer2 = arrange { acmeCorp.owes(highStreetBank, 1.M, USD) }

    val outState1 = UniversalContract.State(listOf(DUMMY_NOTARY), transfer1)
    val outState2 = UniversalContract.State(listOf(DUMMY_NOTARY), transfer2)

    val transferBad1 = arrange { highStreetBank.owes(acmeCorp, 1070.K, USD) } // wrong currency
    val transferBad2 = arrange { acmeCorp.owes(highStreetBank, 900.K, USD) } // wrong amount
    val transferBad3 = arrange { highStreetBank.owes(highStreetBank, 1070.K, EUR) } // wrong party

    val outStateBad1 = UniversalContract.State(listOf(DUMMY_NOTARY), transferBad1)
    val outStateBad2 = UniversalContract.State(listOf(DUMMY_NOTARY), transferBad2)
    val outStateBad3 = UniversalContract.State(listOf(DUMMY_NOTARY), transferBad3)

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY), contract)
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
    fun execute() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("execute"))
            this.verifies()
        }
    }

    @Test
    fun `execute - reversed order`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            timeWindow(TEST_TX_TIME_1)

            tweak {
                command(highStreetBank.owningKey, UniversalContract.Commands.Action("some undefined name"))
                this `fails with` "action must be defined"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Action("execute"))
            this.verifies()
        }
    }

    @Test
    fun `execute - not authorized`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_1)
            command(momAndPop.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - before maturity`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_TOO_EARLY)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - outState mismatch 1`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            timeWindow(TEST_TX_TIME_1)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 2`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outStateBad2)
            timeWindow(TEST_TX_TIME_1)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 3`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outStateBad1)
            output(UNIVERSAL_PROGRAM_ID, outState2)
            timeWindow(TEST_TX_TIME_1)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 4`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState1)
            output(UNIVERSAL_PROGRAM_ID, outStateBad3)
            timeWindow(TEST_TX_TIME_1)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "output states must match action result state"
        }
    }

    @Test @Ignore
    fun `pretty print`() {
        println ( prettyPrint(contract) )
    }

}
