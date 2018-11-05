package net.corda.node.internal.subcommands

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.utilities.loggerFor
import net.corda.node.SharedNodeCmdLineOptions
import net.corda.node.internal.initLogging
import net.corda.node.services.config.schema.v1.V1NodeConfigurationSpec
import net.corda.nodeapi.internal.config.toConfigValue
import picocli.CommandLine.Mixin
import java.nio.file.Path

internal class ValidateConfigurationCli : CliWrapperBase("validate-configuration", "Validate the configuration without starting the node.") {
    internal companion object {
        internal val logger by lazy { loggerFor<ValidateConfigurationCli>() }

        private val configRenderingOptions = ConfigRenderOptions.defaults().setFormatted(true).setComments(false).setOriginComments(false)

        internal fun logConfigurationErrors(errors: Iterable<Configuration.Validation.Error>) {
            errors.map { "Error while parsing node configuration. ${it.description()}." }.forEach(logger::error)
        }

        private fun Configuration.Validation.Error.description(): String {
            return "key: $pathAsString, message: $message"
        }

        internal fun logRawConfig(config: Config) = logger.debug("Actual configuration:\n${V1NodeConfigurationSpec.describe(config, Any::toConfigValue).render(configRenderingOptions)}")
    }

    @Mixin
    private val cmdLineOptions = SharedNodeCmdLineOptions()

    override fun initLogging() = initLogging(cmdLineOptions.baseDirectory)

    override fun runProgram(): Int {
        val rawConfig = cmdLineOptions.rawConfiguration().doIfValid(::logRawConfig).doOnErrors(cmdLineOptions::logRawConfigurationErrors).optional ?: return ExitCodes.FAILURE
        return cmdLineOptions.parseConfiguration(rawConfig).doOnErrors(::logConfigurationErrors).optional?.let { ExitCodes.SUCCESS } ?: ExitCodes.FAILURE
    }
}

internal fun SharedNodeCmdLineOptions.logRawConfigurationErrors(errors: Iterable<ConfigException>) {
    errors.forEach { error ->
        when (error) {
            is ConfigException.IO -> ValidateConfigurationCli.logger.error(configFileNotFoundMessage(configFile))
            else -> ValidateConfigurationCli.logger.error("Error while parsing node configuration.", error)
        }
    }
}

private fun configFileNotFoundMessage(configFile: Path): String {
    return """
                Unable to load the node config file from '$configFile'.

                Try setting the --base-directory flag to change which directory the node
                is looking in, or use the --config-file flag to specify it explicitly.
            """.trimIndent()
}