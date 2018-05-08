package net.corda.blobinspector

import org.apache.commons.cli.CommandLine
import net.corda.core.serialization.SerializedBytes
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

/**
 * Enumeration of the modes in which the blob inspector can be run.
 *
 * @property make lambda function that takes no parameters and returns a specific instance of the configuration
 * object for that mode.
 *
 * @property options A lambda function that takes no parameters and returns an [Options] instance that define
 * the command line flags related to this mode. For example ``file`` mode would have an option to pass in
 * the name of the file to read.
 *
 */
enum class Mode(
        val make : () -> Config,
        val options : (Options) -> Unit
) {
    file(
            {
                FileConfig(Mode.file)
            },
            { o ->
                o.apply{
                    addOption(
                            Option ("f", "file", true, "path to file").apply {
                                isRequired = true
                            }
                    )
                }
            }
    ),
    inMem(
            {
                InMemoryConfig(Mode.inMem)
            },
            {
                // The in memory only mode has no specific option assocaited with it as it's intended for
                // testing purposes only within the unit test framework and not use on the command line
            }
    )
}

/**
 * Configuration data class for  the Blob Inspector.
 *
 * @property mode
 */
abstract class Config (val mode: Mode) {
    var schema: Boolean = false
    var transforms: Boolean = false
    var data: Boolean = false
    var verbose: Boolean = false

    abstract fun populateSpecific(cmdLine: CommandLine)
    abstract fun withVerbose() : Config

    fun populate(cmdLine: CommandLine) {
        schema = cmdLine.hasOption('s')
        transforms = cmdLine.hasOption('t')
        data = cmdLine.hasOption('d')
        verbose = cmdLine.hasOption('v')

        populateSpecific(cmdLine)
    }

    fun options() = Options().apply {
        // install generic options
        addOption(Option("s", "schema", false, "print the blob's schema").apply {
            isRequired = false
        })

        addOption(Option("t", "transforms", false, "print the blob's transforms schema").apply {
            isRequired = false
        })

        addOption(Option("d", "data", false, "Display the serialised data").apply {
            isRequired = false
        })

        addOption(Option("v", "verbose", false, "Enable debug output").apply {
            isRequired = false
        })

        // install the mode specific options
        mode.options(this)
    }
}


/**
 * Configuration object when running in "File" mode, i.e. the object has been specified at
 * the command line
 */
class FileConfig (
        mode: Mode
) : Config(mode) {

    var file: String = "unset"

    override fun populateSpecific(cmdLine : CommandLine) {
        file = cmdLine.getParsedOptionValue("f") as String
    }

    override fun withVerbose() : FileConfig {
        return FileConfig(mode).apply {
            this.schema = schema
            this.transforms = transforms
            this.data = data
            this.verbose = true
        }
    }
}


/**
 * Placeholder config objet used when running unit tests and the inspected blob is being fed in
 * via some mechanism directly. Normally this will be the direct serialisation of an object in a unit
 * test and then dumping that blob into the inspector for visual comparison of the output
 */
class InMemoryConfig (
        mode: Mode
) : Config(mode) {
    var blob: SerializedBytes<*>? = null

    override fun populateSpecific(cmdLine: CommandLine) {
        throw UnsupportedOperationException("In memory config is for testing only and cannot set specific flags")
    }

    override fun withVerbose(): Config {
        throw UnsupportedOperationException("In memory config is for testing headlessly, cannot be verbose")
    }
}
