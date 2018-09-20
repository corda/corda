package net.corda.tools.error.codes.server.web

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import net.corda.tools.error.codes.server.commons.web.Port
import javax.inject.Inject
import javax.inject.Named

@Named
internal class Options @Inject constructor(applyConfigStandards: (Config) -> Config) : WebServer.Options {

    private companion object {

        private const val CONFIGURATION_SECTION_PATH = "configuration.web.server"

        private object Spec : ConfigSpec(CONFIGURATION_SECTION_PATH) {

            val port by required<Int>()
        }
    }

    private val config = applyConfigStandards.invoke(Config { addSpec(Spec) })

    // TODO sollecitom add validation
    override val port: Port = Port(config[Spec.port])
}