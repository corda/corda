package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Frequency
import com.r3corda.core.contracts.Tenor
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.testing.transaction
import org.junit.Test
import java.time.Instant

/**
 * Created by sofusmortensen on 05/09/16.
 */

class Cap {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val notional = 50.M
    val currency = EUR

    val contract = arrange {
        rollOut("2016-04-01", "2017-04-01", Frequency.Quarterly) {
            (roadRunner or wileECoyote).may {
                "exercise".anytime {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("6M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                    wileECoyote.gives(roadRunner, floating - fixed, currency)
                    next()
                }
            } or roadRunner.may {
                "skip".anytime {
                    next()
                }
            }
        }
    }

    val stateStart = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contract)


    val contractTARN = arrange {
        rollOut("2016-04-01", "2017-04-01", Frequency.Quarterly, object {
            val limit = variable(150.K)
        }) {
            (roadRunner or wileECoyote).may {
                "exercise".anytime {
                    val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("6M")), start, end)
                    val fixed = interest(notional, "act/365", 0.5.bd, start, end)
                    val payout = (floating - fixed).plus()
                    wileECoyote.gives(roadRunner, payout, currency)
                    next(vars.limit to vars.limit - payout)
                }
            } or roadRunner.may {
                "skip".anytime {
                    next()
                }
            }
        }
    }

    @Test
    fun issue() {
        transaction {
            output { stateStart }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(roadRunner.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(wileECoyote.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

}