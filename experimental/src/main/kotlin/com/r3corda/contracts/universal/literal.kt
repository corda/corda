package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Frequency
import com.r3corda.core.crypto.Party
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */


infix fun Arrangement.and(arrangement: Arrangement) = And( setOf(this, arrangement) )
infix fun Action.or(arrangement: Action) = Or( setOf(this, arrangement) )
infix fun Or.or(arrangement: Action) = Or( this.actions.plusElement(arrangement) )
infix fun Or.or(ors: Or) = Or( this.actions.plus(ors.actions) )

operator fun Long.times(currency: Currency) = Amount(this.toLong(), currency)
operator fun Double.times(currency: Currency) = Amount(BigDecimal(this.toDouble()), currency)

val Int.M: Long get() = this.toLong() * 1000000
val Int.K: Long get() = this.toLong() * 1000

val zero = Zero()

class ContractBuilder {
    val contracts = mutableListOf<Arrangement>()

    fun Party.gives(beneficiary: Party, amount: Amount<Currency>) {
        contracts.add( Transfer(amount, this, beneficiary))
    }

    fun Party.gives(beneficiary: Party, amount: Perceivable<Amount<Currency>>) {
        contracts.add( Transfer(amount, this, beneficiary))
    }

  /*  fun Party.gives(beneficiary: Party, amount: Perceivable<Long>, currency: Currency) {
        contracts.add( Transfer(amount, currency, this, beneficiary))
    }*/

    fun final() =
            when (contracts.size) {
                0 -> zero
                1 -> contracts[0]
                else -> And(contracts.toSet())
            }
}

interface GivenThatResolve {
    fun resolve(contract: Arrangement)
}

class ActionBuilder(val actors: Set<Party>) {
    val actions = mutableListOf<Action>()

    fun String.givenThat(condition: Perceivable<Boolean>, init: ContractBuilder.() -> Unit ) {
        val b = ContractBuilder()
        b.init()
        actions.add( Action(this, condition, actors, b.final() ) )
    }

    fun String.givenThat(condition: Perceivable<Boolean> ) : GivenThatResolve {
        val This = this
        return object : GivenThatResolve {
            override fun resolve(contract: Arrangement) {
                actions.add(Action(This, condition, actors, contract))
            }
        }
    }

    fun String.anytime(init: ContractBuilder.() -> Unit ) {
        val b = ContractBuilder()
        b.init()
        actions.add( Action(this, const(true), actors, b.final() ) )
    }
}

fun Party.may(init: ActionBuilder.() -> Unit) : Or {
    val b = ActionBuilder(setOf(this))
    b.init()
    return Or(b.actions.toSet())
}

fun Set<Party>.may(init: ActionBuilder.() -> Unit) : Or {
    val b = ActionBuilder(this)
    b.init()
    return Or(b.actions.toSet())
}

infix fun Party.or(party: Party) = setOf(this, party)
infix fun Set<Party>.or(party: Party) = this.plus(party)

fun arrange(init: ContractBuilder.() -> Unit ) : Arrangement {
    val b = ContractBuilder()
    b.init()
    return b.final()
}

class RolloutBuilder(val startDate: String, val endDate: String, val frequency: Frequency) {

    val start = "start date"
    val end = "end date"
    fun recurse() = zero

    fun final() =
            RollOut(startDate, endDate, frequency, zero)
}

fun rollout(startDate: String, endDate: String, frequency: Frequency, init: RolloutBuilder.() -> Unit) : Arrangement {
    val b = RolloutBuilder(startDate, endDate, frequency)
    b.init()
    return b.final()
}
