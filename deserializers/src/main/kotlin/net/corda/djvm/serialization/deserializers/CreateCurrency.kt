package net.corda.djvm.serialization.deserializers

import java.util.*
import java.util.function.Function

class CreateCurrency : Function<String, Currency> {
    override fun apply(currencyCode: String): Currency {
        return Currency.getInstance(currencyCode)
    }
}
