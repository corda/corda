package net.corda.bridge.internal

import net.corda.bridge.FirewallCmdLineOptions
import net.corda.bridge.FirewallVersionInfo
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaVersionProvider
import net.corda.cliutils.ExitCodes
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.addShutdownHook
import org.slf4j.bridge.SLF4JBridgeHandler
import picocli.CommandLine.Mixin
import sun.misc.VMSupport
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Path

class FirewallStartup: CordaCliWrapper("corda-firewall", "The Corda Firewall application for handling outbound and inbound connections to Corda.") {
    companion object {
        // lazy init the logging, because the logging levels aren't configured until we have parsed some options.
        private val log by lazy { contextLogger() }
        const val LOGS_DIRECTORY_NAME = "logs"
    }

    @Mixin
    val cmdLineOptions = FirewallCmdLineOptions()

    /**
     * @return zero if the firewalls startup was successful. This value is the exit code of the process.
     */
    override fun runProgram(): Int {
        val startTime = System.currentTimeMillis()

        // We do the single firewall check before we initialise logging so that in case of a double-firewall start it
        // doesn't mess with the running firewall's logs.
        enforceSingleBridgeIsRunning(cmdLineOptions.baseDirectory)

        val versionInfo = getVersionInfo()

        val conf = try {
            loadConfigFile()
        } catch (e: Exception) {
            log.error("Exception during firewall configuration", e)
            return ExitCodes.FAILURE
        }

        try {
            logStartupInfo(versionInfo, conf)
        } catch (e: Exception) {
            log.error("Exception during firewall registration", e)
            return ExitCodes.FAILURE
        }

        val firewall = try {
            cmdLineOptions.baseDirectory.createDirectories()
            startFirewall(conf, versionInfo, startTime)
        } catch (e: Exception) {
            if (e.message?.startsWith("Unknown named curve:") == true) {
                log.error("Exception during firewall startup - ${e.message}. " +
                        "This is a known OpenJDK issue on some Linux distributions, please use OpenJDK from zulu.org or Oracle JDK.")
            } else {
                log.error("Exception during firewall startup", e)
            }
            return ExitCodes.FAILURE
        }

        if (System.getProperties().containsKey("WAIT_KEY_FOR_EXIT")) {
            System.`in`.read() // Inside IntelliJ we can't forward CTRL-C, so debugging shutdown is a nightmare. So allow -DWAIT_KEY_FOR_EXIT flag for key based quit.
        } else {
            firewall.onExit.get()
        }

        log.info("firewall shutting down")
        firewall.stop()

        return ExitCodes.SUCCESS
    }

    private fun logStartupInfo(versionInfo: FirewallVersionInfo, conf: FirewallConfiguration) {
        log.info("Vendor: ${versionInfo.vendor}")
        log.info("Release: ${versionInfo.releaseVersion}")
        log.info("Platform Version: ${versionInfo.platformVersion}")
        log.info("Revision: ${versionInfo.revision}")
        val info = ManagementFactory.getRuntimeMXBean()
        log.info("PID: ${info.name.split("@").firstOrNull()}")  // TODO Java 9 has better support for this
        log.info("Main class: ${FirewallStartup::class.java.protectionDomain.codeSource.location.toURI().path}")
        log.info("CommandLine Args: ${info.inputArguments.joinToString(" ")}")
        log.info("Application Args: ${args.joinToString(" ")}")
        log.info("bootclasspath: ${info.bootClassPath}")
        log.info("classpath: ${info.classPath}")
        log.info("VM ${info.vmName} ${info.vmVendor} ${info.vmVersion}")
        log.info("Machine: ${lookupMachineNameAndMaybeWarn()}")
        log.info("Working Directory: ${cmdLineOptions.baseDirectory}")
        val agentProperties = VMSupport.getAgentProperties()
        if (agentProperties.containsKey("sun.jdwp.listenerAddress")) {
            log.info("Debug port: ${agentProperties.getProperty("sun.jdwp.listenerAddress")}")
        }
        log.info("Starting as firewall mode of ${conf.firewallMode}")
    }

    private fun loadConfigFile(): FirewallConfiguration = cmdLineOptions.loadConfig()

    private fun getVersionInfo(): FirewallVersionInfo {
        return FirewallVersionInfo(
                CordaVersionProvider.platformVersion,
                CordaVersionProvider.releaseVersion,
                CordaVersionProvider.revision,
                CordaVersionProvider.vendor
        )
    }
    private fun enforceSingleBridgeIsRunning(baseDirectory: Path) {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidFile = (baseDirectory / "firewall-process-id").toFile()
        pidFile.createNewFile()
        pidFile.deleteOnExit()
        val pidFileRw = RandomAccessFile(pidFile, "rw")
        val pidFileLock = pidFileRw.channel.tryLock()
        if (pidFileLock == null) {
            println("It appears there is already a firewall running with the specified data directory $baseDirectory")
            println("Shut that other firewall down and try again. It may have process ID ${pidFile.readText()}")
            System.exit(1)
        }
        // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
        // when our process shuts down, but we try in stop() anyway just to be nice.
        addShutdownHook {
            pidFileLock.release()
        }
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        pidFileRw.setLength(0)
        pidFileRw.write(ourProcessID.toByteArray())
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
            log.warn(messages.joinToString(""))
        }
        return hostName
    }

    override fun initLogging(): Boolean {
        super.initLogging()
        System.setProperty("log-path", (cmdLineOptions.baseDirectory / LOGS_DIRECTORY_NAME).toString())
        SLF4JBridgeHandler.removeHandlersForRootLogger() // The default j.u.l config adds a ConsoleHandler.
        SLF4JBridgeHandler.install()
        return true
    }

    private fun startFirewall(conf: FirewallConfiguration, versionInfo: FirewallVersionInfo, startTime: Long): FirewallInstance {
        val firewall = FirewallInstance(conf, versionInfo)
        firewall.start()
        val elapsed = (System.currentTimeMillis() - startTime) / 10 / 100.0
        log.info("Firewall started up and registered in $elapsed sec")
        return firewall
    }
}