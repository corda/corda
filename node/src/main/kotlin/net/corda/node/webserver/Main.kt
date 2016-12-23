package net.corda.node.webserver

import joptsimple.OptionParser
import net.corda.node.driver.driver
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // TODO: Print basic webserver info
    val parser = OptionParser()
    val baseDirectoryArg = parser.accepts("base-directory", "The directory to put all files under").withRequiredArg()
    val webAddressArg = parser.accepts("web-address", "The web address for this server to bind").withOptionalArg()
    val logToConsoleArg = parser.accepts("log-to-console", "If set, prints logging to the console as well as to a file.")
    val helpArg = parser.accepts("help").forHelp()
    val cmdlineOptions = try {
        parser.parse(*args)
    } catch (ex: Exception) {
        println("Unknown command line arguments: ${ex.message}")
        exitProcess(1)
    }

    // Maybe render command line help.
    if (cmdlineOptions.has(helpArg)) {
        parser.printHelpOn(System.out)
        exitProcess(0)
    }

    // Set up logging.
    if (cmdlineOptions.has(logToConsoleArg)) {
        // This property is referenced from the XML config file.
        System.setProperty("consoleLogLevel", "info")
    }

    val baseDirectoryPath = Paths.get(cmdlineOptions.valueOf(baseDirectoryArg))
    val config = ConfigHelper.loadConfig(baseDirectoryPath)

    println("Starting server")
    val nodeConf = FullNodeConfiguration(baseDirectoryPath, config)
    val server = WebServer(nodeConf).start()
    println("Exiting")
}