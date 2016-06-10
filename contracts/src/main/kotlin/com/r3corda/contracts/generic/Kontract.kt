package com.r3corda.contracts.generic

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import java.security.PublicKey
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

interface Kontract


// A base contract with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.
data class Zero(val dummy: Int = 0) : Kontract


// A base contract representing immediate transfer of Cash - X amount of currency CCY from party A to party B.
// X is an observable of type BigDecimal.
//
// todo: should be replaced with something that uses Corda assets and/or cash?
data class Transfer(val amount: Observable<Long>, val currency: Currency, val from: Party, val to: Party) : Kontract {
    constructor(amount: Amount<Currency>, from: Party, to: Party ) : this(const(amount.quantity), amount.token, from, to)
}


// A combinator over a list of contracts. Each contract in list will create a separate independent contract state.
// The ``And`` combinator cannot be root in a contract.
data class And(val kontracts: Set<Kontract>) : Kontract


// An action combinator. This declares a named action that can be taken by anyone of the actors given that
// _condition_ is met. If the action is performed the contract state transitions into the specificed contract.
data class Action(val name: String, val condition: Observable<Boolean>,
                  val actors: Set<Party>, val kontract: Kontract) : Kontract {
    constructor(name: String, condition: Observable<Boolean>, actor: Party, kontract: Kontract) : this(name, condition, setOf(actor), kontract)
}


// only actions can be or'ed togetherA combinator that can only be used on action contracts. This means only one of the action can be executed. Should any one action be executed, all other actions are discarded.
data class Or(val actions: Set<Action>) : Kontract
