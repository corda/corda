package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Frequency
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

interface Arrangement

// A base arrangement with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.
class Zero() : Arrangement {
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
// todo: should be replaced with something that uses Corda assets and/or cash?
// todo: should only be allowed to transfer non-negative amounts
data class Transfer(val amount: Perceivable<BigDecimal>, val currency: Currency, val from: Party, val to: Party) : Arrangement

// A combinator over a list of arrangements. Each arrangement in list will create a separate independent arrangement state.
// The ``And`` combinator cannot be root in a arrangement.
data class And(val arrangements: Set<Arrangement>) : Arrangement

data class Action(val name: String, val condition: Perceivable<Boolean>,
                  val actors: Set<Party>, val arrangement: Arrangement)

// An action combinator. This declares a list of named action that can be taken by anyone of the actors given that
// _condition_ is met. If the action is performed the arrangement state transitions into the specified arrangement.
data class Actions(val actions: Set<Action>) : Arrangement

//    constructor(name: String, condition: Perceivable<Boolean>,
// actor: Party, arrangement: Arrangement)


// Roll out of arrangement
data class RollOut(val startDate: LocalDate, val endDate: LocalDate, val frequency: Frequency, val template: Arrangement) : Arrangement


// Continuation of roll out
// May only be used inside template for RollOut
class Continuation() : Arrangement {
    override fun hashCode(): Int {
        return 1
    }

    override fun equals(other: Any?): Boolean {
        return other is Continuation
    }
}

// A smart contract template
// todo: handle parameters
//
data class Template(val template: Arrangement)

data class TemplateApplication(val template: SecureHash, val parameters: Map<String, Any>) : Arrangement

data class Context(val arrangement: Arrangement, val parameters: Map<String, Any>) : Arrangement
