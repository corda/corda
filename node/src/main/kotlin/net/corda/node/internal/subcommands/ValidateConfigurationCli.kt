package net.corda.node.internal.subcommands

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.common.validation.internal.Validated
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.utilities.loggerFor
import net.corda.node.SharedNodeCmdLineOptions
import net.corda.node.services.config.NodeConfiguration
import java.lang.IllegalArgumentException

internal class ValidateConfigurationCli(private val cmdLineOptions: SharedNodeCmdLineOptions) : CliWrapperBase("validate-configuration", "Validates the configuration without starting the node.") {

    private companion object {

        private val logger = loggerFor<ValidateConfigurationCli>()
    }

    override fun runProgram(): Int {

        val configuration = parseConfiguration()
        if (configuration.isInvalid) {
            logger.error("Invalid node configuration. Errors were:${System.lineSeparator()}${configuration.errorsDescription()}")
            return ExitCodes.FAILURE
        }
        return ExitCodes.SUCCESS
    }

    fun parseConfiguration(): Valid<NodeConfiguration> {

       return attempt(cmdLineOptions::rawConfiguration).attemptMap(cmdLineOptions::parseConfiguration).mapValid(::validate)
    }

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

    private fun Validated<*, Exception>.errorsDescription(): String {

        return errors.asSequence().map(Exception::message).joinToString(System.lineSeparator())
    }

    // TODO sollecitom do not duplicate the logic in NodeStartup. Call a function here from NodeStartup
}

internal typealias Valid<TARGET> = Validated<TARGET, Exception>