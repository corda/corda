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
import net.corda.behave.file.LogSource
import net.corda.behave.file.currentDirectory
import net.corda.behave.file.stagingRoot
import net.corda.behave.file.tmpDirectory
import net.corda.behave.node.Distribution
import net.corda.behave.node.Node
import net.corda.behave.node.configuration.NotaryType
import net.corda.behave.process.Command
import net.corda.behave.process.JarCommand
import net.corda.core.CordaException
import net.corda.core.internal.*
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
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
            nodeBuilder.withDirectory(directory)
                    .withTimeout(timeout)
            val node = nodeBuilder.build()
            nodes[node.config.name] = node
            return this
        }

        fun generate(): Network {
            val network = Network(nodes, directory, timeout)

            if (!network.configureNodes()) {
                throw CordaException("Unable to configure nodes in Corda network. Please check logs in $directory")
            }

            if (networkType == Distribution.Type.CORDA_ENTERPRISE && System.getProperty("USE_NETWORK_SERVICES") != null)
            // TODO: rework how we use the Doorman/NMS (now these are a separate product / distribution)
                network.bootstrapDoorman()
            else
                network.bootstrapLocalNetwork()
            return network
        }
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
        // TODO: rework how we use the Doorman/NMS (now these are a separate product / distribution)
        signalFailure("Bootstrapping a Corda Enterprise network using the Doorman is no longer supported; exiting ...")
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
            log.info(it)
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
        // Use master version of Bootstrapper, it doesn't matter which bootstrapper we use because we provide corda.jar, the bootstrapper will use the provided jar found in the base dir.
        val bootstrapper = Distribution.R3_MASTER.networkBootstrapper
        log.info("Bootstrapper URL: $bootstrapper\n")

        if (!bootstrapper.exists()) {
            signalFailure("Network bootstrapping tool does not exist; exiting ...")
            return
        }

        log.info("Bootstrapping network, please wait ...")
        val command = JarCommand(
                bootstrapper,
                arrayOf("--dir=$targetDirectory"),
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
        } else {
            log.warn("Missing RPC proxy startup script ($startProxyScript). Continuing ...")
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
                    nodeDir.list { paths ->
                        paths.filter { it.fileName.toString() !in setOf("logs", "node.conf") }
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
                    Command(listOf("kill", "-9", pid)).run()
                    (tmpDirectory / "rpcProxy-pid-$rpcProxyPortNo").deleteIfExists()
                } catch (e: Exception) {
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