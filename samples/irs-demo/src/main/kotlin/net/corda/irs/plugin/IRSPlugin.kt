package net.corda.irs.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.finance.plugin.registerFinanceJSONMappers
import net.corda.irs.api.InterestRateSwapAPI
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class IRSPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::InterestRateSwapAPI))
    override val staticServeDirs: Map<String, String> = mapOf(
            "irsdemo" to javaClass.classLoader.getResource("irsweb").toExternalForm()
    )

    override fun customizeJSONSerialization(om: ObjectMapper): Unit = registerFinanceJSONMappers(om)
}
