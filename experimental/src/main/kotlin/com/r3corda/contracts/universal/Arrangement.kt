package com.r3corda.contracts.universal

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Frequency
import com.r3corda.core.crypto.Party
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

interface Arrangement


// A base arrangement with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.
class Zero() : Arrangement {
    override fun hashCode(): Int {
        return 0;
    }
    override fun equals(other: Any?): Boolean {
        return other is Zero
    }
}

// A basic arrangement representing immediate transfer of Cash - X amount of currency CCY from party A to party B.
// X is an observable of type BigDecimal.
//
// todo: should be replaced with something that uses Corda assets and/or cash?
data class Transfer(val amount: Perceivable<Amount<Currency>>, val from: Party, val to: Party) : Arrangement {
    constructor(amount: Amount<Currency>, from: Party, to: Party ) : this(const(amount), from, to)
}


// A combinator over a list of arrangements. Each arrangement in list will create a separate independent arrangement state.
// The ``And`` combinator cannot be root in a arrangement.
data class And(val arrangements: Set<Arrangement>) : Arrangement


// An action combinator. This declares a named action that can be taken by anyone of the actors given that
// _condition_ is met. If the action is performed the arrangement state transitions into the specified arrangement.
data class Action(val name: String, val condition: Perceivable<Boolean>,
                  val actors: Set<Party>, val arrangement: Arrangement) : Arrangement {
    constructor(name: String, condition: Perceivable<Boolean>, actor: Party, arrangement: Arrangement) : this(name, condition, setOf(actor), arrangement)
}


// only actions can be or'ed togetherA combinator that can only be used on action arrangements. This means only one of the action can be executed. Should any one action be executed, all other actions are discarded.
data class Or(val actions: Set<Action>) : Arrangement


data class RollOut(val startDate: String, val endDate: String, val frequency: Frequency, val arrangement: Arrangement) : Arrangement