package net.corda.node.utilities

import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import kotlin.system.exitProcess

abstract class AbstractArgsParser<out T : Any> {
    protected val optionParser = OptionParser()
    private val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()

    /**
     * Parses the given [args] or exits the process if unable to, printing the help output to stderr.
     * If the help option is specified then the process is also shutdown after printing the help output to stdout.
     */
    fun parseOrExit(vararg args: String): T {
        val optionSet = try {
            optionParser.parse(*args)
        } catch (e: OptionException) {
            System.err.println(e.message ?: "Unable to parse arguments.")
            optionParser.printHelpOn(System.err)
            exitProcess(1)
        }
        if (optionSet.has(helpOption)) {
            optionParser.printHelpOn(System.out)
            exitProcess(0)
        }
        return doParse(optionSet)
    }

    fun parse(vararg args: String): T = doParse(optionParser.parse(*args))

    protected abstract fun doParse(optionSet: OptionSet): T
}