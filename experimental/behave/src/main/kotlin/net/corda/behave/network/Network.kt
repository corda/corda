/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.network

import net.corda.behave.database.DatabaseType
import net.corda.behave.file.*
import net.corda.behave.monitoring.PatternWatch
import net.corda.behave.node.Distribution
import net.corda.behave.node.Node
import net.corda.behave.node.configuration.NotaryType
import net.corda.behave.process.Command
import net.corda.behave.process.JarCommand
import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import net.corda.core.internal.*
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Network private constructor(
        private val nodes: Map<String, Node>,
        private val targetDirectory: Path,
        private val timeout: Duration = 2.minutes
) : Closeable, Iterable<Node> {

    private val latch = CountDownLatch(1)

    private var isRunning = false

    private var isStopped = false

    private var hasError = false

    private var isDoormanNMSRunning = false

    private lateinit var doormanNMS: JarCommand

    init {
        targetDirectory.createDirectories()
    }

    class Builder internal constructor(
            private val networkType: Distribution.Type,
            private val timeout: Duration
    ) {

        private val nodes = mutableMapOf<String, Node>()

        private val startTime = DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(UTC)
                .format(Instant.now())

        private val directory = currentDirectory / "build" / "runs" / startTime

        fun addNode(
                name: String,
                distribution: Distribution = Distribution.MASTER,
                databaseType: DatabaseType = DatabaseType.H2,
                notaryType: NotaryType = NotaryType.NONE,
                issuableCurrencies: List<String> = emptyList(),
                compatibilityZoneURL: String? = null,
                withRPCProxy: Boolean = false
        ): Builder {
            return addNode(Node.new()
                    .withName(name)
                    .withDistribution(distribution)
                    .withDatabaseType(databaseType)
                    .withNotaryType(notaryType)
                    .withIssuableCurrencies(*issuableCurrencies.toTypedArray())
                    .withRPCProxy(withRPCProxy)
                    .withNetworkMap(compatibilityZoneURL)
            )
        }

        fun addNode(nodeBuilder: Node.Builder): Builder {
            nodeBuilder
                    .withDirectory(directory)
                    .withTimeout(timeout)
            val node = nodeBuilder.build()
            nodes[node.config.name] = node
            return this
        }

        fun generate(): Network {
            val network = Network(nodes, directory, timeout)

            network.copyDatabaseDrivers()
            if (!network.configureNodes()) {
                throw CordaException("Unable to configure nodes in Corda network. Please check logs in $directory")
            }

            if (networkType == Distribution.Type.CORDA_ENTERPRISE)
                network.bootstrapDoorman()
            else
                network.bootstrapLocalNetwork()
            return network
        }
    }

    fun copyDatabaseDrivers() {
        val driverDirectory = (targetDirectory / "libs").createDirectories()
        log.info("Copying database drivers from $stagingRoot/drivers to $driverDirectory")
        Files.copy((stagingRoot / "drivers"), driverDirectory, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
    }

    fun configureNodes(): Boolean {
        var allDependenciesStarted = true
        log.info("Configuring nodes ...")
        for (node in nodes.values) {
            node.configure()
            if (!node.startDependencies()) {
                allDependenciesStarted = false
                break
            }
        }
        return if (allDependenciesStarted) {
            log.info("Nodes configured")
            true
        } else {
            false
        }
    }

    /**
     * This method performs the configuration steps defined in the Corda Enterprise Network Management readme:
     * https://github.com/corda/enterprise/blob/master/network-management/README.md
     * using Local signing and "Auto Approval" mode
     */
    private fun bootstrapDoorman() {

        // WARNING!! Need to use the correct bootstrapper
        // only if using OS nodes (need to choose the latest version)
        val r3node = nodes.values
                .find { it.config.distribution.type == Distribution.Type.CORDA_ENTERPRISE } ?: throw CordaRuntimeException("Missing R3 distribution node")
        val distribution = r3node.config.distribution

        // Copy over reference configuration files used in bootstrapping
        val source = doormanConfigDirectory
        val doormanTargetDirectory = targetDirectory / "doorman"
        source.toFile().copyRecursively(doormanTargetDirectory.toFile(), true)

        // 1. Create key stores for local signer

        //  java -jar doorman-<version>.jar --mode ROOT_KEYGEN
        log.info("Doorman target directory: $doormanTargetDirectory")
        runCommand(JarCommand(distribution.doormanJar,
                              arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "ROOT_KEYGEN", "--trust-store-password", "password"),
                              doormanTargetDirectory, timeout))

        //  java -jar doorman-<version>.jar --mode CA_KEYGEN
        runCommand(JarCommand(distribution.doormanJar,
                              arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf", "--mode", "CA_KEYGEN"),
                              doormanTargetDirectory, timeout))

        // 2. Start the doorman service for notary registration
        doormanNMS = JarCommand(distribution.doormanJar,
                                        arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf"),
                                        doormanTargetDirectory, timeout)

        val doormanCommand = runCommand(doormanNMS, noWait = true)
        log.info("Waiting for DoormanNMS to be alive")

        PatternWatch(doormanCommand.output, "Network management web services started on").await(30.seconds)
        log.info("DoormanNMS up and running")

        // Notary Nodes
        val notaryNodes = nodes.values.filter { it.config.notary.notaryType != NotaryType.NONE }
        notaryNodes.forEach { notaryNode ->
            val notaryTargetDirectory = targetDirectory / notaryNode.config.name
            log.info("Notary target directory: $notaryTargetDirectory")

            // 3. Create notary node and register with the doorman
            runCommand(JarCommand(distribution.cordaJar,
                    arrayOf("--initial-registration",
                            "--base-directory", "$notaryTargetDirectory",
                            "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                            "--network-root-truststore-password", "password"),
                    notaryTargetDirectory, timeout))

            // 4. Generate node info files for notary nodes
            runCommand(JarCommand(distribution.cordaJar,
                    arrayOf("--just-generate-node-info",
                            "--base-directory", "$notaryTargetDirectory"),
                    notaryTargetDirectory, timeout))

            // cp (or ln -s) nodeInfo* notary-node-info
            val nodeInfoFile = notaryTargetDirectory.toFile().listFiles { _, filename -> filename.matches("nodeInfo-.+".toRegex()) }.firstOrNull() ?: throw CordaRuntimeException("Missing notary nodeInfo file")

            Files.copy(nodeInfoFile.toPath(), (notaryTargetDirectory / "notary-node-info"), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        }

        // exit Doorman process
        doormanCommand.interrupt()
        doormanCommand.waitFor()

        // 5. Add notary identities to the network parameters

        // 6. Load initial network parameters file for network map service
        val networkParamsConfig = if (notaryNodes.isEmpty()) "network-parameters-without-notary.conf" else "network-parameters.conf"
        val updateNetworkParams = JarCommand(distribution.doormanJar,
                                             arrayOf("--config-file", "$doormanTargetDirectory/node.conf", "--set-network-parameters", "$doormanTargetDirectory/$networkParamsConfig"),
                                             doormanTargetDirectory, timeout)
        runCommand(updateNetworkParams)

        // 7. Start a fully configured Doorman / NMS
        doormanNMS = JarCommand(distribution.doormanJar,
                            arrayOf("--config-file", "$doormanConfigDirectory/node.conf"),
                            doormanTargetDirectory, timeout)

        val doormanNMSCommand = runCommand(doormanNMS, noWait = true)
        log.info("Waiting for DoormanNMS to be alive")

        PatternWatch(doormanNMSCommand.output, "Network management web services started on").await(30.seconds)
        log.info("DoormanNMS up and running")

        // 8. Register other participant nodes
        val partyNodes = nodes.values.filter { it.config.notary.notaryType == NotaryType.NONE }
        partyNodes.forEach { partyNode ->
            val partyTargetDirectory = targetDirectory / partyNode.config.name
            log.info("Party target directory: $partyTargetDirectory")

            // 3. Create notary node and register with the doorman
            runCommand(JarCommand(distribution.cordaJar,
                    arrayOf("--initial-registration",
                            "--network-root-truststore", "../doorman/certificates/distribute-nodes/network-root-truststore.jks",
                            "--network-root-truststore-password", "password",
                            "--base-directory", "$partyTargetDirectory"),
                            partyTargetDirectory, timeout))
        }

        isDoormanNMSRunning = true
    }

    private fun runCommand(command: Command, noWait: Boolean = false): Command {
        if (command is JarCommand) {
            log.info("Checking existence of jar file:", command.jarFile)
            if (!command.jarFile.exists()) {
                throw IllegalStateException("Jar file does not exist: ${command.jarFile}")
            }
        }
        log.info("Running command: {}", command)
        command.output.subscribe {
            if (it.contains("Exception")) {
                log.warn("Found error in output; interrupting command execution ...\n{}", it)
                command.interrupt()
            }
        }
        command.start()
        if (!noWait) {
            if (!command.waitFor()) {
                hasError = true
                error("Failed to execute command") {
                    val matches = LogSource(targetDirectory)
                            .find(".*[Ee]xception.*")
                            .groupBy { it.filename.toAbsolutePath() }
                    for (match in matches) {
                        log.info("Log(${match.key}):\n${match.value.joinToString("\n") { it.contents }}")
                    }
                }
            } else {
                log.info("Command executed successfully")
            }
        }
        return command
    }

    private fun bootstrapLocalNetwork() {
        val bootstrapper = nodes.values
                .filter { it.config.distribution.type != Distribution.Type.CORDA_ENTERPRISE }
                .sortedByDescending { it.config.distribution.version }
                .first()
                .config.distribution.networkBootstrapper

        if (!bootstrapper.exists()) {
            signalFailure("Network bootstrapping tool does not exist; exiting ...")
            return
        }

        log.info("Bootstrapping network, please wait ...")
        val command = JarCommand(
                bootstrapper,
                arrayOf("$targetDirectory"),
                targetDirectory,
                timeout
        )
        runCommand(command)
    }

    private fun bootstrapRPCProxy(node: Node) {
        val cordaDistribution = node.config.distribution.path
        val rpcProxyPortNo = node.config.nodeInterface.rpcProxy

        val startProxyScript = cordaDistribution / "startRPCproxy.sh"
        if (startProxyScript.exists()) {
            log.info("Bootstrapping RPC proxy, please wait ...")
            val rpcProxyLogFile = targetDirectory / node.config.name / "logs" / "startRPCproxy.log"
            val rpcProxyCommand = Command(listOf("$startProxyScript", "$cordaDistribution", "$rpcProxyPortNo", ">>$rpcProxyLogFile", "2>&1"),
                    cordaDistribution,
                    timeout
            )
            runCommand(rpcProxyCommand)
        }
        else {
            log.warn("Missing RPC proxy startup script. Continuing ...")
        }
    }

    private fun cleanup() {
        try {
            if (!hasError || CLEANUP_ON_ERROR) {
                log.info("Cleaning up runtime ...")
                targetDirectory.deleteRecursively()
            } else {
                log.info("Deleting temporary files, but retaining logs and config ...")
                for (node in nodes.values) {
                    val nodeDir = targetDirectory / node.config.name
                    nodeDir.list { paths -> paths
                            .filter { it.fileName.toString() !in setOf("logs", "node.conf") }
                            .forEach(Path::deleteRecursively)
                    }
                }
                listOf("libs", ".cache").forEach { (targetDirectory / it).deleteRecursively() }
            }
            log.info("Network was shut down successfully")
        } catch (e: Exception) {
            log.warn("Failed to cleanup runtime environment")
            e.printStackTrace()
        }
    }

    private fun error(message: String, ex: Throwable? = null, action: (() -> Unit)? = null) {
        hasError = true
        log.warn(message, ex)
        action?.invoke()
        stop()
        throw Exception(message, ex)
    }

    fun start() {
        if (isRunning || hasError) {
            return
        }
        isRunning = true
        for (node in nodes.values) {
            log.info("Starting node [{}]", node.config.name)
            node.start()
            if (node.rpcProxy)
                bootstrapRPCProxy(node)
        }
    }

    fun waitUntilRunning(waitDuration: Duration? = null): Boolean {

        log.info("Network.waitUntilRunning")

        if (hasError) {
            return false
        }
        var failedNodes = 0
        val nodesLatch = CountDownLatch(nodes.size)
        nodes.values.parallelStream().forEach {
            if (!it.waitUntilRunning(waitDuration ?: timeout)) {
                failedNodes += 1
            }
            nodesLatch.countDown()
        }
        nodesLatch.await()
        return if (failedNodes > 0) {
            error("$failedNodes node(s) did not start up as expected within the given time frame") {
                signal()
                keepAlive(timeout)
            }
            false
        } else {
            log.info("All nodes are running")
            true
        }
    }

    fun signalFailure(message: String?, ex: Throwable? = null) {
        error(message ?: "Signaling error to network ...", ex) {
            signal()
            keepAlive(timeout)
        }
    }

    fun signal() {
        log.info("Sending termination signal ...")
        latch.countDown()
    }

    fun keepAlive(timeout: Duration) {
        val secs = timeout.seconds
        log.info("Waiting for up to {} second(s) for termination signal ...", secs)
        val wasSignalled = latch.await(secs, TimeUnit.SECONDS)
        log.info(if (wasSignalled) {
            "Received termination signal"
        } else {
            "Timed out. No termination signal received during wait period"
        })
        stop()
    }

    fun stop() {
        if (isStopped) {
            return
        }
        log.info("Shutting down network ...")
        isStopped = true
        log.info("Shutting down nodes ...")
        for (node in nodes.values) {
            node.shutDown()
            if (node.rpcProxy) {
                log.info("Shutting down RPC proxy ...")
                try {
                    val rpcProxyPortNo = node.config.nodeInterface.rpcProxy
                    val pid = Files.lines(tmpDirectory / "rpcProxy-pid-$rpcProxyPortNo").findFirst().get()
                    // TODO: consider generic implementation to support non *nix platforms
                    Command(listOf("kill", "-9", "$pid")).run()
                    (tmpDirectory / "rpcProxy-pid-$rpcProxyPortNo").deleteIfExists()
                }
                catch (e: Exception) {
                    log.warn("Unable to locate PID file: ${e.message}")
                }
            }
        }

        if (isDoormanNMSRunning) {
            log.info("Shutting down Corda Enterprise NMS server ...")
            doormanNMS.kill()
        }

        if (System.getProperty("DISABLE_CLEANUP") == null)  // useful for re-starting and troubleshooting failure issues
            cleanup()
    }

    fun use(action: (Network) -> Unit) {
        this.start()
        action(this)
        close()
    }

    override fun close() {
        stop()
    }

    override fun iterator(): Iterator<Node> {
        return nodes.values.iterator()
    }

    operator fun get(nodeName: String): Node? {
        return nodes[nodeName]
    }

    companion object {
        val log = contextLogger()
        const val CLEANUP_ON_ERROR = false

        fun new(type: Distribution.Type = Distribution.Type.CORDA_OS, timeout: Duration = 2.minutes
        ): Builder = Builder(type, timeout)
    }
}