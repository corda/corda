package net.corda.irs.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.irs.api.InterestRateSwapAPI
import java.util.function.Function

class IRSPlugin : CordaPluginRegistry() {
    override val webApis = listOf(Function(::InterestRateSwapAPI))
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
}
