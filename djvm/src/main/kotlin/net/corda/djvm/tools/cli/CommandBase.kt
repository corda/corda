package net.corda.djvm.tools.cli

import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.execution.SandboxException
import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.messages.Severity
import net.corda.djvm.rewiring.SandboxClassLoadingException
import picocli.CommandLine
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Option
import java.nio.file.Path
import java.util.concurrent.Callable

@Suppress("KDocMissingDocumentation")
abstract class CommandBase : Callable<Boolean> {

    @Option(
            names = ["-l", "--level"],
            description = ["The minimum severity level to log (TRACE, INFO, WARNING or ERROR."],
            converter = [SeverityConverter::class]
    )
    protected var level: Severity = Severity.WARNING

    @Option(
            names = ["-q", "--quiet"],
            description = ["Only print important messages to standard output."]
    )
    private var quiet: Boolean = false

    @Option(
            names = ["-v", "--verbose"],
            description = ["Enable verbose logging."]
    )
    private var verbose: Boolean = false

    @Option(
            names = ["--debug"],
            description = ["Print full stack traces upon error."]
    )
    private var debug: Boolean = false

    @Option(
            names = ["--colors"],
            description = ["Use colors when printing to terminal."]
    )
    private var useColors: Boolean = false

    @Option(
            names = ["--no-colors"],
            description = ["Do not use colors when printing to terminal."]
    )
    private var useNoColors: Boolean = false

    @Option(
            names = ["--compact"],
            description = ["Print compact errors and warnings."]
    )
    private var compact: Boolean = false

    private val ansi: Ansi
        get() = when {
            useNoColors -> Ansi.OFF
            useColors -> Ansi.ON
            else -> Ansi.AUTO
        }


    class SeverityConverter : CommandLine.ITypeConverter<Severity> {
        override fun convert(value: String): Severity {
            return try {
                when (value.toUpperCase()) {
                    "INFO" -> Severity.INFORMATIONAL
                    else -> Severity.valueOf(value.toUpperCase())
                }
            } catch (exception: Exception) {
                val candidates = Severity.values().filter { it.name.startsWith(value, true) }
                if (candidates.size == 1) {
                    candidates.first()
                } else {
                    println("ERROR: Must be one of ${Severity.values().joinToString(", ") { it.name }}")
                    Severity.INFORMATIONAL
                }
            }
        }
    }

    override fun call(): Boolean {
        if (!validateArguments()) {
            CommandLine.usage(this, System.err)
            return false
        }
        if (verbose && quiet) {
            printError("Error: Cannot set verbose and quiet modes at the same time")
            return false
        }
        return try {
            handleCommand()
        } catch (exception: Throwable) {
            printException(exception)
            false
        }
    }

    protected fun printException(exception: Throwable) = when (exception) {
        is SandboxClassLoadingException -> {
            printMessages(exception.messages)
            printError()
        }
        is SandboxException -> {
            val cause = exception.cause
            when (cause) {
                is SandboxClassLoadingException -> {
                    printMessages(cause.messages)
                    printError()
                }
                else -> {
                    if (debug) {
                        exception.exception.printStackTrace(System.err)
                    } else {
                        printError("Error: ${exception.exception.message}")
                    }
                    printError()
                }
            }
        }
        else -> {
            if (debug) {
                exception.printStackTrace(System.err)
            } else {
                printError("Error: ${exception.message}")
            }
        }
    }

    protected fun printMessages(messages: MessageCollection) {
        val sortedMessages = messages.sorted()
        val errorCount = messages.errorCount.countOf("error")
        val warningCount = messages.warningCount.countOf("warning")
        printInfo("Found $errorCount and $warningCount")
        if (!compact) {
            printInfo()
        }

        var first = true
        for (message in sortedMessages) {
            val severityColor = message.severity.color ?: "blue"
            val location = message.location.format().let {
                when {
                    it.isNotBlank() -> "in $it: "
                    else -> it
                }
            }
            if (compact) {
                printError(" - @|$severityColor ${message.severity}|@ $location${message.message}.")
            } else {
                if (!first) {
                    printError()
                }
                printError(" - @|$severityColor ${message.severity}|@ $location\n   ${message.message}.")
            }
            first = false
        }
    }

    protected open fun handleCommand(): Boolean {
        return false
    }

    protected open fun validateArguments(): Boolean {
        return false
    }

    protected fun printInfo(message: String = "") {
        if (!quiet) {
            println(ansi.Text(message).toString())
        }
    }

    protected fun printVerbose(message: String = "") {
        if (verbose) {
            println(ansi.Text(message).toString())
        }
    }

    protected fun printError(message: String = "") {
        System.err.println(ansi.Text(message).toString())
    }

    protected fun printResult(result: Any?) {
        printInfo("Execution successful")
        printInfo(" - result = $result")
        printInfo()
    }

    protected fun whitelistFromPath(whitelist: Path?): Whitelist {
        return whitelist?.let {
            if ("$it" == "NONE") {
                Whitelist.EMPTY
            } else if ("$it" == "ALL") {
                Whitelist.EVERYTHING
            } else if ("$it" == "LANG") {
                Whitelist.MINIMAL
            } else {
                try {
                    Whitelist.fromFile(file = it)
                } catch (exception: Throwable) {
                    throw Exception("Failed to load whitelist '$it'", exception)
                }
            }
        } ?: Whitelist.DEFAULT
    }

    private fun Int.countOf(suffix: String): String {
        return this.let {
            when (it) {
                0 -> "no ${suffix}s"
                1 -> "@|yellow 1|@ $suffix"
                else -> "@|yellow $it|@ ${suffix}s"
            }
        }
    }

}