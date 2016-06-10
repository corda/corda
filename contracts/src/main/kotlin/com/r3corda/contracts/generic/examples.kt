package com.r3corda.contracts.generic

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

/**
    val cds_contract = Kontract.Action("payout", acmeCorporationHasDefaulted and before("2017-09-01"),
            roadRunner,
            Kontract.Transfer(Amount(1.M, USD), wileECoyote, roadRunner))

// fx swap
// both parties have the right to trigger the exchange of cash flows
    val an_fx_swap = Kontract.Action("execute", after("2017-09-01"), arrayOf(roadRunner, wileECoyote),
            Kontract.Transfer(1200.K * USD, wileECoyote, roadRunner)
                    and Kontract.Transfer(1.M * EUR, roadRunner, wileECoyote))

    val american_fx_option = Kontract.Action("exercise", before("2017-09-01"),
            roadRunner,
            Kontract.Transfer(1200.K * USD, wileECoyote, roadRunner)
                    and Kontract.Transfer(1.M * EUR, roadRunner, wileECoyote))

    val european_fx_option = Kontract.Action("exercise", before("2017-09-01"), roadRunner, fx_swap("2017-09-01", 1.M, 1.2, EUR, USD, roadRunner, wileECoyote)) or
            Kontract.Action("expire", after("2017-09-01"), wileECoyote, zero)

    val zero_coupon_bond_1 = Kontract.Action("execute", after("2017-09-01"), roadRunner, Kontract.Transfer(1.M * USD, wileECoyote, roadRunner))

// maybe in the presence of negative interest rates you would want other side of contract to be able to take initiative as well
    val zero_coupon_bond_2 = Kontract.Action("execute", after("2017-09-01"), arrayOf(roadRunner, wileECoyote), Kontract.Transfer(1.M * USD, wileECoyote, roadRunner))

// no touch
// Party Receiver
// Party Giver
//
// Giver has right to annul contract if barrier is breached
// Receiver has right to receive money at/after expiry
//
// Assume observable is using FX fixing
//
    val no_touch = Kontract.Action("execute", after("2017-09-01"), arrayOf(roadRunner, wileECoyote), Kontract.Transfer(1.M * USD, wileECoyote, roadRunner)) or
            Kontract.Action("knock out", EUR / USD gt 1.3, wileECoyote, zero)

    val one_touch = Kontract.Action("expire", after("2017-09-01"), wileECoyote, zero) or
            Kontract.Action("knock in", EUR / USD gt 1.3, roadRunner, Kontract.Transfer(1.M * USD, wileECoyote, roadRunner))
*/