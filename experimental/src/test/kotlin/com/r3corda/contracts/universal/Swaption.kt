package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Frequency

/**
 * Created by sofusmortensen on 28/06/16.
 */

// Swaption



class Swaption {

    val notional = 10.M * USD
    val coupon = 1.5

    val contract =
            (wileECoyote or roadRunner).may {
                "proceed".givenThat(after("01/07/2015")) {
                    wileECoyote.gives(roadRunner, libor( notional, "01/04/2015", "01/07/2015" ) )
                    roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon, "01/04/2015", "01/07/2015" ) )
                    (wileECoyote or roadRunner).may {
                        "proceed".givenThat(after("01/10/2015")) {
                            wileECoyote.gives(roadRunner, libor( notional, "01/07/2015", "01/10/2015" ) )
                            roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon,  "01/07/2015", "01/10/2015" ) )

                            (wileECoyote or roadRunner).may {
                                // etc ...
                            }
                        }
                    } or roadRunner.may {
                        "cancel".anytime {
                            roadRunner.gives( wileECoyote, 10.K * USD )
                        }
                    }
                }
            } or roadRunner.may {
                "cancel".anytime {
                    roadRunner.gives( wileECoyote, 10.K * USD )
                }
            }


    val contract2 = rollout( "01/04/2015", "01/04/2025", Frequency.Quarterly ) {
        (wileECoyote or roadRunner).may {
            "proceed".givenThat(after(start)) {
                wileECoyote.gives(roadRunner, libor( notional, start, end ) )
                roadRunner.gives(wileECoyote, interest( notional, "act/365", coupon,  start, end ) )
                recurse()
            }
        } or roadRunner.may {
            "cancel".anytime {
                roadRunner.gives( wileECoyote, 10.K * USD )
            }
        }
    }


    val strike = 1.2
    val tarf = rollout( "01/04/2015", "01/04/2016", Frequency.Quarterly ) {
        roadRunner.may {
            "exercise".givenThat(before(start)) {
                wileECoyote.gives(roadRunner, (EUR / USD - strike) * notional )
                recurse()
            }
        } or (roadRunner or wileECoyote).may {
            "proceed".givenThat(after(start)) {
                recurse()
            }
        }
    }

}