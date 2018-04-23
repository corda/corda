/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.universal

import net.corda.finance.USD
import org.junit.Ignore
import org.junit.Test

// various example arrangements using basic syntax

class Examples {

    val cds_contract = arrange {
        actions {
            acmeCorp may {
                "claim".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
        }
    }

    // fx swap
// both parties have the right to trigger the exchange of cash flows
    val an_fx_swap = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1070.K, EUR)
                    acmeCorp.owes(highStreetBank, 1.M, USD)
                }
            }
        }
    }

    val american_fx_option = arrange {
        actions {
            acmeCorp may {
                "exercise".givenThat(before("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1070.K, EUR)
                    acmeCorp.owes(highStreetBank, 1.M, USD)
                }
            }
        }
    }

    val european_fx_option = arrange {
        actions {
            acmeCorp may {
                "exercise".givenThat(before("2017-09-01")) {
                    fx_swap("2017-09-01", 1.M, 1.2.bd, EUR, USD, acmeCorp, highStreetBank)
                }
            }
            (acmeCorp or highStreetBank) may {
                "expire" anytime {
                    zero
                }
            }
        }
    }

    val contractZeroCouponBond = arrange {
        actions {
            acmeCorp may {
                "execute".givenThat(after("2017-11-01")) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
        }
    }

    // maybe in the presence of negative interest rates you would want other side of contract to be able to take initiative as well
    val zero_coupon_bond_2 = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
        }
    }

    // no touch
// Party Receiver
// Party Giver
//
// Giver has right to annul contract if barrier is breached
// Receiver has right to receive money at/after expiry
//
// Assume observable is using FX fixing
//
    val no_touch = arrange {
        actions {
            (acmeCorp or highStreetBank) may {
                "execute".givenThat(after("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
            highStreetBank may {
                "knock out".givenThat(EUR / USD gt 1.3) {
                    zero
                }
            }
        }
    }

    val one_touch = arrange {
        actions {
            highStreetBank may {
                "expire".givenThat(after("2017-09-01")) {
                    zero
                }
            }
            acmeCorp may {
                "knock in".givenThat(EUR / USD gt 1.3) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
        }
    }

    @Test @Ignore
    fun `pretty print`() {
        println ( prettyPrint(cds_contract) )

        println ( prettyPrint(an_fx_swap) )

        println ( prettyPrint(american_fx_option) )

        println ( prettyPrint(european_fx_option) )

        println ( prettyPrint(contractZeroCouponBond) )

        println ( prettyPrint(zero_coupon_bond_2) )

        println ( prettyPrint(no_touch) )

        println ( prettyPrint(one_touch) )
    }


}