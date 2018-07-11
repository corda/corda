package net.corda.vega.flows

import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.CurrencyAmount
import com.opengamma.strata.basics.currency.FxRate
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.data.FxRateId
import com.opengamma.strata.data.MarketData
import com.opengamma.strata.market.observable.QuoteId
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.vega.analytics.CordaMarketData
import net.corda.vega.analytics.InitialMarginTriple
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A very quick util function to convert to decimals number of DPs.
 */
fun roundDP(decimals: Int): (Double) -> Double {
    return { it -> BigDecimal(it.toString()).setScale(decimals, RoundingMode.HALF_UP).toDouble() }
}

val twoDecimalPlaces = roundDP(2)

/**
 * We need to have a "cordaCompatible()" function on MarketData so that we can process / manipulate types that do not
 * either serialize or compare well due to Corda core not having support for adding custom serialisers at the time of
 * writing.
 */
fun MarketData.toCordaCompatible(): CordaMarketData {
    val values = this.ids.map {
        val value = this.getValue(it)
        when (it) {
            is QuoteId -> it.standardId.toString()
            is FxRateId -> it.pair.toString()
            else -> TODO("Conversion from type $it")
        } to when (value) {
            is FxRate -> BigDecimal.valueOf(value.fxRate(value.pair.base, value.pair.counter))
            is Double -> BigDecimal.valueOf(value)
            else -> TODO("Conversion from $value")
        }
    }.toMap()
    return CordaMarketData(valuationDate = this.valuationDate, values = values)
}

/**
 * A very basic modifier of InitialMarginTriple in order to ignore everything past the 2nd decimal place.
 */

fun InitialMarginTriple.toCordaCompatible() = InitialMarginTriple(twoDecimalPlaces(this.first), twoDecimalPlaces(this.second), twoDecimalPlaces(this.third))

/**
 * Utility function to ensure that [CurrencyParameterSensitivities] can be sent over corda and compared
 */
fun CurrencyParameterSensitivities.toCordaCompatible(): CurrencyParameterSensitivities {
    return CurrencyParameterSensitivities.of(this.sensitivities.map { sensitivity ->
        sensitivity.metaBean().builder()
                .set("marketDataName", sensitivity.marketDataName)
                .set("parameterMetadata", sensitivity.parameterMetadata)
                .set("currency", Currency.of(sensitivity.currency.code).serialize().deserialize())
                .set("sensitivity", sensitivity.sensitivity.map { twoDecimalPlaces(it) })
                .build()
    })
}

/**
 * Utility function to ensure that [MultiCurrencyAmount] can be sent over corda and compared
 */
fun MultiCurrencyAmount.toCordaCompatible(): MultiCurrencyAmount {
    return MultiCurrencyAmount.of(this.amounts.map { CurrencyAmount.of(Currency.of(it.currency.code).serialize().deserialize(), twoDecimalPlaces((it.amount))) })
}
