package net.corda.blobinspector

import org.apache.commons.cli.*
import java.lang.IllegalArgumentException

/**
 * Mode isn't a required property as we default it to [Mode.file]
 */
private fun modeOption() = Option("m", "mode", true, "mode, file is the default").apply {
    isRequired = false
}

/**
 *
 * Parse the command line arguments looking for the main mode into which the application is
 * being put. Note, this defaults to [Mode.file] if not set meaning we will look for a file path
 * being passed as a parameter and parse that file.
 *
 * @param args reflects the command line arguments
 *
 * @return An instantiated but unpopulated [Config] object instance suitable for the mode into
 * which we've been placed. This Config object should be populated via [loadModeSpecificOptions]
 */
fun getMode(args: Array<String>): Config {
    // For now we only care what mode we're being put in, we can build the rest of the args and parse them
    // later
    val options = Options().apply {
        addOption(modeOption())
    }

    val cmd = try {
        DefaultParser().parse(options, args, true)
    } catch (e: org.apache.commons.cli.ParseException) {
        println(e)
        HelpFormatter().printHelp("blobinspector", options)
        throw IllegalArgumentException("OH NO!!!")
    }

    return try {
        Mode.valueOf(cmd.getParsedOptionValue("m") as? String ?: "file")
    } catch (e: IllegalArgumentException) {
        Mode.file
    }.make()
}

/**
 *
 * @param config an instance of a [Config] specialisation suitable for the mode into which
 * the application has been put.
 * @param args The command line arguments
 */
fun loadModeSpecificOptions(config: Config, args: Array<String>) {
    config.apply {
        // load that modes specific command line switches, needs to include the mode option
        val modeSpecificOptions = config.options().apply {
            addOption(modeOption())
        }

        populate(try {
            DefaultParser().parse(modeSpecificOptions, args, false)
        } catch (e: org.apache.commons.cli.ParseException) {
            println("Error: ${e.message}")
            HelpFormatter().printHelp("blobinspector", modeSpecificOptions)
            System.exit(1)
            return
        })
    }
}

/**
 * Executable entry point
 */
fun main(args: Array<String>) {
    println("<<< WARNING: this tool is experimental and under active development >>>")
    getMode(args).let { mode ->
        loadModeSpecificOptions(mode, args)
        BlobHandler.make(mode)
    }.apply {
        inspectBlob(config, getBytes())
    }
}
