package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Frequency
import java.math.BigDecimal

/**
 * Created by sofusmortensen on 28/06/16.
 */

// Swaption



class Swaption {

    val notional = 10.M
    val currency = USD
    val coupon = BigDecimal.valueOf(1.5)

    val dreary_contract =
            (wileECoyote or roadRunner).may {
                "proceed".givenThat(after("01/07/2015")) {
                    wileECoyote.gives(roadRunner, libor( notional, "01/04/2015", "01/07/2015" ), currency )
                    roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon, "01/04/2015", "01/07/2015" ), currency )
                    (wileECoyote or roadRunner).may {
                        "proceed".givenThat(after("01/10/2015")) {
                            wileECoyote.gives(roadRunner, libor( notional, "01/07/2015", "01/10/2015" ), currency )
                            roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon,  "01/07/2015", "01/10/2015" ), currency )

                            (wileECoyote or roadRunner).may {
                                // etc ...
                            }
                        }
                    } or roadRunner.may {
                        "cancel".anytime {
                            roadRunner.gives( wileECoyote, 10.K, USD )
                        }
                    }
                }
            } or roadRunner.may {
                "cancel".anytime {
                    roadRunner.gives( wileECoyote, 10.K, USD )
                }
            }


    val elegant_contract = rollOut( "01/04/2015", "01/04/2025", Frequency.Quarterly ) {
        (wileECoyote or roadRunner).may {
            "proceed".givenThat(after(start)) {
                wileECoyote.gives(roadRunner, libor( notional, start, end ), currency )
                roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon,  start, end ), currency )
                next()
            }
        } or roadRunner.may {
            "cancel".anytime {
                roadRunner.gives( wileECoyote, 10.K, currency )
            }
        }
    }


    val strike = 1.2

    val tarf = rollOut( "01/04/2015", "01/04/2016", Frequency.Quarterly, object {
        val cap = variable( 150.K )
    }) {
        roadRunner.may {
            "exercise".givenThat(before(end)) {
                val payout = (EUR / USD - strike).plus() * notional

                (roadRunner or wileECoyote).may {
                    "proceed".givenThat(after(end)) {
                        wileECoyote.gives(roadRunner, payout, USD)
                        next(vars.cap to vars.cap - payout)
                    }
                }
            }
        } or (roadRunner or wileECoyote).may {
            "proceedWithoutExercise".givenThat(after(end)) {
                next()
            }
        }
    }

    val tarf2 = rollOut( "01/04/2015", "01/04/2016", Frequency.Quarterly, object {
        val uses = variable( 4 )
    }) {
        roadRunner.may {
            "exercise".givenThat(before(end)) {
                val payout = (EUR / USD - strike).plus() * notional

                (roadRunner or wileECoyote).may {
                    "proceed".givenThat(after(end)) {
                        wileECoyote.gives(roadRunner, payout, currency)
                        next(vars.uses to vars.uses - 1)
                    }
                }
            }
        } or (roadRunner or wileECoyote).may {
            "proceedWithoutExercise".givenThat(after(end)) {
                next()
            }
        }
    }
}