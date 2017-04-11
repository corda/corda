@file:JvmName("Corda")

package net.corda.node

import com.jcabi.manifests.Manifests
import com.typesafe.config.ConfigException
import joptsimple.OptionException
import net.corda.core.*
import net.corda.core.node.NodeVersionInfo
import net.corda.core.node.Version
import net.corda.core.utilities.Emoji
import net.corda.node.internal.Node
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.shell.InteractiveShell
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private var renderBasicInfoToConsole = true

/** Used for useful info that we always want to show, even when not logging to the console */
fun printBasicNodeInfo(description: String, info: String? = null) {
    val msg = if (info == null) description else "${description.padEnd(40)}: $info"
    val loggerName = if (renderBasicInfoToConsole) "BasicInfo" else "Main"
    LoggerFactory.getLogger(loggerName).info(msg)
}

val LOGS_DIRECTORY_NAME = "logs"

fun main(args: Array<String>) {
    val startTime = System.currentTimeMillis()
    checkJavaVersion()

    val argsParser = ArgsParser()

    val cmdlineOptions = try {
        argsParser.parse(*args)
    } catch (ex: OptionException) {
        println("Invalid command line arguments: ${ex.message}")
        argsParser.printHelp(System.out)
        exitProcess(1)
    }

    // Set up logging. These properties are referenced from the XML config file.
    val loggingLevel = cmdlineOptions.loggingLevel.name.toLowerCase()
    System.setProperty("defaultLogLevel", loggingLevel)
    if (cmdlineOptions.logToConsole) {
        System.setProperty("consoleLogLevel", loggingLevel)
        renderBasicInfoToConsole = false
    }

    // Manifest properties are only available if running from the corda jar
    fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

    val nodeVersionInfo = NodeVersionInfo(
            manifestValue("Corda-Version")?.let { Version.parse(it) } ?: Version(0, 0, 0, false),
            manifestValue("Corda-Revision") ?: "Unknown",
            manifestValue("Corda-Vendor") ?: "Unknown"
    )

    if (cmdlineOptions.isVersion) {
        println("${nodeVersionInfo.vendor} ${nodeVersionInfo.version}")
        println("Revision ${nodeVersionInfo.revision}")
        exitProcess(0)
    }

    // Maybe render command line help.
    if (cmdlineOptions.help) {
        argsParser.printHelp(System.out)
        exitProcess(0)
    }

    drawBanner(nodeVersionInfo)

    val dir: Path = cmdlineOptions.baseDirectory
    System.setProperty("log-path", (dir / "logs").toString())

    val log = LoggerFactory.getLogger("Main")
    printBasicNodeInfo("Logs can be found in", System.getProperty("log-path"))

    val conf = try {
        cmdlineOptions.loadConfig()
    } catch (e: ConfigException) {
        println("Unable to load the configuration file: ${e.rootCause.message}")
        exitProcess(2)
    }

    if (cmdlineOptions.isRegistration) {
        println()
        println("******************************************************************")
        println("*                                                                *")
        println("*       Registering as a new participant with Corda network      *")
        println("*                                                                *")
        println("******************************************************************")
        NetworkRegistrationHelper(conf, HTTPNetworkRegistrationService(conf.certificateSigningService)).buildKeystore()
        exitProcess(0)
    }

    log.info("Version: ${nodeVersionInfo.version}")
    log.info("Vendor: ${nodeVersionInfo.vendor}")
    log.info("Revision: ${nodeVersionInfo.revision}")
    val info = ManagementFactory.getRuntimeMXBean()
    log.info("PID: ${info.name.split("@").firstOrNull()}")  // TODO Java 9 has better support for this
    log.info("Main class: ${FullNodeConfiguration::class.java.protectionDomain.codeSource.location.toURI().path}")
    log.info("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
    log.info("Application Args: ${args.joinToString(" ")}")
    log.info("bootclasspath: ${info.bootClassPath}")
    log.info("classpath: ${info.classPath}")
    log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
    log.info("Machine: ${InetAddress.getLocalHost().hostName}")
    log.info("Working Directory: ${cmdlineOptions.baseDirectory}")
    val agentProperties = sun.misc.VMSupport.getAgentProperties()
    if (agentProperties.containsKey("sun.jdwp.listenerAddress")) {
        log.info("Debug port: ${agentProperties.getProperty("sun.jdwp.listenerAddress")}")
    }
    log.info("Starting as node on ${conf.p2pAddress}")

    try {
        cmdlineOptions.baseDirectory.createDirectories()

        val node = conf.createNode(nodeVersionInfo)
        node.start()
        printPluginsAndServices(node)

        node.networkMapRegistrationFuture.success {
            val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
            printBasicNodeInfo("Node for \"${node.info.legalIdentity.name}\" started up and registered in $elapsed sec")

            // Don't start the shell if there's no console attached.
            val runShell = !cmdlineOptions.noLocalShell && System.console() != null
            node.startupComplete then {
                try {
                    InteractiveShell.startShell(dir, runShell, cmdlineOptions.sshdServer, node)
                } catch(e: Throwable) {
                    log.error("Shell failed to start", e)
                }
            }
        } failure {
            log.error("Error during network map registration", it)
            exitProcess(1)
        }
        node.run()
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
    node.configuration.extraAdvertisedServiceIds.let {
        if (it.isNotEmpty()) printBasicNodeInfo("Providing network services", it.joinToString())
    }
    val plugins = node.pluginRegistries
            .map { it.javaClass.name }
            .filterNot { it.startsWith("net.corda.node.") || it.startsWith("net.corda.core.") || it.startsWith("net.corda.nodeapi.") }
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
            "Computer science and finance together.\nYou should see our crazy Christmas parties!",
            "I met my bank manager yesterday and asked\nto check my balance ... he pushed me over!",
            "A banker with nobody around may find\nthemselves .... a-loan! <applause>",
            "Whenever I go near my bank I get\nwithdrawal symptoms ${Emoji.coolGuy}",
            "There was an earthquake in California,\na local bank went into de-fault.",
            "I asked for insurance if the nearby\nvolcano erupted. They said I'd be covered.",
            "I had an account with a bank in the\nNorth Pole, but they froze all my assets ${Emoji.santaClaus}",
            "Check your contracts carefully. The\nfine print is usually a clause for suspicion ${Emoji.santaClaus}",
            "Some bankers are generous ...\nto a vault! ${Emoji.bagOfCash} ${Emoji.coolGuy}",
            "What you can buy for a dollar these\ndays is absolute non-cents! ${Emoji.bagOfCash}",
            "Old bankers never die, they just\n... pass the buck",
            "My wife made me into millionaire.\nI was a multi-millionaire before we met.",
            "I won $3M on the lottery so I donated\na quarter of it to charity. Now I have $2,999,999.75.",
            "There are two rules for financial success:\n1) Don't tell everything you know.",
            "Top tip: never say \"oops\", instead\nalways say \"Ah, Interesting!\"",
            "Computers are useless. They can only\ngive you answers.  -- Picasso"
    )
    if (Emoji.hasEmojiTerminal)
        messages += "Kind of like a regular database but\nwith emojis, colours and ascii art. ${Emoji.coolGuy}"
    val (a, b) = messages.randomOrNull()!!.split('\n')
    return Pair(a, b)
}

private fun drawBanner(nodeVersionInfo: NodeVersionInfo) {
    // This line makes sure ANSI escapes work on Windows, where they aren't supported out of the box.
    AnsiConsole.systemInstall()

    Emoji.renderIfSupported {
        val (msg1, msg2) = messageOfTheDay()

        println(Ansi.ansi().fgBrightRed().a("""
   ______               __
  / ____/     _________/ /___ _
 / /     __  / ___/ __  / __ `/         """).fgBrightBlue().a(msg1).newline().fgBrightRed().a(
"/ /___  /_/ / /  / /_/ / /_/ /          ").fgBrightBlue().a(msg2).newline().fgBrightRed().a(
"""\____/     /_/   \__,_/\__,_/""").reset().newline().newline().fgBrightDefault().bold().
        a("--- ${nodeVersionInfo.vendor} ${nodeVersionInfo.version} (${nodeVersionInfo.revision.take(7)}) -----------------------------------------------").
        newline().
        newline().
        a("${Emoji.books}New! ").reset().a("Training now available worldwide, see https://corda.net/corda-training/").
        newline().
        reset())
    }
}