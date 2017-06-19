package net.corda.irs.plugin

import net.corda.irs.api.InterestRateSwapAPI
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class IRSPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::InterestRateSwapAPI))
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )
}
