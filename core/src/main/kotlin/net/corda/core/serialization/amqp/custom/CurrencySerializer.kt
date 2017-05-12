package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.CustomSerializer
import java.util.*


object CurrencySerializer : CustomSerializer.ToString<Currency>(Currency::class.java,
        withInheritance = false,
        maker = { Currency.getInstance(it) },
        unmaker = { it.currencyCode })
