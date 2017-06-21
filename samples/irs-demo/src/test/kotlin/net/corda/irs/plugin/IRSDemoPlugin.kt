package net.corda.irs.plugin

import net.corda.irs.api.InterestRatesSwapDemoAPI
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class IRSDemoPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::InterestRatesSwapDemoAPI))
}