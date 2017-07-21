package net.corda.smoketesting

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.getOrThrow
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.testing.establishRpc
import net.corda.testing.pollProcessDeath
import net.corda.testing.shutdownAndAwaitTermination
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class NodeProcess(
        val config: NodeConfig,
        val nodeDir: Path,
        private val node: Process,
        private val client: CordaRPCClient
) : AutoCloseable {
    private companion object {
        val log = loggerFor<NodeProcess>()
        val javaPath = System.getProperty("java.home") / "bin" / "java"
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(systemDefault())
    }

    fun connect() = config.users[0].run { client.start(username, password) }

    override fun close() {
        log.info("Stopping node '${config.commonName}'")
        node.destroy()
        if (!node.waitFor(60, SECONDS)) {
            log.warn("Node '${config.commonName}' has not shutdown correctly")
            node.destroyForcibly()
        }
        log.info("Deleting Artemis directories, because they're large!")
        (nodeDir / "artemis").toFile().deleteRecursively()
    }

    class Factory(val buildDirectory: Path = Paths.get("build"),
                  val cordaJar: Path = Paths.get(this::class.java.getResource("/corda.jar").toURI())) {
        val nodesDirectory = (buildDirectory / formatter.format(Instant.now())).createDirectories()

        fun baseDirectory(config: NodeConfig) = nodesDirectory / config.commonName

        fun create(config: NodeConfig): NodeProcess {
            val nodeDir = baseDirectory(config).createDirectories()
            log.info("Node directory: {}", nodeDir)
            nodeDir.resolve("node.conf").toFile().writeText(config.toText())
            val rpcAddress = NetworkHostAndPort("localhost", config.rpcPort)
            val user = config.users[0]
            val process = startNode(nodeDir)
            try {
                val setupExecutor = Executors.newSingleThreadScheduledExecutor()
                try {
                    val processDeathFuture = setupExecutor.pollProcessDeath(process, rpcAddress)
                    val (client, connection) = setupExecutor.establishRpc(rpcAddress, null, user.username, user.password, processDeathFuture).getOrThrow()
                    processDeathFuture.cancel(false)
                    connection.close()
                    return NodeProcess(config, nodeDir, process, client)
                } finally {
                    setupExecutor.shutdownAndAwaitTermination()
                }
            } catch (t: Throwable) {
                process.destroyForcibly()
                throw t
            }
        }

        private fun startNode(nodeDir: Path) = ProcessBuilder().apply {
            command(javaPath.toString(), "-jar", cordaJar.toString())
            directory(nodeDir.toFile())
            inheritIO() // Show any Capsule errors in the console.
            environment().putAll(mapOf(
                    "CAPSULE_CACHE_DIR" to (buildDirectory / "capsule").toString()
            ))
        }.start()
    }
}
