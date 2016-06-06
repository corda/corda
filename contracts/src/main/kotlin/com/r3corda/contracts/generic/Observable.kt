package com.r3corda.contracts.generic

import java.math.BigDecimal
import java.text.DateFormat
import java.time.Instant
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

interface Observable<T>

enum class Comparison {
    LT, LTE, GT, GTE
}

/**
 * Constant observable
 */
data class Const<T>(val value: T) : Observable<T>

fun<T> const(k: T) = Const(k)

/**
 * Observable based on time
 */
data class TimeObservable(val cmp: Comparison, val instant: Instant) : Observable<Boolean>

fun parseInstant(str: String) = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).parse(str).toInstant()

fun before(expiry: Instant) = TimeObservable(Comparison.LTE, expiry)
fun after(expiry: Instant) = TimeObservable(Comparison.GTE, expiry)
fun before(expiry: String) = TimeObservable(Comparison.LTE, parseInstant(expiry))
fun after(expiry: String) = TimeObservable(Comparison.GTE, parseInstant(expiry))

data class ObservableAnd(val left: Observable<Boolean>, val right: Observable<Boolean>) : Observable<Boolean>
infix fun Observable<Boolean>.and(obs: Observable<Boolean>) = ObservableAnd(this, obs)

data class ObservableOr(val left: Observable<Boolean>, val right: Observable<Boolean>) : Observable<Boolean>
infix fun Observable<Boolean>.or(obs: Observable<Boolean>) = ObservableOr(this, obs)

data class CurrencyCross(val foreign: Currency, val domestic: Currency) : Observable<BigDecimal>

operator fun Currency.div(currency: Currency) = CurrencyCross(this, currency)

data class ObservableComparison<T>(val left: Observable<T>, val cmp: Comparison, val right: Observable<T>) : Observable<Boolean>

infix fun Observable<BigDecimal>.lt(n: BigDecimal) = ObservableComparison<BigDecimal>(this, Comparison.LT, const(n))
infix fun Observable<BigDecimal>.gt(n: BigDecimal) = ObservableComparison<BigDecimal>(this, Comparison.GT, const(n))
infix fun Observable<BigDecimal>.lt(n: Double) = ObservableComparison<BigDecimal>(this, Comparison.LT, const( BigDecimal(n) ))
infix fun Observable<BigDecimal>.gt(n: Double) = ObservableComparison<BigDecimal>(this, Comparison.GT, const( BigDecimal(n) ))

infix fun Observable<BigDecimal>.lte(n: BigDecimal) = ObservableComparison<BigDecimal>(this, Comparison.LTE, const(n))
infix fun Observable<BigDecimal>.gte(n: BigDecimal) = ObservableComparison<BigDecimal>(this, Comparison.GTE, const(n))
infix fun Observable<BigDecimal>.lte(n: Double) = ObservableComparison<BigDecimal>(this, Comparison.LTE, const( BigDecimal(n) ))
infix fun Observable<BigDecimal>.gte(n: Double) = ObservableComparison<BigDecimal>(this, Comparison.GTE, const( BigDecimal(n) ))

enum class Operation {
    PLUS, MINUS, TIMES, DIV
}

data class ObservableOperation<T>(val left: Observable<T>, val op: Operation, val right: Observable<T>) : Observable<T>

infix fun Observable<BigDecimal>.plus(n: BigDecimal) = ObservableOperation<BigDecimal>(this, Operation.PLUS, const(n))
infix fun Observable<BigDecimal>.minus(n: BigDecimal) = ObservableOperation<BigDecimal>(this, Operation.MINUS, const(n))
infix fun Observable<BigDecimal>.times(n: BigDecimal) = ObservableOperation<BigDecimal>(this, Operation.TIMES, const(n))
infix fun Observable<BigDecimal>.div(n: BigDecimal) = ObservableOperation<BigDecimal>(this, Operation.DIV, const(n))