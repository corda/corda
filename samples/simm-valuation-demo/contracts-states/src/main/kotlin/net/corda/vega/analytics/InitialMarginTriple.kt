package net.corda.vega.analytics

/**
 *
 */
data class InitialMarginTriple(val first: Double, val second: Double, val third: Double) {
    companion object {
        fun zero() = InitialMarginTriple(0.0, 0.0, 0.0)
    }
}
