package net.corda.irs.plugin

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.irs.api.InterestRateSwapAPI
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.flows.AutoOfferFlow
import net.corda.irs.flows.ExitServerFlow
import net.corda.irs.flows.FixingFlow
import net.corda.irs.flows.UpdateBusinessDayFlow
import java.time.Duration

class IRSPlugin : CordaPluginRegistry() {
    override val webApis: List<Class<*>> = listOf(InterestRateSwapAPI::class.java)
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
    override val servicePlugins: List<Class<*>> = listOf(FixingFlow.Service::class.java)
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            Pair(AutoOfferFlow.Requester::class.java.name, setOf(InterestRateSwap.State::class.java.name)),
            Pair(UpdateBusinessDayFlow.Broadcast::class.java.name, setOf(java.time.LocalDate::class.java.name)),
            Pair(ExitServerFlow.Broadcast::class.java.name, setOf(kotlin.Int::class.java.name)),
            Pair(FixingFlow.FixingRoleDecider::class.java.name, setOf(StateRef::class.java.name, Duration::class.java.name)),
            Pair(FixingFlow.Floater::class.java.name, setOf(Party::class.java.name, FixingFlow.FixingSession::class.java.name)))
}
