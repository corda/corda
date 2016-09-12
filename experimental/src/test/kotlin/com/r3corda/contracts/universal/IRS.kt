package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Frequency
import com.r3corda.core.contracts.Tenor
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.testing.transaction
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Created by sofusmortensen on 12/09/16.
 */

class IRS {

    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val notional = 50.M
    val currency = EUR

    val tradeDate: LocalDate = LocalDate.of(2016, 9, 1)

    val contract = arrange {
        rollOut("2016-09-01".ld, "2018-09-01".ld, Frequency.Quarterly) {
            (acmeCorp or highStreetBank).may {
                val floating = interest(notional, "act/365", fix("LIBOR", start, Tenor("3M")), start, end)
                val fixed = interest(notional, "act/365", 0.5.bd, start, end)

                "pay 1".anytime {
                    highStreetBank.gives(acmeCorp, floating - fixed, currency)
                    next()
                }
                "pay 2".anytime {
                    highStreetBank.gives(acmeCorp, fixed - floating, currency)
                    next()
                }
            }
        }
    }


    val stateStart = UniversalContract.State( listOf(DUMMY_NOTARY.owningKey), contract)

    @Test
    fun issue() {
        transaction {
            output { stateStart }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

}