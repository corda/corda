package net.corda.errorPageBuilder

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start

fun main(args: Array<String>) {
    val tool = ErrorTool()
    tool.start(args)
}

/**
 * Entry point for the error utilities.
 *
 * By itself, this doesn't do anything - instead one of the subcommands should be invoked.
 */
class ErrorTool : CordaCliWrapper("error-utils", "Utilities for working with error codes and error reporting") {

    private val errorPageBuilder = ErrorPageBuilder()
    private val errorResourceGenerator = ErrorResourceGenerator()

    override fun additionalSubCommands() = setOf(errorPageBuilder, errorResourceGenerator)

    override fun runProgram(): Int {
        println("No subcommand specified - please invoke one of the subcommands.")
        printHelp()
        return ExitCodes.FAILURE
    }
}