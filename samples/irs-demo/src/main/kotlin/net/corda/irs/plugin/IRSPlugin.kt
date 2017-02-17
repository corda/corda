package net.corda.irs.plugin

import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import net.corda.irs.api.InterestRateSwapAPI
import net.corda.irs.contract.*
import net.corda.irs.flows.AutoOfferFlow
import net.corda.irs.flows.ExitServerFlow
import net.corda.irs.flows.FixingFlow
import net.corda.irs.flows.UpdateBusinessDayFlow
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.function.Function

class IRSPlugin : CordaPluginRegistry() {
    override val webApis = listOf(Function(::InterestRateSwapAPI))
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
    override val servicePlugins = listOf(Function(FixingFlow::Service))
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            AutoOfferFlow.Requester::class.java.name to setOf(InterestRateSwap.State::class.java.name),
            UpdateBusinessDayFlow.Broadcast::class.java.name to setOf(LocalDate::class.java.name),
            ExitServerFlow.Broadcast::class.java.name to setOf(kotlin.Int::class.java.name),
            FixingFlow.FixingRoleDecider::class.java.name to setOf(StateRef::class.java.name, Duration::class.java.name),
            FixingFlow.Floater::class.java.name to setOf(Party::class.java.name, FixingFlow.FixingSession::class.java.name))

    override fun customiseSerialization(custom: SerializationCustomization): Boolean {
        custom.apply {
            addToWhitelist(InterestRateSwap::class.java)
            addToWhitelist(InterestRateSwap.State::class.java)
            addToWhitelist(InterestRateSwap.FixedLeg::class.java)
            addToWhitelist(InterestRateSwap.FloatingLeg::class.java)
            addToWhitelist(InterestRateSwap.Calculation::class.java)
            addToWhitelist(InterestRateSwap.Common::class.java)
            addToWhitelist(Expression::class.java)
            addToWhitelist(HashMap::class.java)
            addToWhitelist(LinkedHashMap::class.java)
            addToWhitelist(RatioUnit::class.java)
            addToWhitelist(Tenor::class.java)
            addToWhitelist(Tenor.TimeUnit::class.java)
            addToWhitelist(BusinessCalendar::class.java)
            addToWhitelist(Comparable::class.java)
            addToWhitelist(ReferenceRate::class.java)
            addToWhitelist(UnknownType::class.java)
            addToWhitelist(DayCountBasisDay::class.java)
            addToWhitelist(DayCountBasisYear::class.java)
            addToWhitelist(FixedRate::class.java)
            addToWhitelist(PercentageRatioUnit::class.java)
            addToWhitelist(BigDecimal::class.java)
            addToWhitelist(AccrualAdjustment::class.java)
            addToWhitelist(Frequency::class.java)
            addToWhitelist(PaymentRule::class.java)
            addToWhitelist(DateRollConvention::class.java)
            addToWhitelist(LocalDate::class.java)
            addToWhitelist(FixingFlow.FixingSession::class.java)
            addToWhitelist(FixedRatePaymentEvent::class.java)
            addToWhitelist(FloatingRatePaymentEvent::class.java)
        }
        return true
    }
}
