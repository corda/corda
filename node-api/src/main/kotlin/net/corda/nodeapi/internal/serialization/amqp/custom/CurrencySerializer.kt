/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import java.util.*

/**
 * A custom serializer for the [Currency] class, utilizing the currency code string representation.
 */
object CurrencySerializer : CustomSerializer.ToString<Currency>(Currency::class.java,
        withInheritance = false,
        maker = { Currency.getInstance(it) },
        unmaker = { it.currencyCode })
