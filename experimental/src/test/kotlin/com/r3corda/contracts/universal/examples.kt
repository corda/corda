package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.USD
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

// various example arrangements using basic syntax

val cds_contract = arrange {
    acmeCorp.may {
        "claim".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1.M, USD)
        }
    }
}

// fx swap
// both parties have the right to trigger the exchange of cash flows
val an_fx_swap = arrange {
    (acmeCorp or highStreetBank).may {
        "execute".givenThat(after("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1200.K, USD)
            acmeCorp.gives(highStreetBank, 1.M, EUR)
        }
    }
}

val american_fx_option = arrange {
    acmeCorp.may {
        "exercise".givenThat(before("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1200.K, USD)
            acmeCorp.gives(highStreetBank, 1.M, EUR)
        }
    }
}

val european_fx_option = arrange {
    acmeCorp.may {
        "exercise".givenThat(before("2017-09-01")) {
            fx_swap("2017-09-01", 1.M, 1.2.bd, EUR, USD, acmeCorp, highStreetBank)
        }
    }
    (acmeCorp or highStreetBank).may {
        "expire".anytime {
            zero
        }
    }
}

val zero_coupon_bond_1 = arrange {
    acmeCorp.may {
        "execute".givenThat(after("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1.M, USD)
        }
    }
}

// maybe in the presence of negative interest rates you would want other side of contract to be able to take initiative as well
val zero_coupon_bond_2 = arrange {
    (acmeCorp or highStreetBank).may {
        "execute".givenThat(after("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1.M, USD)
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
    (acmeCorp or highStreetBank).may {
        "execute".givenThat(after("2017-09-01")) {
            highStreetBank.gives(acmeCorp, 1.M, USD)
        }
    }
    highStreetBank.may {
        "knock out".givenThat(EUR/USD gt 1.3)
    }
}

val one_touch = arrange {
    highStreetBank.may {
        "expire".givenThat(after("2017-09-01")) {
            zero
        }
    }
    acmeCorp.may {
        "knock in".givenThat(EUR / USD gt 1.3) {
            highStreetBank.gives(acmeCorp, 1.M, USD)
        }
    }
}
