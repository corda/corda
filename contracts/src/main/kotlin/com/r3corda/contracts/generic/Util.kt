package com.r3corda.contracts.generic

import com.r3corda.core.contracts.Amount
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

val Int.M: Long get() = this.toLong() * 1000000
val Int.K: Long get() = this.toLong() * 1000

val zero = Kontract.Zero()

infix fun Kontract.and(kontract: Kontract) = Kontract.And( arrayOf(this, kontract) )
infix fun Kontract.Action.or(kontract: Kontract.Action) = Kontract.Or( arrayOf(this, kontract) )
infix fun Kontract.Or.or(kontract: Kontract.Action) = Kontract.Or( this.contracts.plusElement( kontract ) )
infix fun Kontract.Or.or(ors: Kontract.Or) = Kontract.Or( this.contracts.plus(ors.contracts) )

operator fun Long.times(currency: Currency) = Amount(this.toLong(), currency)
operator fun Double.times(currency: Currency) = Amount(BigDecimal(this.toDouble()), currency)