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
