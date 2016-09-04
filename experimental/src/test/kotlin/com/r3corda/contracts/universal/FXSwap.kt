package com.r3corda.contracts.universal

import com.r3corda.testing.*
import com.r3corda.core.utilities.DUMMY_NOTARY
import org.junit.Test
import java.time.Instant

/**
 * Created by sofusmortensen on 01/06/16.
 */

class FXSwap {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")
    val TEST_TX_TIME_TOO_EARLY: Instant get() = Instant.parse("2017-08-31T12:00:00.00Z")

    val contract =
            (roadRunner or wileECoyote).may {
                "execute".givenThat(after("2017-09-01")) {
                    wileECoyote.gives(roadRunner, 1200.K, USD)
                    roadRunner.gives(wileECoyote, 1.M, EUR)
                }
            }

    val transfer1 = arrange { wileECoyote.gives(roadRunner, 1200.K, USD) }
    val transfer2 = arrange { roadRunner.gives(wileECoyote, 1.M, EUR) }

    val outState1 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer1 )
    val outState2 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer2 )

    val transferBad1 = arrange { wileECoyote.gives(roadRunner, 1200.K, GBP) } // wrong currency
    val transferBad2 = arrange { roadRunner.gives(wileECoyote, 900.K, EUR) } // wrong amount
    val transferBad3 = arrange { wileECoyote.gives(wileECoyote, 1.M, EUR) } // wrong party

    val outStateBad1 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transferBad1 )
    val outStateBad2 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transferBad2 )
    val outStateBad3 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transferBad3 )

    val inState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract)

    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(roadRunner.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }
            tweak {
                command(wileECoyote.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(wileECoyote.owningKey, roadRunner.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(wileECoyote.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Action("execute") }

            this.verifies()
        }
    }

    @Test
    fun `execute - reversed order`() {
        transaction {
            input { inState }
            output { outState2 }
            output { outState1 }
            timestamp(TEST_TX_TIME_1)

            tweak {
                command(wileECoyote.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails with` "action must be defined"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Action("execute") }

            this.verifies()
        }
    }

    @Test
    fun `execute - not authorized`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_1)

            command(porkyPig.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "action must be authorized"
        }
    }

    @Test
    fun `execute - before maturity`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_TOO_EARLY)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "condition must be met"
        }
    }

    @Test
    fun `execute - outState mismatch 1`() {
        transaction {
            input { inState }
            output { outState1 }
            timestamp(TEST_TX_TIME_1)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 2`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outStateBad2 }
            timestamp(TEST_TX_TIME_1)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 3`() {
        transaction {
            input { inState }
            output { outStateBad1 }
            output { outState2 }
            timestamp(TEST_TX_TIME_1)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }

    @Test
    fun `execute - outState mismatch 4`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outStateBad3 }
            timestamp(TEST_TX_TIME_1)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output states must match action result state"
        }
    }
}