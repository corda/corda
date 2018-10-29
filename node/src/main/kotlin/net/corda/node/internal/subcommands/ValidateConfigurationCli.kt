package net.corda.node.internal.subcommands

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.utilities.loggerFor
import net.corda.node.SharedNodeCmdLineOptions
import net.corda.node.internal.initLogging
import net.corda.node.services.config.NodeConfiguration
import picocli.CommandLine.*
import java.nio.file.Path

internal class ValidateConfigurationCli : CliWrapperBase("validate-configuration", "Validate the configuration without starting the node.") {
    internal companion object {
        private val logger = loggerFor<ValidateConfigurationCli>()

        internal fun logConfigurationErrors(errors: Iterable<Exception>, configFile: Path) {
            errors.forEach { error ->
                when (error) {
                    is ConfigException.IO -> logger.error(configFileNotFoundMessage(configFile))
                    else -> logger.error("Error while parsing node configuration.", error)
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
    }

    @Mixin
    private val cmdLineOptions = SharedNodeCmdLineOptions()

    override fun initLogging() = initLogging(cmdLineOptions.baseDirectory)

    override fun runProgram(): Int {
        val configuration = cmdLineOptions.nodeConfiguration()
        if (configuration.isInvalid) {
            logConfigurationErrors(configuration.errors, cmdLineOptions.configFile)
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }
}

internal fun SharedNodeCmdLineOptions.nodeConfiguration(): Valid<NodeConfiguration> = NodeConfigurationParser.invoke(this)

private object NodeConfigurationParser : (SharedNodeCmdLineOptions) -> Valid<NodeConfiguration> {
    private val logger = loggerFor<ValidateConfigurationCli>()

    private val configRenderingOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false).setFormatted(true)

    override fun invoke(cmds: SharedNodeCmdLineOptions): Valid<NodeConfiguration> {
        return attempt(cmds::rawConfiguration).doIfValid(::log).mapValid(cmds::parseConfiguration).mapValid(::validate)
    }

    internal fun log(config: Config) = logger.debug("Actual configuration:\n${config.root().render(configRenderingOptions)}")

    private fun validate(configuration: NodeConfiguration): Valid<NodeConfiguration> {
        return Validated.withResult(configuration, configuration.validate().asSequence().map { error -> IllegalArgumentException(error) }.toSet())
    }

    private fun <VALUE> attempt(action: () -> VALUE): Valid<VALUE> {
        return try {
            valid(action.invoke())
        } catch (exception: Exception) {
            return invalid(exception)
        }
    }
}

private typealias Valid<TARGET> = Validated<TARGET, Exception>