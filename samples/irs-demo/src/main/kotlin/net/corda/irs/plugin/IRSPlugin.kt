package net.corda.irs.plugin

import com.esotericsoftware.kryo.Kryo
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
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

    override fun registerRPCKryoTypes(kryo: Kryo): Boolean {
        kryo.apply {
            register(InterestRateSwap::class.java)
            register(InterestRateSwap.State::class.java)
            register(InterestRateSwap.FixedLeg::class.java)
            register(InterestRateSwap.FloatingLeg::class.java)
            register(InterestRateSwap.Calculation::class.java)
            register(InterestRateSwap.Common::class.java)
            register(Expression::class.java)
            register(HashMap::class.java)
            register(LinkedHashMap::class.java)
            register(RatioUnit::class.java)
            register(Tenor::class.java)
            register(Tenor.TimeUnit::class.java)
            register(BusinessCalendar::class.java)
            register(Comparable::class.java)
            register(ReferenceRate::class.java)
            register(UnknownType::class.java)
            register(DayCountBasisDay::class.java)
            register(DayCountBasisYear::class.java)
            register(FixedRate::class.java)
            register(PercentageRatioUnit::class.java)
            register(BigDecimal::class.java)
            register(AccrualAdjustment::class.java)
            register(Frequency::class.java)
            register(PaymentRule::class.java)
            register(DateRollConvention::class.java)
            register(LocalDate::class.java)
            register(FixingFlow.FixingSession::class.java)
            register(FixedRatePaymentEvent::class.java)
            register(FloatingRatePaymentEvent::class.java)
        }
        return true
    }
}
