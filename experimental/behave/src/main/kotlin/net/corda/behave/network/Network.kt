package net.corda.behave.network

import net.corda.behave.database.DatabaseType
import net.corda.behave.file.LogSource
import net.corda.behave.file.currentDirectory
import net.corda.behave.file.div
import net.corda.behave.file.doormanConfigDirectory
import net.corda.behave.logging.getLogger
import net.corda.behave.minutes
import net.corda.behave.node.Distribution
import net.corda.behave.node.Node
import net.corda.behave.node.configuration.NotaryType
import net.corda.behave.process.Command
import net.corda.behave.process.JarCommand
import net.corda.behave.seconds
import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import org.apache.commons.io.FileUtils
import java.io.Closeable
import java.io.File
import java.lang.Thread.sleep
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
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

    private val log = getLogger<Network>()

    private val latch = CountDownLatch(1)

    private var isRunning = false

    private var isStopped = false

    private var hasError = false

    private var isDoormanNMSRunning = false

    private lateinit var doormanNMS: JarCommand

    init {
        FileUtils.forceMkdir(targetDirectory)
    }

    class Builder internal constructor(
            private val timeout: Duration
    ) {

        private val nodes = mutableMapOf<String, Node>()

        private val startTime = DateTimeFormatter
                .ofPattern("yyyyMMDD-HHmmss")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.now())

        private val directory = currentDirectory / "build/runs/$startTime"

        fun addNode(
                name: String,
                distribution: Distribution = Distribution.LATEST_MASTER,
                databaseType: DatabaseType = DatabaseType.H2,
                notaryType: NotaryType = NotaryType.NONE,
                issuableCurrencies: List<String> = emptyList(),
                compatibilityZoneURL: String = "http://localhost:1300",
                withRPCProxy: Boolean = false
        ): Builder {
            val czURL = if (distribution.type == Distribution.Type.R3_CORDA) compatibilityZoneURL else null
            return addNode(Node.new()
                    .withName(name)
                    .withDistribution(distribution)
                    .withDatabaseType(databaseType)
                    .withNotaryType(notaryType)
                    .withIssuableCurrencies(*issuableCurrencies.toTypedArray())
                    .withRPCProxy(withRPCProxy)
                    .withNetworkMap(czURL)
            )
        }

        fun addNode(nodeBuilder: Node.Builder): Builder {
            nodeBuilder
                    .withDirectory(directory)
                    .withTimeout(timeout)
                    .withNetworkMap("\"http://localhost:1300\"")
            val node = nodeBuilder.build()
            nodes[node.config.name] = node
            return this
        }

        fun generate(): Network {
            val network = Network(nodes, directory, timeout)

            network.copyDatabaseDrivers()
            if (!network.configureNodes()) {
                throw CordaException("Unable to configure nodes in Corda network")
//                hasError = true
//                return
            }

            // Corda distribution (OS or R3 Corda) will be determined by the type the first node
            val distribution = network.nodes.values.first().config.distribution
            network.log.info("Corda network distribution: $distribution")

            if (distribution.type == Distribution.Type.R3_CORDA)
                network.bootstrapDoorman(distribution)
            else
                network.bootstrapLocalNetwork()
            return network
        }
    }

    fun copyDatabaseDrivers() {
        val driverDirectory = targetDirectory / "libs"
        FileUtils.forceMkdir(driverDirectory)
        FileUtils.copyDirectory(
                currentDirectory / "deps/drivers",
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

    /**
     * This method performs the configuration steps defined in the R3 Corda Network Management readme:
     * https://github.com/corda/enterprise/blob/master/network-management/README.md
     * using Local signing and "Auto Approval" mode
     */
    private fun bootstrapDoorman(distribution: Distribution) {

        // Copy over reference configuration files used in bootstrapping
        val source = doormanConfigDirectory
        val doormanTargetDirectory = targetDirectory/"doorman"
        source.copyRecursively(doormanTargetDirectory, true)

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
        val doormanCommand = JarCommand(distribution.doormanJar,
                                        arrayOf("--config-file", "$doormanConfigDirectory/node-init.conf"),
                                        doormanTargetDirectory, timeout)
        runCommand(doormanCommand, noWait = true)
        // give time for process to be ready
        sleep(10.seconds.toMillis())

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
            val nodeInfoFile = notaryTargetDirectory.listFiles { _, filename -> filename.matches("nodeInfo-.+".toRegex()) }.firstOrNull() ?: throw CordaRuntimeException("Missing notary nodeInfo file")
            FileUtils.copyFile(nodeInfoFile, notaryTargetDirectory / "notary-node-info")
        }

        // exit Doorman process
        doormanCommand.interrupt()
        doormanCommand.waitFor()

        // 5. Add notary identities to the network parameters

        // 6. Load initial network parameters file for network map service
        val networkParamsConfig = if (notaryNodes.isEmpty()) "network-parameters-without-notary.conf" else "network-parameters.conf"
        val updateNetworkParams = JarCommand(distribution.doormanJar,
                                             arrayOf("--config-file", "$doormanTargetDirectory/node.conf", "--update-network-parameters", "$doormanTargetDirectory/$networkParamsConfig"),
                                             doormanTargetDirectory, timeout)
        runCommand(updateNetworkParams, noWait = true)
        // WAIT 15 SECS and then interrupt command to gracefully terminate
        sleep(15.seconds.toMillis())
        updateNetworkParams.interrupt()
        updateNetworkParams.waitFor()

        // 7. Start a fully configured Doorman / NMS
        doormanNMS = JarCommand(distribution.doormanJar,
                            arrayOf("--config-file", "$doormanConfigDirectory/node.conf"),
                            doormanTargetDirectory, timeout)
        runCommand(doormanNMS, noWait = true)
        // give time for process to be ready
        sleep(10.seconds.toMillis())

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

    private fun runCommand(command: JarCommand, noWait: Boolean = false) {
        if (!command.jarFile.exists()) {
            log.warn("Jar file does not exist: ${command.jarFile}")
            return
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
                            .groupBy { it.filename.absolutePath }
                    for (match in matches) {
                        log.info("Log(${match.key}):\n${match.value.joinToString("\n") { it.contents }}")
                    }
                }
            } else {
                log.info("Command executed successfully")
            }
        }
    }

    private fun bootstrapLocalNetwork() {
        // WARNING!! Need to use the correct bootstrapper
        // only if using OS nodes (need to choose the latest version)
        val bootstrapper = nodes.values
                .filter { it.config.distribution.type != Distribution.Type.R3_CORDA }
                .sortedByDescending { it.config.distribution.version }
                .first()
                .config.distribution.networkBootstrapper

        if (!bootstrapper.exists()) {
            log.warn("Network bootstrapping tool does not exist; continuing ...")
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

    private fun bootstrapRPCProxy(node: Node) {
        val cordaDistribution = node.config.distribution.path
        val rpcProxyPortNo = node.config.nodeInterface.rpcProxy

        val fromPath = Paths.get(currentDirectory.toString()+"/startRPCproxy.sh")
        val toPath = Paths.get(cordaDistribution.toString()+"/startRPCproxy.sh")
        Files.copy(fromPath, toPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)

        log.info("Bootstrapping RPC proxy, please wait ...")
        val rpcProxyCommand = Command(listOf("./startRPCproxy.sh", "$cordaDistribution", "$rpcProxyPortNo", ">>startRPCproxy.log","2>&1"),
                cordaDistribution,
                timeout
        )

        log.info("Running command: {}", rpcProxyCommand)
        rpcProxyCommand.output.subscribe {
            if (it.contains("Exception")) {
                log.warn("Found error in output; interrupting RPC proxy bootstrapping action ...\n{}", it)
                rpcProxyCommand.interrupt()
            }
        }
        rpcProxyCommand.start()
        if (!rpcProxyCommand.waitFor()) {
            hasError = true
            error("Failed to bootstrap RPC proxy") {
                val matches = LogSource(targetDirectory)
                        .find(".*[Ee]xception.*")
                        .groupBy { it.filename.absolutePath }
                for (match in matches) {
                    log.info("Log(${match.key}):\n${match.value.joinToString("\n") { it.contents }}")
                }
            }
        } else {
            log.info("RPC Proxy set-up completed")
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
            node.start()
            if (node.rpcProxy)
                bootstrapRPCProxy(node)
        }
    }

    fun waitUntilRunning(waitDuration: Duration? = null): Boolean {
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
                    val pid = Files.lines(Paths.get("/tmp/rpcProxy-pid-$rpcProxyPortNo")).findFirst().get()
                    Command(listOf("kill", "-9", "$pid")).run()
                    FileUtils.deleteQuietly(Paths.get("/tmp/rpcProxy-pid-$rpcProxyPortNo").toFile())
                }
                catch (e: Exception) {
                    log.warn("Unable to locate PID file: ${e.message}")
                }
            }
        }

        if (isDoormanNMSRunning) {
            log.info("Shutting down R3 Corda NMS server ...")
            doormanNMS.kill()
        }

//        cleanup()
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

        const val CLEANUP_ON_ERROR = false

        fun new(timeout: Duration = 2.minutes
        ): Builder = Builder(timeout)
    }
}