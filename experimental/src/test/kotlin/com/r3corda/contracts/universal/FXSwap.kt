package com.r3corda.contracts.universal

import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
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

    val outState1 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer1 )
    val outState2 = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), transfer2 )


    val inState = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract)

    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }

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

            command(porkyPig.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "action must be authorized"
        }
    }

    @Test
    fun `execute - outState mismatch`() {
        transaction {
            input { inState }
            output { outState1 }

            command(roadRunner.owningKey) { UniversalContract.Commands.Action("execute") }
            this `fails with` "output state must match action result state"
        }
    }
}