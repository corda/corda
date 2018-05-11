package com.r3.corda.networkmanage.common.utils

import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import java.io.PrintStream
import kotlin.system.exitProcess

// TODO: This class could be useful for the rest of the codebase.
abstract class ArgsParser<out T : Any> {
    protected val optionParser = OptionParser()
    private val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()

    /**
    If [printHelpOn] output stream is not null, this method will print help message and exit process
    when encountered any error during args parsing, or when help flag is set.
     */
    fun parseOrExit(vararg args: String, printHelpOn: PrintStream? = System.out): T {
        val optionSet = try {
            optionParser.parse(*args)
        } catch (e: OptionException) {
            printHelpOn?.let {
                it.println(e.message ?: "Unable to parse arguments.")
                optionParser.printHelpOn(it)
                exitProcess(1)
            }
            throw e
        }

        if (optionSet.has(helpOption)) {
            printHelpOn?.let(optionParser::printHelpOn)
            exitProcess(0)
        }

        return try {
            parse(optionSet)
        } catch (e: RuntimeException) {
            // We handle errors from the parsing of the command line arguments as a runtime
            // exception because the joptsimple library is overly helpful and doesn't expose
            // parsing / conversion exceptions in a way that makes reporting the message out
            // to the user possible. Thus, that library is re-throwing those exceptions as simple
            // runtime exceptions with a modified cause to preserve the error location
            printHelpOn?.let {
                it.println("ERROR: ${e.message ?: "Unable to parse arguments."}")
                exitProcess(2)
            }
            throw e
        }
    }

    protected abstract fun parse(optionSet: OptionSet): T
}
