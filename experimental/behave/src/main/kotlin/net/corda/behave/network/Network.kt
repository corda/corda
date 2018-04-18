package net.corda.behave.network

import net.corda.behave.database.DatabaseType
import net.corda.behave.file.LogSource
import net.corda.behave.file.currentDirectory
import net.corda.behave.file.div
import net.corda.behave.file.stagingRoot
import net.corda.behave.logging.getLogger
import net.corda.behave.minutes
import net.corda.behave.node.Distribution
import net.corda.behave.node.Node
import net.corda.behave.node.configuration.NotaryType
import net.corda.behave.process.JarCommand
import net.corda.core.CordaException
import org.apache.commons.io.FileUtils
import java.io.Closeable
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Network private constructor(
        private val nodes: Map<String, Node>,
        private val targetDirectory: File,
        private val timeout: Duration = 2.minutes
) : Closeable, Iterable<Node> {

    private val latch = CountDownLatch(1)

    private var isRunning = false

    private var isStopped = false

    private var hasError = false

    init {
        FileUtils.forceMkdir(targetDirectory)
    }

    class Builder internal constructor(
            private val timeout: Duration
    ) {

        private val nodes = mutableMapOf<String, Node>()

        private val startTime = DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.now())

        private val directory = currentDirectory / "build/runs/$startTime"

        fun addNode(
                name: String,
                distribution: Distribution = Distribution.MASTER,
                databaseType: DatabaseType = DatabaseType.H2,
                notaryType: NotaryType = NotaryType.NONE,
                issuableCurrencies: List<String> = emptyList()
        ): Builder {
            return addNode(Node.new()
                    .withName(name)
                    .withDistribution(distribution)
                    .withDatabaseType(databaseType)
                    .withNotaryType(notaryType)
                    .withIssuableCurrencies(*issuableCurrencies.toTypedArray())
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
            network.bootstrapLocalNetwork()

            return network
        }
    }

    fun copyDatabaseDrivers() {
        val driverDirectory = targetDirectory / "libs"
        log.info("Copying database drivers from $stagingRoot/deps/drivers to $driverDirectory")
        FileUtils.forceMkdir(driverDirectory)
        FileUtils.copyDirectory(
                stagingRoot / "deps/drivers",
                driverDirectory
        )
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

    private fun bootstrapLocalNetwork() {
        val bootstrapper = nodes.values
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
        log.info("Running command: {}", command)
        command.output.subscribe {
            if (it.contains("Exception")) {
                log.warn("Found error in output; interrupting bootstrapping action ...\n{}", it)
                command.interrupt()
            }
        }
        command.start()
        if (!command.waitFor()) {
            hasError = true
            error("Failed to bootstrap network") {
                val matches = LogSource(targetDirectory)
                        .find(".*[Ee]xception.*")
                        .groupBy { it.filename.absolutePath }
                for (match in matches) {
                    log.info("Log(${match.key}):\n${match.value.joinToString("\n") { it.contents }}")
                }
            }
        } else {
            log.info("Network set-up completed")
        }
    }

    private fun cleanup() {
        try {
            if (!hasError || CLEANUP_ON_ERROR) {
                log.info("Cleaning up runtime ...")
                FileUtils.deleteDirectory(targetDirectory)
            } else {
                log.info("Deleting temporary files, but retaining logs and config ...")
                for (node in nodes.values.map { it.config.name }) {
                    val nodeFolder = targetDirectory / node
                    FileUtils.deleteDirectory(nodeFolder / "additional-node-infos")
                    FileUtils.deleteDirectory(nodeFolder / "artemis")
                    FileUtils.deleteDirectory(nodeFolder / "certificates")
                    FileUtils.deleteDirectory(nodeFolder / "cordapps")
                    FileUtils.deleteDirectory(nodeFolder / "shell-commands")
                    FileUtils.deleteDirectory(nodeFolder / "sshkey")
                    FileUtils.deleteQuietly(nodeFolder / "corda.jar")
                    FileUtils.deleteQuietly(nodeFolder / "network-parameters")
                    FileUtils.deleteQuietly(nodeFolder / "persistence.mv.db")
                    FileUtils.deleteQuietly(nodeFolder / "process-id")

                    for (nodeInfo in nodeFolder.listFiles({
                        file ->  file.name.matches(Regex("nodeInfo-.*"))
                    })) {
                        FileUtils.deleteQuietly(nodeInfo)
                    }
                }
                FileUtils.deleteDirectory(targetDirectory / "libs")
                FileUtils.deleteDirectory(targetDirectory / ".cache")
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
        }
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
        val log = getLogger<Network>()
        const val CLEANUP_ON_ERROR = false

        fun new(timeout: Duration = 2.minutes
        ): Builder = Builder(timeout)
    }
}