package net.corda.tools.error.codes.server.web.endpoints

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec

internal interface EndpointConfiguration {

    val name: String
    val path: String
    val enabled: Boolean
}

internal abstract class EndpointConfigProvider(sectionPath: String, applyConfigStandards: (Config) -> Config) : EndpointConfiguration {

    private val spec = EndpointConfSpec(sectionPath)

    private val config = applyConfigStandards.invoke(Config { addSpec(spec) })

    // TODO sollecitom add validation
    override val name: String = config[spec.name]
    override val path: String = config[spec.path]
    override val enabled: Boolean = config[spec.enabled]

    private class EndpointConfSpec(sectionPath: String) : ConfigSpec(sectionPath) {

        val name by required<String>()
        val path by required<String>()
        val enabled by optional(true)
    }
}