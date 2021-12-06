package net.corda.errorUtilities

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.errorUtilities.docsTable.DocsTableCLI
import net.corda.errorUtilities.resourceGenerator.ResourceGeneratorCLI

fun main(args: Array<String>) = ErrorTool().start(args)


/**
 * Entry point for the error utilities.
 *
 * By itself, this doesn't do anything - instead one of the subcommands should be invoked.
 */
class ErrorTool : CordaCliWrapper("error-utils", "Utilities for working with error codes and error reporting") {

    private val errorPageBuilder = DocsTableCLI()
    private val errorResourceGenerator = ResourceGeneratorCLI()

    override fun additionalSubCommands() = setOf(errorPageBuilder, errorResourceGenerator)

    override fun runProgram(): Int {
        println("No subcommand specified - please invoke one of the subcommands.")
        printHelp()
        return ExitCodes.FAILURE
    }
}