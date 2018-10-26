package net.corda.node.internal.subcommands

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.utilities.loggerFor
import net.corda.node.SharedNodeCmdLineOptions
import net.corda.node.services.config.NodeConfiguration

internal class ValidateConfigurationCli(private val cmdLineOptions: SharedNodeCmdLineOptions) : CliWrapperBase("validate-configuration", "Validates the configuration without starting the node.") {

    internal companion object {

        private val logger = loggerFor<ValidateConfigurationCli>()

        internal fun logConfigurationErrors(errors: Iterable<Exception>) {

            errors.forEach { error ->
                logger.error("Error while parsing node configuration.", error)
            }
        }
    }

    override fun runProgram(): Int {

        val configuration = cmdLineOptions.nodeConfiguration()
        if (configuration.isInvalid) {
            logConfigurationErrors(configuration.errors)
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }
}

internal fun SharedNodeCmdLineOptions.nodeConfiguration(): Valid<NodeConfiguration> = NodeConfigurationParser.invoke(this)

private object NodeConfigurationParser : (SharedNodeCmdLineOptions) -> Valid<NodeConfiguration> {

    private val logger = loggerFor<ValidateConfigurationCli>()

    override fun invoke(cmds: SharedNodeCmdLineOptions): Valid<NodeConfiguration> {

        return attempt(cmds::rawConfiguration).doIfValid(::log).attemptMap(cmds::parseConfiguration).mapValid(::validate)
    }

    internal fun log(config: Config) = logger.debug("Actual configuration:\n${config.root().render(ConfigRenderOptions.defaults().setComments(false).setOriginComments(false).setFormatted(true))}")

    private fun validate(configuration: NodeConfiguration): Valid<NodeConfiguration> {

        return Validated.withResult(configuration, configuration.validate().asSequence().map { error -> IllegalArgumentException(error) }.toSet())
    }

    private fun <VALUE, MAPPED> Valid<VALUE>.attemptMap(convert: (VALUE) -> MAPPED): Valid<MAPPED> = mapValid { value -> attempt { convert.invoke(value) } }

    private fun <VALUE> attempt(action: () -> VALUE): Valid<VALUE> {

        return try {
            valid(action.invoke())
        } catch (exception: Exception) {
            return invalid(exception)
        }
    }
}

private typealias Valid<TARGET> = Validated<TARGET, Exception>