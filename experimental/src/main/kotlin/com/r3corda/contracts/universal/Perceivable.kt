package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import java.math.BigDecimal
import java.text.DateFormat
import java.time.Instant
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

interface Perceivable<T>

enum class Comparison {
    LT, LTE, GT, GTE
}

/**
 * Constant perceivable
 */
data class Const<T>(val value: T) : Perceivable<T>

fun<T> const(k: T) = Const(k)

//
class StartDate : Perceivable<Instant>
class EndDate : Perceivable<Instant>

/**
 * Perceivable based on time
 */
data class TimePerceivable(val cmp: Comparison, val instant: Perceivable<Instant>) : Perceivable<Boolean>

fun parseInstant(str: String) = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).parse(str).toInstant()

fun before(expiry: Perceivable<Instant>) = TimePerceivable(Comparison.LTE, expiry)
fun after(expiry: Perceivable<Instant>) = TimePerceivable(Comparison.GTE, expiry)
fun before(expiry: Instant) = TimePerceivable(Comparison.LTE, const(expiry))
fun after(expiry: Instant) = TimePerceivable(Comparison.GTE, const(expiry))
fun before(expiry: String) = TimePerceivable(Comparison.LTE, const(parseInstant(expiry)))
fun after(expiry: String) = TimePerceivable(Comparison.GTE, const(parseInstant(expiry)))

data class PerceivableAnd(val left: Perceivable<Boolean>, val right: Perceivable<Boolean>) : Perceivable<Boolean>
infix fun Perceivable<Boolean>.and(obs: Perceivable<Boolean>) = PerceivableAnd(this, obs)

data class PerceivableOr(val left: Perceivable<Boolean>, val right: Perceivable<Boolean>) : Perceivable<Boolean>
infix fun Perceivable<Boolean>.or(obs: Perceivable<Boolean>) = PerceivableOr(this, obs)

data class CurrencyCross(val foreign: Currency, val domestic: Currency) : Perceivable<BigDecimal>

operator fun Currency.div(currency: Currency) = CurrencyCross(this, currency)

data class PerceivableComparison<T>(val left: Perceivable<T>, val cmp: Comparison, val right: Perceivable<T>) : Perceivable<Boolean>

infix fun Perceivable<BigDecimal>.lt(n: BigDecimal) = PerceivableComparison(this, Comparison.LT, const(n))
infix fun Perceivable<BigDecimal>.gt(n: BigDecimal) = PerceivableComparison(this, Comparison.GT, const(n))
infix fun Perceivable<BigDecimal>.lt(n: Double) = PerceivableComparison(this, Comparison.LT, const( BigDecimal(n) ))
infix fun Perceivable<BigDecimal>.gt(n: Double) = PerceivableComparison(this, Comparison.GT, const( BigDecimal(n) ))

infix fun Perceivable<BigDecimal>.lte(n: BigDecimal) = PerceivableComparison(this, Comparison.LTE, const(n))
infix fun Perceivable<BigDecimal>.gte(n: BigDecimal) = PerceivableComparison(this, Comparison.GTE, const(n))
infix fun Perceivable<BigDecimal>.lte(n: Double) = PerceivableComparison(this, Comparison.LTE, const( BigDecimal(n) ))
infix fun Perceivable<BigDecimal>.gte(n: Double) = PerceivableComparison(this, Comparison.GTE, const( BigDecimal(n) ))

enum class Operation {
    PLUS, MINUS, TIMES, DIV
}

data class PerceivableOperation<T>(val left: Perceivable<T>, val op: Operation, val right: Perceivable<T>) : Perceivable<T>

operator fun Perceivable<BigDecimal>.plus(n: BigDecimal) = PerceivableOperation(this, Operation.PLUS, const(n))
operator fun Perceivable<BigDecimal>.minus(n: BigDecimal) = PerceivableOperation(this, Operation.MINUS, const(n))
operator fun Perceivable<BigDecimal>.plus(n: Double) = PerceivableOperation(this, Operation.PLUS, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.minus(n: Double) = PerceivableOperation(this, Operation.MINUS, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.times(n: BigDecimal) = PerceivableOperation(this, Operation.TIMES, const(n))
operator fun Perceivable<BigDecimal>.div(n: BigDecimal) = PerceivableOperation(this, Operation.DIV, const(n))
operator fun Perceivable<BigDecimal>.times(n: Double) = PerceivableOperation(this, Operation.TIMES, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.div(n: Double) = PerceivableOperation(this, Operation.DIV, const(BigDecimal(n)))

data class ScaleAmount<T>(val left: Perceivable<BigDecimal>, val right: Perceivable<Amount<T>>) : Perceivable<Amount<T>>

operator fun Perceivable<BigDecimal>.times(n: Amount<Currency>) = ScaleAmount(this, const(n))
        //PerceivableOperation(this, Operation.TIMES, const(BigDecimal(n)))
