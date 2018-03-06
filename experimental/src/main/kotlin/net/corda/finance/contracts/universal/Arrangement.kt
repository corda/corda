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

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.contracts.Frequency
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@CordaSerializable
interface Arrangement

// A base arrangement with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.
class Zero : Arrangement {
    override fun hashCode(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is Zero
    }
}

// A basic arrangement representing immediate transfer of Cash - X amount of currency CCY from party A to party B.
// X is an observable of type BigDecimal.
//
// TODO: should be replaced with something that uses Corda assets and/or cash?
// TODO: should only be allowed to transfer non-negative amounts
data class Obligation(val amount: Perceivable<BigDecimal>, val currency: Currency, val from: Party, val to: Party) : Arrangement

// A combinator over a list of arrangements. Each arrangement in list will create a separate independent arrangement state.
// The ``And`` combinator cannot be root in a arrangement.
data class And(val arrangements: Set<Arrangement>) : Arrangement

@CordaSerializable
data class Action(val name: String, val condition: Perceivable<Boolean>, val arrangement: Arrangement)

// An action combinator. This declares a list of named action that can be taken by anyone of the actors given that
// _condition_ is met. If the action is performed the arrangement state transitions into the specified arrangement.
data class Actions(val actions: Set<Action>) : Arrangement

// Roll out of arrangement
// TODO: fixing offset
// TODO: think about payment offset (ie. settlement) - probably it doesn't belong on a distributed ledger
data class RollOut(val startDate: LocalDate, val endDate: LocalDate, val frequency: Frequency, val template: Arrangement) : Arrangement

// Continuation of roll out
// May only be used inside template for RollOut
class Continuation : Arrangement {
    override fun hashCode(): Int {
        return 1
    }

    override fun equals(other: Any?): Boolean {
        return other is Continuation
    }
}