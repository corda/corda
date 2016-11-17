package net.corda.vega.services

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.utilities.loggerFor
import net.corda.vega.api.PortfolioApi
import net.corda.vega.contracts.SwapData
import net.corda.vega.protocols.IRSTradeProtocol
import net.corda.vega.protocols.SimmProtocol
import net.corda.vega.protocols.SimmRevaluation
import java.time.LocalDate

/**
 * [SimmService] is the object that makes available the protocols and services for the Simm agreement / evaluation protocol
 * It also enables a human usable web service for demo purposes - if available.
 * It is loaded via discovery - see [CordaPluginRegistry]
 */
object SimmService {
    class Plugin : CordaPluginRegistry() {
        override val webApis: List<Class<*>> = listOf(PortfolioApi::class.java)
        override val requiredProtocols: Map<String, Set<String>> = mapOf(
                SimmProtocol.Requester::class.java.name to setOf(Party::class.java.name, LocalDate::class.java.name),
                SimmRevaluation.Initiator::class.java.name to setOf(StateRef::class.java.name, LocalDate::class.java.name),
                IRSTradeProtocol.Requester::class.java.name to setOf(SwapData::class.java.name, Party::class.java.name))
        override val servicePlugins: List<Class<*>> = listOf(
                SimmProtocol.Service::class.java,
                IRSTradeProtocol.Service::class.java)
        override val staticServeDirs: Map<String, String> = mapOf("simmvaluationdemo" to javaClass.classLoader.getResource("simmvaluationweb").toExternalForm())
    }
}
