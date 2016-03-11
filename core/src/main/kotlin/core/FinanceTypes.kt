/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.*

/**
 * Amount represents a positive quantity of currency, measured in pennies, which are the smallest representable units.
 *
 * Note that "pennies" are not necessarily 1/100ths of a currency unit, but are the actual smallest amount used in
 * whatever currency the amount represents.
 *
 * Amounts of different currencies *do not mix* and attempting to add or subtract two amounts of different currencies
 * will throw [IllegalArgumentException]. Amounts may not be negative. Amounts are represented internally using a signed
 * 64 bit value, therefore, the maximum expressable amount is 2^63 - 1 == Long.MAX_VALUE. Addition, subtraction and
 * multiplication are overflow checked and will throw [ArithmeticException] if the operation would have caused integer
 * overflow.
 *
 * TODO: It may make sense to replace this with convenience extensions over the JSR 354 MonetaryAmount interface
 * TODO: Should amount be abstracted to cover things like quantities of a stock, bond, commercial paper etc? Probably.
 * TODO: Think about how positive-only vs positive-or-negative amounts can be represented in the type system.
 */
data class Amount(val pennies: Long, val currency: Currency) : Comparable<Amount> {
    init {
        // Negative amounts are of course a vital part of any ledger, but negative values are only valid in certain
        // contexts: you cannot send a negative amount of cash, but you can (sometimes) have a negative balance.
        // If you want to express a negative amount, for now, use a long.
        require(pennies >= 0) { "Negative amounts are not allowed: $pennies" }
    }

    operator fun plus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.addExact(pennies, other.pennies), currency)
    }

    operator fun minus(other: Amount): Amount {
        checkCurrency(other)
        return Amount(Math.subtractExact(pennies, other.pennies), currency)
    }

    private fun checkCurrency(other: Amount) {
        require(other.currency == currency) { "Currency mismatch: ${other.currency} vs $currency" }
    }

    operator fun div(other: Long): Amount = Amount(pennies / other, currency)
    operator fun times(other: Long): Amount = Amount(Math.multiplyExact(pennies, other), currency)
    operator fun div(other: Int): Amount = Amount(pennies / other, currency)
    operator fun times(other: Int): Amount = Amount(Math.multiplyExact(pennies, other.toLong()), currency)

    override fun toString(): String = currency.currencyCode + " " + (BigDecimal(pennies) / BigDecimal(100)).toPlainString()

    override fun compareTo(other: Amount): Int {
        checkCurrency(other)
        return pennies.compareTo(other.pennies)
    }
}

fun Iterable<Amount>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()
fun Iterable<Amount>.sumOrThrow() = reduce { left, right -> left + right }
fun Iterable<Amount>.sumOrZero(currency: Currency) = if (iterator().hasNext()) sumOrThrow() else Amount(0, currency)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Interest rate fixes
//

/** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Duration)
/** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
data class Fix(val of: FixOf, val value: BigDecimal) : CommandData
