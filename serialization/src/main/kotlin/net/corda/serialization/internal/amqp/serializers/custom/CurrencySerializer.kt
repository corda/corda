package net.corda.serialization.internal.amqp.serializers.custom

import net.corda.serialization.internal.amqp.serializers.CustomSerializer
import java.util.*

/**
 * A custom serializer for the [Currency] class, utilizing the currency code string representation.
 */
object CurrencySerializer : CustomSerializer.ToString<Currency>(Currency::class.java,
        withInheritance = false,
        maker = { Currency.getInstance(it) },
        unmaker = { it.currencyCode })
