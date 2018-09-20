package net.corda.tools.error.codes.server.web

import com.uchuhimo.konf.Config
import javax.inject.Named

@Named
internal class ConfigStandardsDecorator : (Config) -> Config {

    private companion object {

        private const val CONFIGURATION_FILE_NAME = "configuration.yml"
    }

    override fun invoke(config: Config): Config = config.from.yaml.resource(CONFIGURATION_FILE_NAME).from.env().from.systemProperties()
}