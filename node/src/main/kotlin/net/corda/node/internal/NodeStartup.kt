package net.corda.node.internal

import com.jcabi.manifests.Manifests
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import io.netty.channel.unix.Errors
import joptsimple.OptionParser
import joptsimple.util.PathConverter
import net.corda.core.crypto.Crypto
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.utilities.Try
import net.corda.core.utilities.loggerFor
import net.corda.node.*
import net.corda.node.internal.cordapp.MultipleCordappsForFlowException
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.shouldStartLocalShell
import net.corda.node.services.config.shouldStartSSHDaemon
import net.corda.node.services.transactions.bftSMaRtSerialFilter
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NodeRegistrationHelper
import net.corda.node.utilities.registration.NodeRegistrationException
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.tools.shell.InteractiveShell
import org.apache.commons.lang.SystemUtils
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.bridge.SLF4JBridgeHandler
import sun.misc.VMSupport
import java.io.Console
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.*
import kotlin.system.exitProcess

/** This class is responsible for starting a Node from command line arguments. */
open class NodeStartup(val args: Array<String>) {
    companion object {
        private val logger by lazy { loggerFor<Node>() } // I guess this is lazy to allow for logging init, but why Node?
        const val LOGS_DIRECTORY_NAME = "logs"
        const val LOGS_CAN_BE_FOUND_IN_STRING = "Logs can be found in"
        private const val INITIAL_REGISTRATION_MARKER = ".initialregistration"
    }


    /**
     * @return true if the node startup was successful. This value is intended to be the exit code of the process.
     */
    open fun run(): Boolean {
        val startTime = System.currentTimeMillis()
        if (!hasMinimumJavaVersion()) {
            println("You are using a version of Java that is not supported (${SystemUtils.JAVA_VERSION}). Please upgrade to the latest version of Java 8.")
            println("Corda will now exit...")
            return false
        }

        val registrationMode = checkRegistrationMode()
        val cmdlineOptions: CmdLineOptions = if (registrationMode && !args.contains("--initial-registration")) {
            println("Node was started before with `--initial-registration`, but the registration was not completed.\nResuming registration.")
            // Pretend that the node was started with `--initial-registration` to help prevent user error.
            NodeArgsParser().parseOrExit(*args.plus("--initial-registration"))
        } else {
            NodeArgsParser().parseOrExit(*args)
        }
        // We do the single node check before we initialise logging so that in case of a double-node start it
        // doesn't mess with the running node's logs.
        enforceSingleNodeIsRunning(cmdlineOptions.baseDirectory)

        initLogging(cmdlineOptions)
        // Register all cryptography [Provider]s.
        // Required to install our [SecureRandom] before e.g., UUID asks for one.
        // This needs to go after initLogging(netty clashes with our logging).
        Crypto.registerProviders()

        val versionInfo = getVersionInfo()

        if (cmdlineOptions.isVersion) {
            println("${versionInfo.vendor} ${versionInfo.releaseVersion}")
            println("Revision ${versionInfo.revision}")
            println("Platform Version ${versionInfo.platformVersion}")
            return true
        }

        drawBanner(versionInfo)
        Node.printBasicNodeInfo(LOGS_CAN_BE_FOUND_IN_STRING, System.getProperty("log-path"))

        val configuration = (attempt { loadConfiguration(cmdlineOptions) }.doOnException(handleConfigurationLoadingError(cmdlineOptions.configFile)) as? Try.Success)?.let(Try.Success<NodeConfiguration>::value) ?: return false

        val errors = configuration.validate()
        if (errors.isNotEmpty()) {
            logger.error("Invalid node configuration. Errors were:${System.lineSeparator()}${errors.joinToString(System.lineSeparator())}")
            return false
        }

        attempt { banJavaSerialisation(configuration) }.doOnException { error -> error.logAsUnexpected("Exception while configuring serialisation") } as? Try.Success ?: return false

        attempt { preNetworkRegistration(configuration) }.doOnException(handleRegistrationError) as? Try.Success ?: return false

        cmdlineOptions.nodeRegistrationOption?.let {
            // Null checks for [compatibilityZoneURL], [rootTruststorePath] and [rootTruststorePassword] has been done in [CmdLineOptions.loadConfig]
            attempt { registerWithNetwork(configuration, versionInfo, cmdlineOptions.nodeRegistrationOption) }.doOnException(handleRegistrationError) as? Try.Success ?: return false

            // At this point the node registration was successful. We can delete the marker file.
            deleteNodeRegistrationMarker(cmdlineOptions.baseDirectory)
            return true
        }

        logStartupInfo(versionInfo, cmdlineOptions, configuration)

        return attempt { startNode(configuration, versionInfo, startTime, cmdlineOptions) }.doOnSuccess { logger.info("Node exiting successfully") }.doOnException(handleStartError).isSuccess
    }

    private fun <RESULT> attempt(action: () -> RESULT): Try<RESULT> = Try.on(action)

    private fun Exception.isExpectedWhenStartingNode() = startNodeExpectedErrors.any { error -> error.isInstance(this) }

    private val startNodeExpectedErrors = setOf(MultipleCordappsForFlowException::class, CheckpointIncompatibleException::class, AddressBindingException::class, NetworkParametersReader::class, DatabaseIncompatibleException::class)

    private fun Exception.logAsExpected(message: String? = this.message, print: (String?) -> Unit = logger::error) = print("$message [errorCode=${errorCode()}]")

    private fun Exception.logAsUnexpected(message: String? = this.message, error: Exception = this, print: (String?, Throwable) -> Unit = logger::error) = print("$message${this.message?.let { ": $it" } ?: ""} [errorCode=${errorCode()}]", error)

    private fun Exception.isOpenJdkKnownIssue() = message?.startsWith("Unknown named curve:") == true

    private fun Exception.errorCode(): String {

        val hash = staticLocationBasedHash()
        return Integer.toOctalString(hash)
    }

    private fun Throwable.staticLocationBasedHash(visited: Set<Throwable> = setOf(this)): Int {

        val cause = this.cause
        return when {
            cause != null && !visited.contains(cause) -> Objects.hash(this::class.java.name, stackTrace, cause.staticLocationBasedHash(visited +  cause))
            else -> Objects.hash(this::class.java.name, stackTrace)
        }
    }

    private val handleRegistrationError = { error: Exception ->
        when (error) {
            is NodeRegistrationException -> error.logAsExpected("Node registration service is unavailable. Perhaps try to perform the initial registration again after a while.")
            else -> error.logAsUnexpected("Exception during node registration")
        }
    }

    private val handleStartError = { error: Exception ->
        when {
            error.isExpectedWhenStartingNode() -> error.logAsExpected()
            error is CouldNotCreateDataSourceException -> error.logAsUnexpected()
            error is Errors.NativeIoException && error.message?.contains("Address already in use") == true -> error.logAsExpected("One of the ports required by the Corda node is already in use.")
            error.isOpenJdkKnownIssue() -> error.logAsExpected("Exception during node startup - ${error.message}. This is a known OpenJDK issue on some Linux distributions, please use OpenJDK from zulu.org or Oracle JDK.")
            else -> error.logAsUnexpected("Exception during node startup")
        }
    }

    private fun handleConfigurationLoadingError(configFile: Path) = { error: Exception ->
        when (error) {
            is UnknownConfigurationKeysException -> error.logAsExpected()
            is ConfigException.IO -> error.logAsExpected(configFileNotFoundMessage(configFile), ::println)
            else -> error.logAsUnexpected("Unexpected error whilst reading node configuration")
        }
    }

    private fun configFileNotFoundMessage(configFile: Path): String {
        return """
                Unable to load the node config file from '$configFile'.

                Try setting the --base-directory flag to change which directory the node
                is looking in, or use the --config-file flag to specify it explicitly.
            """.trimIndent()
    }

    private fun loadConfiguration(cmdlineOptions: CmdLineOptions): NodeConfiguration {

        val (rawConfig, configurationResult) = loadConfigFile(cmdlineOptions)
        if (cmdlineOptions.devMode) {
            println("Config:\n${rawConfig.root().render(ConfigRenderOptions.defaults())}")
        }
        val configuration = configurationResult.getOrThrow()
        return if (cmdlineOptions.bootstrapRaftCluster) {
            println("Bootstrapping raft cluster (starting up as seed node).")
            // Ignore the configured clusterAddresses to make the node bootstrap a cluster instead of joining.
            (configuration as NodeConfigurationImpl).copy(notary = configuration.notary?.copy(raft = configuration.notary?.raft?.copy(clusterAddresses = emptyList())))
        } else {
            configuration
        }
    }

    private fun checkRegistrationMode(): Boolean {
        // Parse the command line args just to get the base directory. The base directory is needed to determine
        // if the node registration marker file exists, _before_ we call NodeArgsParser.parse().
        // If it does exist, we call NodeArgsParser with `--initial-registration` added to the argument list. This way
        // we make sure that the initial registration is completed, even if the node was restarted before the first
        // attempt to register succeeded and the node administrator forgets to specify `--initial-registration` upon
        // restart.
        val optionParser = OptionParser()
        optionParser.allowsUnrecognizedOptions()
        val baseDirectoryArg = optionParser
                .accepts("base-directory", "The node working directory where all the files are kept")
                .withRequiredArg()
                .withValuesConvertedBy(PathConverter())
                .defaultsTo(Paths.get("."))
        val isRegistrationArg =
                optionParser.accepts("initial-registration", "Start initial node registration with Corda network to obtain certificate from the permissioning server.")
        val optionSet = optionParser.parse(*args)
        val baseDirectory = optionSet.valueOf(baseDirectoryArg).normalize().toAbsolutePath()
        // If the node was started with `--initial-registration`, create marker file.
        // We do this here to ensure the marker is created even if parsing the args with NodeArgsParser fails.
        val marker = File((baseDirectory / INITIAL_REGISTRATION_MARKER).toUri())
        if (!optionSet.has(isRegistrationArg) && !marker.exists()) {
            return false
        }
        try {
            marker.createNewFile()
        } catch (e: Exception) {
            logger.warn("Could not create marker file for `--initial-registration`.", e)
        }
        return true
    }

    private fun deleteNodeRegistrationMarker(baseDir: Path) {
        try {
            val marker = File((baseDir / INITIAL_REGISTRATION_MARKER).toUri())
            if (marker.exists()) {
                marker.delete()
            }
        } catch (e: Exception) {
            e.logAsUnexpected("Could not delete the marker file that was created for `--initial-registration`.", print = logger::warn)
        }
    }

    protected open fun preNetworkRegistration(conf: NodeConfiguration) = Unit

    protected open fun createNode(conf: NodeConfiguration, versionInfo: VersionInfo): Node = Node(conf, versionInfo)

    protected open fun startNode(conf: NodeConfiguration, versionInfo: VersionInfo, startTime: Long, cmdlineOptions: CmdLineOptions) {

        cmdlineOptions.baseDirectory.createDirectories()
        val node = createNode(conf, versionInfo)
        if (cmdlineOptions.clearNetworkMapCache) {
            node.clearNetworkMapCache()
            return
        }
        if (cmdlineOptions.justGenerateNodeInfo) {
            // Perform the minimum required start-up logic to be able to write a nodeInfo to disk
            node.generateAndSaveNodeInfo()
            return
        }
        if (cmdlineOptions.justGenerateRpcSslCerts) {
            val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(conf.myLegalName.x500Principal)

            val keyStorePath = conf.baseDirectory / "certificates" / "rpcsslkeystore.jks"
            val trustStorePath = conf.baseDirectory / "certificates" / "export" / "rpcssltruststore.jks"

            if (keyStorePath.exists() || trustStorePath.exists()) {
                println("Found existing RPC SSL keystores. Command was already run. Exiting..")
                exitProcess(0)
            }

            val console: Console? = System.console()

            when (console) {
                // In this case, the JVM is not connected to the console so we need to exit
                null -> {
                    println("Not connected to console. Exiting")
                    exitProcess(1)
                }
                // Otherwise we can proceed normally
                else -> {
                    while (true) {
                        val keystorePassword1 = console.readPassword("Enter the keystore password => ")
                        val keystorePassword2 = console.readPassword("Re-enter the keystore password => ")
                        if (!keystorePassword1.contentEquals(keystorePassword2)) {
                            println("The keystore passwords don't match.")
                            continue
                        }
                        saveToKeyStore(keyStorePath, keyPair, cert, String(keystorePassword1), "rpcssl")
                        println("The keystore was saved to: $keyStorePath .")
                        break
                    }

                    while (true) {
                        val trustStorePassword1 = console.readPassword("Enter the truststore password => ")
                        val trustStorePassword2 = console.readPassword("Re-enter the truststore password => ")
                        if (!trustStorePassword1.contentEquals(trustStorePassword2)) {
                            println("The truststore passwords don't match.")
                            continue
                        }

                        saveToTrustStore(trustStorePath, cert, String(trustStorePassword1), "rpcssl")
                        println("The truststore was saved to: $trustStorePath .")
                        println("You need to distribute this file along with the password in a secure way to all RPC clients.")
                        break
                    }

                    val dollar = '$'
                    println("""
                        |
                        |The SSL certificates were generated successfully.
                        |
                        |Add this snippet to the "rpcSettings" section of your node.conf:
                        |       useSsl=true
                        |       ssl {
                        |           keyStorePath=$dollar{baseDirectory}/certificates/rpcsslkeystore.jks
                        |           keyStorePassword=the_above_password
                        |       }
                        |""".trimMargin())
                }
            }
            return
        }

        if (conf.devMode) {
            Emoji.renderIfSupported {
                Node.printWarning("This node is running in developer mode! ${Emoji.developer} This is not safe for production deployment.")
            }
        } else {
            logger.info("The Corda node is running in production mode. If this is a developer environment you can set 'devMode=true' in the node.conf file.")
        }

        val nodeInfo = node.start()
        Node.printBasicNodeInfo("Loaded CorDapps", node.services.cordappProvider.cordapps.joinToString { it.name })
        node.nodeReadyFuture.thenMatch({
            val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
            val name = nodeInfo.legalIdentitiesAndCerts.first().name.organisation
            Node.printBasicNodeInfo("Node for \"$name\" started up and registered in $elapsed sec")

            // Don't start the shell if there's no console attached.
            if (conf.shouldStartLocalShell()) {
                node.startupComplete.then {
                    try {
                        InteractiveShell.runLocalShell(node::stop)
                    } catch (e: Throwable) {
                        logger.error("Shell failed to start", e)
                    }
                }
            }
            if (conf.shouldStartSSHDaemon()) {
                Node.printBasicNodeInfo("SSH server listening on port", conf.sshd!!.port.toString())
            }
        },
                { th ->
                    logger.error("Unexpected exception during registration", th)
                })
        node.run()
    }

    protected open fun logStartupInfo(versionInfo: VersionInfo, cmdlineOptions: CmdLineOptions, conf: NodeConfiguration) {
        logger.info("Vendor: ${versionInfo.vendor}")
        logger.info("Release: ${versionInfo.releaseVersion}")
        logger.info("Platform Version: ${versionInfo.platformVersion}")
        logger.info("Revision: ${versionInfo.revision}")
        val info = ManagementFactory.getRuntimeMXBean()
        logger.info("PID: ${info.name.split("@").firstOrNull()}")  // TODO Java 9 has better support for this
        logger.info("Main class: ${NodeConfiguration::class.java.location.toURI().path}")
        logger.info("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
        logger.info("Application Args: ${args.joinToString(" ")}")
        logger.info("bootclasspath: ${info.bootClassPath}")
        logger.info("classpath: ${info.classPath}")
        logger.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
        logger.info("Machine: ${lookupMachineNameAndMaybeWarn()}")
        logger.info("Working Directory: ${cmdlineOptions.baseDirectory}")
        val agentProperties = VMSupport.getAgentProperties()
        if (agentProperties.containsKey("sun.jdwp.listenerAddress")) {
            logger.info("Debug port: ${agentProperties.getProperty("sun.jdwp.listenerAddress")}")
        }
        logger.info("Starting as node on ${conf.p2pAddress}")
    }

    protected open fun registerWithNetwork(conf: NodeConfiguration, versionInfo: VersionInfo, nodeRegistrationConfig: NodeRegistrationOption) {
        val compatibilityZoneURL = conf.networkServices?.doormanURL ?: throw RuntimeException(
                "compatibilityZoneURL or networkServices must be configured!")

        println()
        println("******************************************************************")
        println("*                                                                *")
        println("*       Registering as a new participant with Corda network      *")
        println("*                                                                *")
        println("******************************************************************")
        NodeRegistrationHelper(conf, HTTPNetworkRegistrationService(compatibilityZoneURL, versionInfo), nodeRegistrationConfig).buildKeystore()

        // Minimal changes to make registration tool create node identity.
        // TODO: Move node identity generation logic from node to registration helper.
        createNode(conf, getVersionInfo()).generateAndSaveNodeInfo()

        println("Successfully registered Corda node with compatibility zone, node identity keys and certificates are stored in '${conf.certificatesDirectory}', it is advised to backup the private keys and certificates.")
        println("Corda node will now terminate.")
    }

    protected open fun loadConfigFile(cmdlineOptions: CmdLineOptions): Pair<Config, Try<NodeConfiguration>> = cmdlineOptions.loadConfig()

    protected open fun banJavaSerialisation(conf: NodeConfiguration) {
        SerialFilter.install(if (conf.notary?.bftSMaRt != null) ::bftSMaRtSerialFilter else ::defaultSerialFilter)
    }

    protected open fun getVersionInfo(): VersionInfo {
        // Manifest properties are only available if running from the corda jar
        fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

        return VersionInfo(
                manifestValue("Corda-Platform-Version")?.toInt() ?: 1,
                manifestValue("Corda-Release-Version") ?: "Unknown",
                manifestValue("Corda-Revision") ?: "Unknown",
                manifestValue("Corda-Vendor") ?: "Unknown"
        )
    }

    private fun enforceSingleNodeIsRunning(baseDirectory: Path) {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidFile = (baseDirectory / "process-id").toFile()
        try {
            pidFile.createNewFile()
            val pidFileRw = RandomAccessFile(pidFile, "rw")
            val pidFileLock = pidFileRw.channel.tryLock()

            if (pidFileLock == null) {
                println("It appears there is already a node running with the specified data directory $baseDirectory")
                println("Shut that other node down and try again. It may have process ID ${pidFile.readText()}")
                System.exit(1)
            }
            pidFile.deleteOnExit()
            // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
            // when our process shuts down, but we try in stop() anyway just to be nice.
            addShutdownHook {
                pidFileLock.release()
            }
            val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
            pidFileRw.setLength(0)
            pidFileRw.write(ourProcessID.toByteArray())
        } catch (ex: IOException) {
            val appUser = System.getProperty("user.name")
            println("Application user '$appUser' does not have necessary permissions for Node base directory '$baseDirectory'.")
            println("Corda Node process in now exiting. Please check directory permissions and try starting the Node again.")
            System.exit(1)
        }
    }

    protected open fun initLogging(cmdlineOptions: CmdLineOptions) {
        val loggingLevel = cmdlineOptions.loggingLevel.name.toLowerCase(Locale.ENGLISH)
        System.setProperty("defaultLogLevel", loggingLevel) // These properties are referenced from the XML config file.
        if (cmdlineOptions.logToConsole) {
            System.setProperty("consoleLogLevel", loggingLevel)
            Node.renderBasicInfoToConsole = false
        }
        System.setProperty("log-path", (cmdlineOptions.baseDirectory / LOGS_DIRECTORY_NAME).toString())
        SLF4JBridgeHandler.removeHandlersForRootLogger() // The default j.u.l config adds a ConsoleHandler.
        SLF4JBridgeHandler.install()
    }

    private fun lookupMachineNameAndMaybeWarn(): String {
        val start = System.currentTimeMillis()
        val hostName: String = InetAddress.getLocalHost().hostName
        val elapsed = System.currentTimeMillis() - start
        if (elapsed > 1000 && hostName.endsWith(".local")) {
            // User is probably on macOS and experiencing this problem: http://stackoverflow.com/questions/10064581/how-can-i-eliminate-slow-resolving-loading-of-localhost-virtualhost-a-2-3-secon
            //
            // Also see https://bugs.openjdk.java.net/browse/JDK-8143378
            val messages = listOf(
                    "Your computer took over a second to resolve localhost due an incorrect configuration. Corda will work but start very slowly until this is fixed. ",
                    "Please see https://docs.corda.net/troubleshooting.html#slow-localhost-resolution for information on how to fix this. ",
                    "It will only take a few seconds for you to resolve."
            )
            logger.warn(messages.joinToString(""))
            Emoji.renderIfSupported {
                print(Ansi.ansi().fgBrightRed())
                messages.forEach {
                    println("${Emoji.sleepingFace}$it")
                }
                print(Ansi.ansi().reset())
            }
        }
        return hostName
    }

    private fun hasMinimumJavaVersion(): Boolean {
        val major = SystemUtils.JAVA_VERSION_FLOAT
        val update = SystemUtils.JAVA_VERSION.substringAfter("_").toLong()
        return major > 1.8 || (major == 1.8F && update >= 171)
    }

    open fun drawBanner(versionInfo: VersionInfo) {
        // This line makes sure ANSI escapes work on Windows, where they aren't supported out of the box.
        AnsiConsole.systemInstall()

        Emoji.renderIfSupported {
            val messages = arrayListOf(
                    "The only distributed ledger that pays\nhomage to Pac Man in its logo.",
                    "You know, I was a banker\nonce ... but I lost interest. ${Emoji.bagOfCash}",
                    "It's not who you know, it's who you know\nknows what you know you know.",
                    "It runs on the JVM because QuickBasic\nis apparently not 'professional' enough.",
                    "\"It's OK computer, I go to sleep after\ntwenty minutes of inactivity too!\"",
                    "It's kind of like a block chain but\ncords sounded healthier than chains.",
                    "Computer science and finance together.\nYou should see our crazy Christmas parties!",
                    "I met my bank manager yesterday and asked\nto check my balance ... he pushed me over!",
                    "A banker left to their own devices may find\nthemselves .... a-loan! <applause>",
                    "Whenever I go near my bank\nI get withdrawal symptoms ${Emoji.coolGuy}",
                    "There was an earthquake in California,\na local bank went into de-fault.",
                    "I asked for insurance if the nearby\nvolcano erupted. They said I'd be covered.",
                    "I had an account with a bank in the\nNorth Pole, but they froze all my assets ${Emoji.santaClaus}",
                    "Check your contracts carefully. The fine print\nis usually a clause for suspicion ${Emoji.santaClaus}",
                    "Some bankers are generous ...\nto a vault! ${Emoji.bagOfCash} ${Emoji.coolGuy}",
                    "What you can buy for a dollar these\ndays is absolute non-cents! ${Emoji.bagOfCash}",
                    "Old bankers never die, they\njust... pass the buck",
                    "I won $3M on the lottery so I donated a quarter\nof it to charity. Now I have $2,999,999.75.",
                    "There are two rules for financial success:\n1) Don't tell everything you know.",
                    "Top tip: never say \"oops\", instead\nalways say \"Ah, Interesting!\"",
                    "Computers are useless. They can only\ngive you answers.  -- Picasso",
                    "Regular naps prevent old age, especially\nif you take them whilst driving.",
                    "Always borrow money from a pessimist.\nHe won't expect it back.",
                    "War does not determine who is right.\nIt determines who is left.",
                    "A bus stops at a bus station. A train stops at a\ntrain station. What happens at a workstation?",
                    "I got a universal remote control yesterday.\nI thought, this changes everything.",
                    "Did you ever walk into an office and\nthink, whiteboards are remarkable!",
                    "The good thing about lending out your time machine\nis that you basically get it back immediately.",
                    "I used to work in a shoe recycling\nshop. It was sole destroying.",
                    "What did the fish say\nwhen he hit a wall? Dam.",
                    "You should really try a seafood diet.\nIt's easy: you see food and eat it.",
                    "I recently sold my vacuum cleaner,\nall it was doing was gathering dust.",
                    "My professor accused me of plagiarism.\nHis words, not mine!",
                    "Change is inevitable, except\nfrom a vending machine.",
                    "If at first you don't succeed, destroy\nall the evidence that you tried.",
                    "If at first you don't succeed, \nthen we have something in common!",
                    "Moses had the first tablet that\ncould connect to the cloud.",
                    "How did my parents fight boredom before the internet?\nI asked my 17 siblings and they didn't know either.",
                    "Cats spend two thirds of their lives sleeping\nand the other third making viral videos.",
                    "The problem with troubleshooting\nis that trouble shoots back.",
                    "I named my dog 'Six Miles' so I can tell\npeople I walk Six Miles every day.",
                    "People used to laugh at me when I said I wanted\nto be a comedian. Well they're not laughing now!",
                    "My wife just found out I replaced our bed\nwith a trampoline; she hit the roof.",
                    "My boss asked me who is the stupid one, me or him?\nI said everyone knows he doesn't hire stupid people.",
                    "Don't trust atoms.\nThey make up everything.",
                    "Keep the dream alive:\nhit the snooze button.",
                    "Rest in peace, boiled water.\nYou will be mist.",
                    "When I discovered my toaster wasn't\nwaterproof, I was shocked.",
                    "Where do cryptographers go for\nentertainment? The security theatre.",
                    "How did the Java programmer get rich?\nThey inherited a factory.",
                    "Why did the developer quit his job?\nHe didn't get ar-rays."
            )

            if (Emoji.hasEmojiTerminal)
                messages += "Kind of like a regular database but\nwith emojis, colours and ascii art. ${Emoji.coolGuy}"


            if (ZonedDateTime.now().dayOfWeek == DayOfWeek.FRIDAY) {
                // Make it quite likely people see it.
                repeat(20) { messages += "Ah, Friday.\nMy second favourite F-word." }
            }

            val (msg1, msg2) = messages.randomOrNull()!!.split('\n')

            println(Ansi.ansi().newline().fgBrightRed().a(
                    """   ______               __""").newline().a(
                    """  / ____/     _________/ /___ _""").newline().a(
                    """ / /     __  / ___/ __  / __ `/         """).fgBrightBlue().a(msg1).newline().fgBrightRed().a(
                    """/ /___  /_/ / /  / /_/ / /_/ /          """).fgBrightBlue().a(msg2).newline().fgBrightRed().a(
                    """\____/     /_/   \__,_/\__,_/""").reset().newline().newline().fgBrightDefault().bold().a("--- ${versionInfo.vendor} ${versionInfo.releaseVersion} (${versionInfo.revision.take(7)}) -------------------------------------------------------------").newline().newline().reset())

        }
    }
}
