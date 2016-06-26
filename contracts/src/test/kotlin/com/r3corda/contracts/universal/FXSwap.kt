package com.r3corda.contracts.universal

import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.transaction
import org.junit.Test

/**
 * Created by sofusmortensen on 01/06/16.
 */

class FXSwap {

    val contract =
            (roadRunner or wileECoyote).may {
                "execute".givenThat(after("01/09/2017")) {
                    wileECoyote.gives(roadRunner, 1200.K*USD)
                    roadRunner.gives(wileECoyote, 1.M*EUR)
                }
            }

    val transfer1 = arrange { wileECoyote.gives(roadRunner, 1200.K*USD) }
    val transfer2 = arrange { roadRunner.gives(wileECoyote, 1.M*EUR) }

    val outState1 = UniversalContract.State( DUMMY_NOTARY, transfer1 )
    val outState2 = UniversalContract.State( DUMMY_NOTARY, transfer2 )


    val inState = UniversalContract.State( DUMMY_NOTARY, contract)

    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }

            this `fails requirement` "transaction has a single command"

            tweak {
                arg(roadRunner.owningKey) { UniversalContract.Commands.Issue() }
                this `fails requirement` "the transaction is signed by all liable parties"
            }
            tweak {
                arg(wileECoyote.owningKey) { UniversalContract.Commands.Issue() }
                this `fails requirement` "the transaction is signed by all liable parties"
            }

            arg(wileECoyote.owningKey, roadRunner.owningKey) { UniversalContract.Commands.Issue() }

            this.accepts()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { inState }
            output { outState1 }
            output { outState2 }

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
            output { outState1 }
            output { outState2 }

            arg(porkyPig.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails requirement` "action must be authorized"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input { inState }
            output { outState1 }

            arg(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails requirement` "output state must match action result state"
        }
    }
}