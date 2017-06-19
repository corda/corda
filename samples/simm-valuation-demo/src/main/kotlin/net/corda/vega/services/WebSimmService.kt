package net.corda.vega.services

import net.corda.vega.api.PortfolioApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

/**
 * [WebSimmService] is the object that enables a human usable web service for demo purposes - if available.
 * It is loaded via discovery - see [WebServerPluginRegistry]
 */
object WebSimmService {
    class Plugin : WebServerPluginRegistry() {
        override val webApis = listOf(Function(::PortfolioApi))
        override val staticServeDirs: Map<String, String> = mapOf("simmvaluationdemo" to javaClass.classLoader.getResource("simmvaluationweb").toExternalForm())
    }
}
