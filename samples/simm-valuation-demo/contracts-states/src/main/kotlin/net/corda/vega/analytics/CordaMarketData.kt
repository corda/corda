package net.corda.vega.analytics

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Kryo and Jackson compatible market data
 */
data class CordaMarketData(val valuationDate: LocalDate,
                           val values: Map<String, BigDecimal>)
