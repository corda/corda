package net.corda.serialization.djvm.deserializers

import java.util.Currency
import java.util.function.Function

class CreateCurrency : Function<String, Currency> {
    override fun apply(currencyCode: String): Currency {
        return Currency.getInstance(currencyCode)
    }
}
