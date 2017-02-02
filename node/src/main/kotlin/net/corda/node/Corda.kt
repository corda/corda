@file:JvmName("Corda")
package net.corda.node

import com.typesafe.config.ConfigException
import net.corda.core.*
import net.corda.core.utilities.Emoji
import net.corda.node.internal.Node
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.utilities.ANSIProgressObserver
import net.corda.node.webserver.WebServer
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private var renderBasicInfoToConsole = true

/** Used for useful info that we always want to show, even when not logging to the console */
fun printBasicNodeInfo(description: String, info: String? = null) {
    if (renderBasicInfoToConsole) {
        val msg = if (info == null) description else "${description.padEnd(40)}: $info"
        println(msg)
    } else {
        val msg = if (info == null) description else "$description: $info"
        LoggerFactory.getLogger("Main").info(msg)
    }
}

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    checkJavaVersion()

    val argsParser = ArgsParser()

    val cmdlineOptions = try {
        argsParser.parse(*args)
    } catch (ex: Exception) {
        println("Unknown command line arguments: ${ex.message}")
        exitProcess(1)
    }

    // Maybe render command line help.
    if (cmdlineOptions.help) {
        argsParser.printHelp(System.out)
        exitProcess(0)
    }

    // Set up logging.
    if (cmdlineOptions.logToConsole) {
        // This property is referenced from the XML config file.
        System.setProperty("consoleLogLevel", "info")
        renderBasicInfoToConsole = false
    }

    drawBanner()

    val logDir = if (cmdlineOptions.isWebserver) "logs/web" else "logs"
    System.setProperty("log-path", (cmdlineOptions.baseDirectory / logDir).toString())

    val log = LoggerFactory.getLogger("Main")
    printBasicNodeInfo("Logs can be found in", System.getProperty("log-path"))

    val conf = try {
        FullNodeConfiguration(cmdlineOptions.baseDirectory, cmdlineOptions.loadConfig())
    } catch (e: ConfigException) {
        println("Unable to load the configuration file: ${e.rootCause.message}")
        exitProcess(2)
    }

    log.info("Main class: ${FullNodeConfiguration::class.java.protectionDomain.codeSource.location.toURI().path}")
    val info = ManagementFactory.getRuntimeMXBean()
    log.info("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
    log.info("Application Args: ${args.joinToString(" ")}")
    log.info("bootclasspath: ${info.bootClassPath}")
    log.info("classpath: ${info.classPath}")
    log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.info("Machine: ${InetAddress.getLocalHost().hostName}")
    log.info("Working Directory: ${cmdlineOptions.baseDirectory}")
    if(cmdlineOptions.isWebserver) {
        log.info("Starting as webserver on ${conf.webAddress}")
    } else {
        log.info("Starting as node on ${conf.artemisAddress}")
    }

    try {
        cmdlineOptions.baseDirectory.createDirectories()

        // TODO: Webserver should be split and start from inside a WAR container
        if  (!cmdlineOptions.isWebserver) {
            val node = conf.createNode()
            node.start()
            printPluginsAndServices(node)

            node.networkMapRegistrationFuture.success {
                val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
                printBasicNodeInfo("Node started up and registered in $elapsed sec")

                if (renderBasicInfoToConsole)
                    ANSIProgressObserver(node.smm)
            } failure {
                log.error("Error during network map registration", it)
                exitProcess(1)
            }
            node.run()
        } else {
            val server = WebServer(conf)
            server.start()
            val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
            printBasicNodeInfo("Webserver started up in $elapsed sec")
            server.run()
        }
    } catch (e: Exception) {
        log.error("Exception during node startup", e)
        exitProcess(1)
    }
    exitProcess(0)
}

private fun checkJavaVersion() {
    // Check we're not running a version of Java with a known bug: https://github.com/corda/corda/issues/83
    try {
        Paths.get("").normalize()
    } catch (e: ArrayIndexOutOfBoundsException) {
        println("""
You are using a version of Java that is not supported (${System.getProperty("java.version")}). Please upgrade to the latest version.
Corda will now exit...""")
        exitProcess(1)
    }
}

private fun printPluginsAndServices(node: Node) {
    node.configuration.extraAdvertisedServiceIds.let { if (it.isNotEmpty()) printBasicNodeInfo("Providing network services", it) }
    val plugins = node.pluginRegistries
            .map { it.javaClass.name }
            .filterNot { it.startsWith("net.corda.node.") || it.startsWith("net.corda.core.") }
            .map { it.substringBefore('$') }
    if (plugins.isNotEmpty())
        printBasicNodeInfo("Loaded plugins", plugins.joinToString())
}

private fun messageOfTheDay(): Pair<String, String> {
    val messages = arrayListOf(
            "The only distributed ledger that pays\nhomage to Pac Man in its logo.",
            "You know, I was a banker once ...\nbut I lost interest. ${Emoji.bagOfCash}",
            "It's not who you know, it's who you know\nknows what you know you know.",
            "It runs on the JVM because QuickBasic\nis apparently not 'professional' enough.",
            "\"It's OK computer, I go to sleep after\ntwenty minutes of inactivity too!\"",
            "It's kind of like a block chain but\ncords sounded healthier than chains.",
            "Computer science and finance together.\nYou should see our crazy Christmas parties!"
    )
    if (Emoji.hasEmojiTerminal)
        messages +=
            "Kind of like a regular database but\nwith emojis, colours and ascii art. ${Emoji.coolGuy}"
    val (a, b) = messages.randomOrNull()!!.split('\n')
    return Pair(a, b)
}

private fun drawBanner() {
    // This line makes sure ANSI escapes work on Windows, where they aren't supported out of the box.
    AnsiConsole.systemInstall()

    val (msg1, msg2) = Emoji.renderIfSupported { messageOfTheDay() }

    println(Ansi.ansi().fgBrightRed().a(
"""
   ______               __
  / ____/     _________/ /___ _
 / /     __  / ___/ __  / __ `/         """).fgBrightBlue().a(msg1).newline().fgBrightRed().a(
"/ /___  /_/ / /  / /_/ / /_/ /          ").fgBrightBlue().a(msg2).newline().fgBrightRed().a(
"""\____/     /_/   \__,_/\__,_/""").reset().newline().newline().fgBrightDefault().bold().
a("--- DEVELOPER SNAPSHOT ------------------------------------------------------------").newline().reset())
}
