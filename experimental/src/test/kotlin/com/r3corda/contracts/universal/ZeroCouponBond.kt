package com.r3corda.contracts.universal

import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.TEST_TX_TIME
import com.r3corda.core.testing.transaction
import org.junit.Test
import java.time.Instant

/**
 * Created by sofusmortensen on 01/06/16.
 */

class ZeroCouponBond {

    val contract =
            (roadRunner or wileECoyote).may {
                "execute".givenThat(after("2017-09-01")) {
                    wileECoyote.gives(roadRunner, 100.K*GBP)
                }
            }


    val contractMove =
            (porkyPig or wileECoyote).may {
                "execute".givenThat(after("2017-09-01")) {
                    wileECoyote.gives(porkyPig, 100.K*GBP)
                }
            }

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val transfer = arrange { wileECoyote.gives(roadRunner, 100.K*GBP) }
    val transferWrong = arrange { wileECoyote.gives(roadRunner, 80.K*GBP) }

    val inState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract )

    val outState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer )
    val outStateWrong = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transferWrong )

    val outStateMove = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contractMove )

    @Test
    fun basic() {
        assert( Zero().equals(Zero()))
    }


    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }

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
    fun `execute`() {
        transaction {
            input { inState }
            output { outState }
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
            output { outState }
            timestamp(TEST_TX_TIME_1)

            command(porkyPig.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "action must be authorized"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input { inState }
            output { outStateWrong }
            timestamp(TEST_TX_TIME_1)

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output state must match action result state"
        }
    }

    @Test
    fun move() {
        transaction {
            input { inState }

            tweak {
                output { outStateMove }
                command(roadRunner.owningKey) {
                    UniversalContract.Commands.Move(roadRunner, porkyPig)
                }
                this `fails with` "the transaction is signed by all liable parties"
            }

            tweak {
                output { inState }
                command(roadRunner.owningKey, porkyPig.owningKey, wileECoyote.owningKey) {
                    UniversalContract.Commands.Move(roadRunner, porkyPig)
                }
                this `fails with` "output state does not reflect move command"
            }

            output { outStateMove}

            command(roadRunner.owningKey, porkyPig.owningKey, wileECoyote.owningKey) {
                UniversalContract.Commands.Move(roadRunner, porkyPig)
            }
            this.verifies()
        }
    }

}