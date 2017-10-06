@file:JvmName("Generators")

package net.corda.client.mock

import net.corda.core.contracts.Amount
import net.corda.core.utilities.OpaqueBytes
import java.util.*

fun generateCurrency(): Generator<Currency> {
    return Generator.pickOne(Currency.getAvailableCurrencies().toList())
}

fun <T : Any> generateAmount(min: Long, max: Long, tokenGenerator: Generator<T>): Generator<Amount<T>> {
    return Generator.longRange(min, max).combine(tokenGenerator) { quantity, token -> Amount(quantity, token) }
}

fun generateCurrencyAmount(min: Long, max: Long): Generator<Amount<Currency>> {
    return generateAmount(min, max, generateCurrency())
}

fun generateIssueRef(size: Int): Generator<OpaqueBytes> {
    return Generator.bytes(size).map { OpaqueBytes(it) }
}
