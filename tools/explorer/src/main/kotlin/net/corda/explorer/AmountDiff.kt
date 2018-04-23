/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer

import net.corda.core.contracts.Amount

enum class Positivity {
    Positive,
    Negative
}

val Positivity.sign: String
    get() = when (this) {
        Positivity.Positive -> ""
        Positivity.Negative -> "-"
    }

data class AmountDiff<T : Any>(
        val positivity: Positivity,
        val amount: Amount<T>
) {
    companion object {
        fun <T : Any> fromLong(quantity: Long, token: T) =
                AmountDiff(
                        positivity = if (quantity < 0) Positivity.Negative else Positivity.Positive,
                        amount = Amount(Math.abs(quantity), token)
                )
    }
}
