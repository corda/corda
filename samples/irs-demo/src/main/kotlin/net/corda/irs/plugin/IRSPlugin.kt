package net.corda.irs.plugin

import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.irs.api.InterestRateSwapAPI
import net.corda.irs.flows.FixingFlow
import java.util.function.Function

class IRSPlugin : CordaPluginRegistry() {
    override val webApis = listOf(Function(::InterestRateSwapAPI))
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
    override val servicePlugins = listOf(Function(FixingFlow::Service))
}
