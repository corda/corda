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
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class ZeroCouponBond {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    val contract = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 100.K, GBP)
                }
            }
        }
    }

    val contractMove = arrange {
        actions {
            (momAndPop or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(momAndPop, 100.K, GBP)
                }
            }
        }
    }

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val transfer = arrange { highStreetBank.owes(acmeCorp, 100.K, GBP) }
    val transferWrong = arrange { highStreetBank.owes(acmeCorp, 80.K, GBP) }

    val inState = UniversalContract.State(listOf(DUMMY_NOTARY), contract)

    val outState = UniversalContract.State(listOf(DUMMY_NOTARY), transfer)
    val outStateWrong = UniversalContract.State(listOf(DUMMY_NOTARY), transferWrong)

    val outStateMove = UniversalContract.State(listOf(DUMMY_NOTARY), contractMove)
    @Test
    fun basic() {
        assertEquals(Zero(), Zero())
    }


    @Test
    fun `issue - signature`() {
        transaction {
            output(UNIVERSAL_PROGRAM_ID, inState)
            tweak {
                command(acmeCorp.owningKey, UniversalContract.Commands.Issue())
                this `fails with` "the transaction is signed by all liable parties"
            }
            command(highStreetBank.owningKey, UniversalContract.Commands.Issue())
            this.verifies()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outState)
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
            output(UNIVERSAL_PROGRAM_ID, outState)
            timeWindow(TEST_TX_TIME_1)
            command(momAndPop.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            output(UNIVERSAL_PROGRAM_ID, outStateWrong)
            timeWindow(TEST_TX_TIME_1)
            command(acmeCorp.owningKey, UniversalContract.Commands.Action("execute"))
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun move() {
        transaction {
            input(UNIVERSAL_PROGRAM_ID, inState)
            tweak {
                output(UNIVERSAL_PROGRAM_ID, outStateMove)
                command(acmeCorp.owningKey,
                    UniversalContract.Commands.Move(acmeCorp, momAndPop))
                this `fails with` "the transaction is signed by all liable parties"
            }

            tweak {
                output(UNIVERSAL_PROGRAM_ID, inState)
                command(listOf(acmeCorp.owningKey, momAndPop.owningKey, highStreetBank.owningKey),
                    UniversalContract.Commands.Move(acmeCorp, momAndPop))
                this `fails with` "output state does not reflect move command"
            }
            output(UNIVERSAL_PROGRAM_ID, outStateMove)
            command(listOf(acmeCorp.owningKey, momAndPop.owningKey, highStreetBank.owningKey),
                UniversalContract.Commands.Move(acmeCorp, momAndPop))
            this.verifies()
        }
    }

}
