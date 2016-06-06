package com.r3corda.contracts.generic

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

    val inState = GenericContract.State( DUMMY_NOTARY, contract)

    @Test
    fun `issue - signature`() {

        transaction {
            output { inState }

            this `fails requirement` "transaction has a single command"

            tweak {
                arg(roadRunner.owningKey) { GenericContract.Commands.Issue() }
                this `fails requirement` "the transaction is signed by all involved parties"
            }
            tweak {
                arg(wileECoyote.owningKey) { GenericContract.Commands.Issue() }
                this `fails requirement` "the transaction is signed by all involved parties"
            }

            arg(wileECoyote.owningKey, roadRunner.owningKey) { GenericContract.Commands.Issue() }

            this.accepts()
        }
    }

}