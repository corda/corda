package net.corda.irs.plugin

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.irs.api.InterestRateSwapAPI
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.protocols.AutoOfferProtocol
import net.corda.irs.protocols.ExitServerProtocol
import net.corda.irs.protocols.FixingProtocol
import net.corda.irs.protocols.UpdateBusinessDayProtocol
import net.corda.protocols.TwoPartyDealProtocol
import java.time.Duration

class IRSPlugin : CordaPluginRegistry() {
    override val webApis: List<Class<*>> = listOf(InterestRateSwapAPI::class.java)
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
    override val servicePlugins: List<Class<*>> = listOf(FixingProtocol.Service::class.java)
    override val requiredProtocols: Map<String, Set<String>> = mapOf(
            Pair(AutoOfferProtocol.Requester::class.java.name, setOf(InterestRateSwap.State::class.java.name)),
            Pair(UpdateBusinessDayProtocol.Broadcast::class.java.name, setOf(java.time.LocalDate::class.java.name)),
            Pair(ExitServerProtocol.Broadcast::class.java.name, setOf(kotlin.Int::class.java.name)),
            Pair(FixingProtocol.FixingRoleDecider::class.java.name, setOf(StateRef::class.java.name, Duration::class.java.name)),
            Pair(FixingProtocol.Floater::class.java.name, setOf(Party::class.java.name, FixingProtocol.FixingSession::class.java.name)))
}
