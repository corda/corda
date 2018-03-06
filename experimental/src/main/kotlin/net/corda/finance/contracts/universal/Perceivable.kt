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
import net.corda.finance.contracts.BusinessCalendar
import net.corda.finance.contracts.Tenor
import java.lang.reflect.Type
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.util.*

@CordaSerializable
interface Perceivable<T>

@CordaSerializable
enum class Comparison {
    LT, LTE, GT, GTE
}

/**
 * Constant perceivable
 */
data class Const<T>(val value: T) : Perceivable<T> {
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (value == null) {
            return false
        }
        if (other is Const<*>) {
            if (value is BigDecimal && other.value is BigDecimal) {
                return this.value.compareTo(other.value) == 0
            }
            return value.equals(other.value)
        }
        return false
    }

    override fun hashCode(): Int =
            if (value is BigDecimal)
                value.toDouble().hashCode()
            else
                value!!.hashCode()
}

fun <T> const(k: T) : Perceivable<T> = Const(k)

// fun const(b: Boolean) : Perceivable<Boolean> = Const(b)

data class Max(val args: Set<Perceivable<BigDecimal>>) : Perceivable<BigDecimal>

fun max(vararg args: Perceivable<BigDecimal>) = Max(args.toSet())

data class Min(val args: Set<Perceivable<BigDecimal>>) : Perceivable<BigDecimal>

fun min(vararg args: Perceivable<BigDecimal>) = Min(args.toSet())

class StartDate : Perceivable<Instant> {
    override fun hashCode(): Int {
        return 2
    }

    override fun equals(other: Any?): Boolean {
        return other is StartDate
    }
}

class EndDate : Perceivable<Instant> {
    override fun hashCode(): Int {
        return 3
    }

    override fun equals(other: Any?): Boolean {
        return other is EndDate
    }
}

data class ActorPerceivable(val actor: Party) : Perceivable<Boolean>
fun signedBy(actor: Party) : Perceivable<Boolean> = ActorPerceivable(actor)

fun signedByOneOf(actors: Collection<Party>): Perceivable<Boolean> =
        if (actors.size == 0)
            const(true)
        else
            actors.drop(1).fold(signedBy(actors.first())) { total, next -> total or signedBy(next) }

/**
 * Perceivable based on time
 */
data class TimePerceivable(val cmp: Comparison, val instant: Perceivable<Instant>) : Perceivable<Boolean>

fun parseDate(str: String) = BusinessCalendar.parseDateFromString(str)

fun before(expiry: Perceivable<Instant>) = TimePerceivable(Comparison.LTE, expiry)
fun after(expiry: Perceivable<Instant>) = TimePerceivable(Comparison.GTE, expiry)
fun before(expiry: Instant) = TimePerceivable(Comparison.LTE, const(expiry))
fun after(expiry: Instant) = TimePerceivable(Comparison.GTE, const(expiry))
fun before(expiry: LocalDate) = TimePerceivable(Comparison.LTE, const(expiry.toInstant()))
fun after(expiry: LocalDate) = TimePerceivable(Comparison.GTE, const(expiry.toInstant()))
fun before(expiry: String) = TimePerceivable(Comparison.LTE, const(parseDate(expiry).toInstant()))
fun after(expiry: String) = TimePerceivable(Comparison.GTE, const(parseDate(expiry).toInstant()))

data class PerceivableAnd(val left: Perceivable<Boolean>, val right: Perceivable<Boolean>) : Perceivable<Boolean>

infix fun Perceivable<Boolean>.and(obs: Perceivable<Boolean>) = PerceivableAnd(this, obs)

data class PerceivableOr(val left: Perceivable<Boolean>, val right: Perceivable<Boolean>) : Perceivable<Boolean>

infix fun Perceivable<Boolean>.or(obs: Perceivable<Boolean>) = PerceivableOr(this, obs)

data class CurrencyCross(val foreign: Currency, val domestic: Currency) : Perceivable<BigDecimal>

operator fun Currency.div(currency: Currency) = CurrencyCross(this, currency)

data class PerceivableComparison<T>(val left: Perceivable<T>, val cmp: Comparison, val right: Perceivable<T>, val type: Type) : Perceivable<Boolean>

inline fun<reified T : Any> perceivableComparison(left: Perceivable<T>, cmp: Comparison, right: Perceivable<T>) =
    PerceivableComparison(left, cmp, right, T::class.java)

infix fun Perceivable<BigDecimal>.lt(n: BigDecimal) = perceivableComparison(this, Comparison.LT, const(n))
infix fun Perceivable<BigDecimal>.gt(n: BigDecimal) = perceivableComparison(this, Comparison.GT, const(n))
infix fun Perceivable<BigDecimal>.lt(n: Double) = perceivableComparison(this, Comparison.LT, const(BigDecimal(n)))
infix fun Perceivable<BigDecimal>.gt(n: Double) = perceivableComparison(this, Comparison.GT, const(BigDecimal(n)))

infix fun Perceivable<BigDecimal>.lte(n: BigDecimal) = perceivableComparison(this, Comparison.LTE, const(n))
infix fun Perceivable<BigDecimal>.gte(n: BigDecimal) = perceivableComparison(this, Comparison.GTE, const(n))
infix fun Perceivable<BigDecimal>.lte(n: Double) = perceivableComparison(this, Comparison.LTE, const(BigDecimal(n)))
infix fun Perceivable<BigDecimal>.gte(n: Double) = perceivableComparison(this, Comparison.GTE, const(BigDecimal(n)))

@CordaSerializable
enum class Operation {
    PLUS, MINUS, TIMES, DIV
}

data class UnaryPlus<T>(val arg: Perceivable<T>) : Perceivable<T>

data class PerceivableOperation<T>(val left: Perceivable<T>, val op: Operation, val right: Perceivable<T>) : Perceivable<T>

operator fun Perceivable<BigDecimal>.plus(n: BigDecimal) = PerceivableOperation(this, Operation.PLUS, const(n))
fun <T> Perceivable<T>.plus() = UnaryPlus(this)
operator fun Perceivable<BigDecimal>.minus(n: Perceivable<BigDecimal>) = PerceivableOperation(this, Operation.MINUS, n)
operator fun Perceivable<BigDecimal>.minus(n: BigDecimal) = PerceivableOperation(this, Operation.MINUS, const(n))
operator fun Perceivable<BigDecimal>.plus(n: Double) = PerceivableOperation(this, Operation.PLUS, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.minus(n: Double) = PerceivableOperation(this, Operation.MINUS, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.times(n: BigDecimal) = PerceivableOperation(this, Operation.TIMES, const(n))
operator fun Perceivable<BigDecimal>.div(n: BigDecimal) = PerceivableOperation(this, Operation.DIV, const(n))
operator fun Perceivable<BigDecimal>.times(n: Double) = PerceivableOperation(this, Operation.TIMES, const(BigDecimal(n)))
operator fun Perceivable<BigDecimal>.div(n: Double) = PerceivableOperation(this, Operation.DIV, const(BigDecimal(n)))

operator fun Perceivable<Int>.plus(n: Int) = PerceivableOperation(this, Operation.PLUS, const(n))
operator fun Perceivable<Int>.minus(n: Int) = PerceivableOperation(this, Operation.MINUS, const(n))

data class TerminalEvent(val reference: Party, val source: PublicKey) : Perceivable<Boolean>

// todo: holidays
data class Interest(val amount: Perceivable<BigDecimal>, val dayCountConvention: String,
                    val interest: Perceivable<BigDecimal>, val start: Perceivable<Instant>, val end: Perceivable<Instant>) : Perceivable<BigDecimal>

fun interest(@Suppress("UNUSED_PARAMETER") amount: BigDecimal, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: BigDecimal /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: String, @Suppress("UNUSED_PARAMETER") end: String): Perceivable<BigDecimal> = Interest(Const(amount), dayCountConvention, Const(interest), const(parseDate(start).toInstant()), const(parseDate(end).toInstant()))

fun interest(@Suppress("UNUSED_PARAMETER") amount: BigDecimal, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: Perceivable<BigDecimal> /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: String, @Suppress("UNUSED_PARAMETER") end: String): Perceivable<BigDecimal> =
        Interest(Const(amount), dayCountConvention, interest, const(parseDate(start).toInstant()), const(parseDate(end).toInstant()))

fun interest(@Suppress("UNUSED_PARAMETER") amount: BigDecimal, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: BigDecimal /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: Perceivable<Instant>, @Suppress("UNUSED_PARAMETER") end: Perceivable<Instant>): Perceivable<BigDecimal> = Interest(const(amount), dayCountConvention, const(interest), start, end)

fun interest(@Suppress("UNUSED_PARAMETER") amount: BigDecimal, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: Perceivable<BigDecimal> /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: Perceivable<Instant>, @Suppress("UNUSED_PARAMETER") end: Perceivable<Instant>): Perceivable<BigDecimal> = Interest(const(amount), dayCountConvention, interest, start, end)

data class Fixing(val source: String, val date: Perceivable<Instant>, val tenor: Tenor) : Perceivable<BigDecimal>

// TODO: fix should have implied default date and perhaps tenor when used in a rollOut template
fun fix(source: String, date: Perceivable<Instant>, tenor: Tenor): Perceivable<BigDecimal> = Fixing(source, date, tenor)
fun fix(source: String, date: LocalDate, tenor: Tenor): Perceivable<BigDecimal> = Fixing(source, const(date.toInstant()), tenor)
