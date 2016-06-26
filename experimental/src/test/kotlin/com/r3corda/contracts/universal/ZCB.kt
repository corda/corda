package com.r3corda.contracts.universal

import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.transaction
import org.junit.Test

/**
 * Created by sofusmortensen on 01/06/16.
 */

class ZCB {

    val contract =
            (roadRunner or wileECoyote).may {
                "execute".givenThat(after("01/09/2017")) {
                    wileECoyote.gives(roadRunner, 100.K*GBP)
                }
            }


    val contractMove =
            (porkyPig or wileECoyote).may {
                "execute".givenThat(after("01/09/2017")) {
                    wileECoyote.gives(porkyPig, 100.K*GBP)
                }
            }

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

            this `fails requirement` "transaction has a single command"

            tweak {
                arg(roadRunner.owningKey) { UniversalContract.Commands.Issue() }
                this `fails requirement` "the transaction is signed by all liable parties"
            }

            arg(wileECoyote.owningKey) { UniversalContract.Commands.Issue() }

            this.accepts()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { inState }
            output { outState }

            tweak {
                arg(wileECoyote.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                this `fails requirement` "action must be defined"
            }

            arg(wileECoyote.owningKey) { UniversalContract.Commands.Action("execute") }

            this.accepts()
        }
    }

    @Test
    fun `execute - not authorized`() {
        transaction {
            input { inState }
            output { outState }

            arg(porkyPig.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails requirement` "action must be authorized"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input { inState }
            output { outStateWrong }

            arg(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails requirement` "output state must match action result state"
        }
    }

    @Test
    fun move() {
        transaction {
            input { inState }

            tweak {
                output { outStateMove }
                arg(roadRunner.owningKey) {
                    UniversalContract.Commands.Move(roadRunner, porkyPig)
                }
                this `fails requirement` "the transaction is signed by all liable parties"
            }

            tweak {
                output { inState }
                arg(roadRunner.owningKey, porkyPig.owningKey, wileECoyote.owningKey) {
                    UniversalContract.Commands.Move(roadRunner, porkyPig)
                }
                this `fails requirement` "output state does not reflect move command"
            }

            output { outStateMove}

            arg(roadRunner.owningKey, porkyPig.owningKey, wileECoyote.owningKey) {
                UniversalContract.Commands.Move(roadRunner, porkyPig)
            }
            this.accepts()
        }
    }

}