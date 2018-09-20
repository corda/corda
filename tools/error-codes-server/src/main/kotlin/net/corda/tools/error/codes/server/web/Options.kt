package net.corda.tools.error.codes.server.web

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import javax.inject.Named

@Named
internal class Options : WebServer.Options {

    private companion object {

        private const val CONFIGURATION_FILE_NAME = "configuration.yml"

        private object Spec : ConfigSpec("config.web.server") {

            val port by required<Int>()
        }
    }

    private val config = Config { addSpec(Spec) }.from.yaml.resource(CONFIGURATION_FILE_NAME).from.env().from.systemProperties()

    // TODO sollecitom add validation
    override val port: Port = Port(config[Spec.port])
}