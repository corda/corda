package net.corda.vega.services

import com.esotericsoftware.kryo.Kryo
import com.google.common.collect.Ordering
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.CurrencyAmount
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.date.Tenor
import com.opengamma.strata.collect.array.DoubleArray
import com.opengamma.strata.market.curve.CurveName
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.market.param.CurrencyParameterSensitivity
import com.opengamma.strata.market.param.TenorDateParameterMetadata
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.vega.analytics.CordaMarketData
import net.corda.vega.analytics.InitialMarginTriple
import net.corda.vega.api.PortfolioApi
import net.corda.vega.contracts.*
import net.corda.vega.flows.IRSTradeFlow
import net.corda.vega.flows.SimmFlow
import net.corda.vega.flows.SimmRevaluation
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.util.*
import java.util.function.Function

/**
 * [SimmService] is the object that makes available the flows and services for the Simm agreement / evaluation flow
 * It also enables a human usable web service for demo purposes - if available.
 * It is loaded via discovery - see [CordaPluginRegistry]
 */
object SimmService {
    class Plugin : CordaPluginRegistry() {
        override val webApis = listOf(Function(::PortfolioApi))
        override val requiredFlows: Map<String, Set<String>> = mapOf(
                SimmFlow.Requester::class.java.name to setOf(Party::class.java.name, LocalDate::class.java.name),
                SimmRevaluation.Initiator::class.java.name to setOf(StateRef::class.java.name, LocalDate::class.java.name),
                IRSTradeFlow.Requester::class.java.name to setOf(SwapData::class.java.name, Party::class.java.name))
        override val staticServeDirs: Map<String, String> = mapOf("simmvaluationdemo" to javaClass.classLoader.getResource("simmvaluationweb").toExternalForm())
        override val servicePlugins = listOf(Function(SimmFlow::Service), Function(IRSTradeFlow::Service))
        override fun registerRPCKryoTypes(kryo: Kryo): Boolean {
            kryo.apply {
                register(SwapData::class.java)
                register(LocalDate::class.java)
                register(BigDecimal::class.java)
                register(IRSState::class.java)
                register(OGTrade::class.java)
                register(PortfolioState::class.java)
                register(PortfolioSwap::class.java)
                register(PortfolioValuation::class.java)
                register(MultiCurrencyAmount::class.java)
                register(Ordering.natural<Comparable<Any>>().javaClass)
                register(CurrencyAmount::class.java)
                register(Currency::class.java)
                register(InitialMarginTriple::class.java)
                register(CordaMarketData::class.java)
                register(CurrencyParameterSensitivities::class.java)
                register(CurrencyParameterSensitivity::class.java)
                register(DoubleArray::class.java)
                register(kotlin.DoubleArray::class.java)
                register(LinkedHashMap::class.java)
                register(CurveName::class.java)
                register(TenorDateParameterMetadata::class.java)
                register(Tenor::class.java)
                register(Period::class.java)
                register(Class.forName("java.util.Collections\$SingletonMap"))
            }
            return true
        }
    }
}
