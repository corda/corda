package net.corda.cliutils

import net.corda.core.internal.rootMessage
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor

import org.fusesource.jansi.AnsiConsole
import org.slf4j.event.Level
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import java.util.*
import java.util.concurrent.Callable

/**
 * When we have errors in command line flags that are not handled by picocli (e.g. non existing files), an error is thrown
 * without any command line help afterwards. This can be called from run() method.
 */
interface Validated {
    companion object {
        val logger = contextLogger()
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
            logger.error(ShellConstants.RED + "Exceptions when parsing command line arguments:")
            logger.error(errors.joinToString("\n") + ShellConstants.RESET)
            CommandLine(this).usage(System.err)
            exitProcess(ExitCodes.FAILURE)
        }
    }
}

/** This is generally covered by commons-lang. */
object CordaSystemUtils {
    private const val OS_NAME = "os.name"
    private const val MAC_PREFIX = "Mac"
    private const val WIN_PREFIX = "Windows"

    fun isOsMac(): Boolean = getOsName().startsWith(MAC_PREFIX)
    fun isOsWindows(): Boolean = getOsName().startsWith(WIN_PREFIX)
    fun getOsName(): String = System.getProperty(OS_NAME)
}

object ShellConstants {
    const val RED = "\u001B[31m"
    const val RESET = "\u001B[0m"
}

fun CordaCliWrapper.start(args: Array<String>) {
    this.args = args

    // This line makes sure ANSI escapes work on Windows, where they aren't supported out of the box.
    AnsiConsole.systemInstall()

    val cmd = CommandLine(this)
    // Make sure any provided paths are absolute. Relative paths have caused issues and are less clear in logs.
    cmd.registerConverter(Path::class.java) { Paths.get(it).toAbsolutePath().normalize() }
    cmd.commandSpec.name(alias)
    cmd.commandSpec.usageMessage().description(description)
    cmd.commandSpec.parser().collectErrors(true)
    try {
        val defaultAnsiMode = if (CordaSystemUtils.isOsWindows()) {
            Help.Ansi.ON
        } else {
            Help.Ansi.AUTO
        }

        val results = cmd.parse(*args)
        val app = cmd.getCommand<CordaCliWrapper>()
        if (cmd.isUsageHelpRequested) {
            cmd.usage(System.out, defaultAnsiMode)
            exitProcess(ExitCodes.SUCCESS)
        }
        if (cmd.isVersionHelpRequested) {
            cmd.printVersionHelp(System.out, defaultAnsiMode)
            exitProcess(ExitCodes.SUCCESS)
        }
        if (app.installShellExtensionsParser.installShellExtensions) {
            System.out.println("Install shell extensions: ${app.installShellExtensionsParser.installShellExtensions}")
            // ignore any parsing errors and run the program
            exitProcess(app.call())
        }
        val allErrors = results.flatMap { it.parseResult?.errors() ?: emptyList() }
        if (allErrors.any()) {
            val parameterExceptions = allErrors.asSequence().filter { it is ParameterException }
            if (parameterExceptions.any()) {
                System.err.println("${ShellConstants.RED}${parameterExceptions.map{ it.message }.joinToString()}${ShellConstants.RESET}")
                parameterExceptions.filter { it is UnmatchedArgumentException}.forEach { (it as UnmatchedArgumentException).printSuggestions(System.out) }
                usage(cmd, System.out, defaultAnsiMode)
                exitProcess(ExitCodes.FAILURE)
            }
            throw allErrors.first()
        }
        exitProcess(app.call())
    } catch (e: Exception) {
        val throwable = e.cause ?: e
        if (this.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("${ShellConstants.RED}${throwable.rootMessage ?: "Use --verbose for more details"}${ShellConstants.RESET}")
        }
        exitProcess(ExitCodes.FAILURE)
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
abstract class CordaCliWrapper(val alias: String, val description: String) : Callable<Int> {
    companion object {
        private val logger by lazy { loggerFor<CordaCliWrapper>() }
    }

    // Raw args are provided for use in logging - this is a lateinit var rather than a constructor parameter as the class
    // needs to be parameterless for autocomplete to work.
    lateinit var args: Array<String>

    @Option(names = ["-v", "--verbose", "--log-to-console"], description = ["If set, prints logging to the console as well as to a file."])
    var verbose: Boolean = false

    @Option(names = ["--logging-level"],
            completionCandidates = LoggingLevelConverter.LoggingLevels::class,
            description = ["Enable logging at this level and higher. Possible values: \${COMPLETION-CANDIDATES}"],
            converter = [LoggingLevelConverter::class]
    )
    var loggingLevel: Level = Level.INFO

    @Mixin
    lateinit var installShellExtensionsParser: InstallShellExtensionsParser

    // This needs to be called before loggers (See: NodeStartup.kt:51 logger called by lazy, initLogging happens before).
    // Node's logging is more rich. In corda configurations two properties, defaultLoggingLevel and consoleLogLevel, are usually used.
    open fun initLogging() {
        val loggingLevel = loggingLevel.name.toLowerCase(Locale.ENGLISH)
        System.setProperty("defaultLogLevel", loggingLevel) // These properties are referenced from the XML config file.
        if (verbose) {
            System.setProperty("consoleLogLevel", loggingLevel)
        }
        System.setProperty("log-path", Paths.get(".").toString())
    }

    // Override this function with the actual method to be run once all the arguments have been parsed. The return number
    // is the exit code to be returned
    abstract fun runProgram(): Int

    override fun call(): Int {
        initLogging()
        logger.info("Application Args: ${args.joinToString(" ")}")
        installShellExtensionsParser.installOrUpdateShellExtensions(alias, this.javaClass.name)
        return runProgram()
    }
}

/**
 * Converter from String to slf4j logging Level.
 */
class LoggingLevelConverter : ITypeConverter<Level> {
    override fun convert(value: String?): Level {
        return value?.let { Level.valueOf(it.toUpperCase()) }
                ?: throw TypeConversionException("Unknown option for --logging-level: $value")
    }

    class LoggingLevels : ArrayList<String>(Level.values().map { it.toString() })
}
