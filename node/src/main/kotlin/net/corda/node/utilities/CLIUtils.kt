package net.corda.node.utilities

import com.jcabi.manifests.Manifests
import net.corda.core.internal.exists
import net.corda.core.internal.isReadable
import net.corda.core.utilities.contextLogger
import org.apache.logging.log4j.Level
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Path
import kotlin.system.exitProcess
import java.util.*

/**
 * Something heavily used in network services, I am not sure it's of much use in corda, but who knows. Definitely it was the key to making DevOps happy.
 * Add it as
 * `@CommandLine.Mixin
 * lateinit var configParser: ConfigFilePathArgsParser`
 *
 * in your command class and then validate()
 */
@Command(description = ["Parse configuration file. Checks if given configuration file exists"])
class ConfigFilePathArgsParser : Validated {
    @Option(names = ["--config-file", "-f"], required = true, paramLabel = "FILE", description = ["The path to the config file"])
    lateinit var configFile: Path

    override fun validator(): List<String> {
        val res = mutableListOf<String>()
        if(!configFile.exists()) res += "Config file ${configFile.toAbsolutePath().normalize()} does not exist!"
        if(!configFile.isReadable) res += "Config file ${configFile.toAbsolutePath().normalize()} is not readable"
        return res
    }
}

/**
 * Simple version printing when command is called with --version or -V flag. Assuming that we reuse Corda-Release-Version and Corda-Revision
 * in the manifest file.
 */
class CordaVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> {
        return if (Manifests.exists("Corda-Release-Version") && Manifests.exists("Corda-Revision")) {
            arrayOf("Version: ${Manifests.read("Corda-Release-Version")}", "Revision: ${Manifests.read("Corda-Revision")}")
        } else {
            arrayOf("No version data is available in the MANIFEST file.")
        }
    }
}

/**
 * Usually when we have errors in some command line flags that are not handled by picocli (e.g. non existing file). Error is thrown
 * and no CommandLine help afterwards. This can be called from run() method.
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

/**
 * Simple base class for handling help, version, verbose and logging-level commands.
 * As versionProvider information from the MANIFEST file is used. It can be overwritten by custom version providers (see: Node)
 * Picocli will prioritise versionProvider from the `@Command` annotation on the subclass, see: https://picocli.info/#_reuse_combinations
 */
@Command(mixinStandardHelpOptions = true,
        versionProvider = CordaVersionProvider::class,
        sortOptions = false,
        synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
        descriptionHeading = "%n@|bold,underline Description|@:%n%n",
        parameterListHeading = "%n@|bold,underline Parameters|@:%n%n",
        optionListHeading = "%n@|bold,underline Options|@:%n%n",
        commandListHeading = "%n@|bold,underline Commands|@:%n%n")
abstract class ArgsParser {
    @Option(names = [ "-v", "--verbose" ], description = ["If set, prints logging to the console as well as to a file."])
    var verbose: Boolean = false

    @Option(names = ["--logging-level"],
            // TODO For some reason I couldn't make picocli COMPLETION-CANDIDATES work
            description = ["Enable logging at this level and higher. Defaults to INFO. Possible values: OFF, INFO, WARN, TRACE, DEBUG, ERROR, FATAL, ALL"],
            converter = [LoggingLevelConverter::class])
    var loggingLevel: Level = Level.INFO

    // This needs to be called before loggers (See: NodeStartup.kt:51 logger called by lazy, initLogging happens before).
    // Node's logging is more rich. In corda configurations two properties, defaultLoggingLevel and consoleLogLevel, are usually used.
    protected open fun initLogging() {
        val loggingLevel = loggingLevel.name().toLowerCase(Locale.ENGLISH)
        System.setProperty("defaultLogLevel", loggingLevel) // These properties are referenced from the XML config file.
        if (verbose) {
            System.setProperty("consoleLogLevel", loggingLevel)
        }
    }
}

/**
 * Converter from String to log4j logging Level.
 */
class LoggingLevelConverter : ITypeConverter<Level> {
    override fun convert(value: String?): Level {
        return value?.let { Level.getLevel(it) } ?: throw TypeConversionException("Unknown option for --logging-level: $value")
    }
}
