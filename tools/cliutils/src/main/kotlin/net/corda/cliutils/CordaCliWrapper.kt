package net.corda.cliutils

import net.corda.core.internal.rootMessage
import net.corda.core.utilities.contextLogger

import org.apache.logging.log4j.Level
import picocli.CommandLine
import picocli.CommandLine.*
import kotlin.system.exitProcess
import java.util.*

/**
 * When we have errors in command line flags that are not handled by picocli (e.g. non existing files), an error is thrown
 * without any command line help afterwards. This can be called from run() method.
 */
interface Validated {
    companion object {
        val logger = contextLogger()
        const val RED = "\u001B[31m"
        const val RESET = "\u001B[0m"
    }

    /**
     * Check that provided command line parameters are valid, e.g. check file existence. Return list of error strings.
     */
    fun validator(): List<String>

    /**
     * Function that provides nice error handing of command line validation.
     */
    fun validate() {
        val errors = validator()
        if (errors.isNotEmpty()) {
            logger.error(RED + "Exceptions when parsing command line arguments:")
            logger.error(errors.joinToString("\n") + RESET)
            CommandLine(this).usage(System.err)
            exitProcess(1)
        }
    }
}


fun CordaCliWrapper.start(vararg args: String) {
    val cmd = CommandLine(this)
    cmd.commandSpec.name(alias)
    cmd.commandSpec.usageMessage().description(description)
    try {
        cmd.parseWithHandlers(RunLast().useOut(System.out).useAnsi(Help.Ansi.AUTO),
                DefaultExceptionHandler<List<Any>>().useErr(System.err).useAnsi(Help.Ansi.AUTO),
                *args)
    } catch (e: ExecutionException) {
        val throwable = e.cause ?: e
        if (this.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("*ERROR*: ${throwable.rootMessage ?: "Use --verbose for more details"}")
        }
        exitProcess(1)
    }
}

/**
 * Simple base class for handling help, version, verbose and logging-level commands.
 * As versionProvider information from the MANIFEST file is used. It can be overwritten by custom version providers (see: Node)
 * Picocli will prioritise versionProvider from the `@Command` annotation on the subclass, see: https://picocli.info/#_reuse_combinations
 */
@Command(mixinStandardHelpOptions = true,
        versionProvider = CordaVersionProvider::class,
        sortOptions = false,
        showDefaultValues = true,
        synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
        descriptionHeading = "%n@|bold,underline Description|@:%n%n",
        parameterListHeading = "%n@|bold,underline Parameters|@:%n%n",
        optionListHeading = "%n@|bold,underline Options|@:%n%n",
        commandListHeading = "%n@|bold,underline Commands|@:%n%n")
abstract class CordaCliWrapper(val alias: String, val description: String) : Runnable {
    @Option(names = ["-v", "--verbose"], description = ["If set, prints logging to the console as well as to a file."])
    var verbose: Boolean = false

    @Option(names = ["--logging-level"],
            // TODO For some reason I couldn't make picocli COMPLETION-CANDIDATES work
            description = ["Enable logging at this level and higher. Defaults to INFO. Possible values: OFF, INFO, WARN, TRACE, DEBUG, ERROR, FATAL, ALL"],
            converter = [LoggingLevelConverter::class])
    var loggingLevel: Level = Level.INFO

    @Mixin
    private lateinit var installShellExtensionsParser: InstallShellExtensionsParser

    // This needs to be called before loggers (See: NodeStartup.kt:51 logger called by lazy, initLogging happens before).
    // Node's logging is more rich. In corda configurations two properties, defaultLoggingLevel and consoleLogLevel, are usually used.
    private fun initLogging() {
        val loggingLevel = loggingLevel.name().toLowerCase(Locale.ENGLISH)
        System.setProperty("defaultLogLevel", loggingLevel) // These properties are referenced from the XML config file.
        if (verbose) {
            System.setProperty("consoleLogLevel", loggingLevel)
        }
    }

    // Override this function with the actual method to be run once all the arguments have been parsed
    abstract fun invoke()

    override fun run() {
        installShellExtensionsParser.installOrUpdateShellExtensions(alias)
        initLogging()
        invoke()
    }
}

/**
 * Converter from String to log4j logging Level.
 */
class LoggingLevelConverter : ITypeConverter<Level> {
    override fun convert(value: String?): Level {
        return value?.let { Level.getLevel(it) }
                ?: throw TypeConversionException("Unknown option for --logging-level: $value")
    }
}
