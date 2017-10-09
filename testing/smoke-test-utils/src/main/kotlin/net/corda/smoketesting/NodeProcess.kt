package net.corda.smoketesting

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
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
        val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(systemDefault())
    }

    fun connect(): CordaRPCConnection {
        val user = config.users[0]
        return client.start(user.username, user.password)
    }

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
        val nodesDirectory = buildDirectory / formatter.format(Instant.now())

        init {
            nodesDirectory.createDirectories()
        }

        fun baseDirectory(config: NodeConfig): Path = nodesDirectory / config.commonName

        fun create(config: NodeConfig): NodeProcess {
            val nodeDir = baseDirectory(config).createDirectories()
            log.info("Node directory: {}", nodeDir)

            val confFile = nodeDir.resolve("node.conf").toFile()
            confFile.writeText(config.toText())

            val process = startNode(nodeDir)
            val client = CordaRPCClient(NetworkHostAndPort("localhost", config.rpcPort))
            val user = config.users[0]

            val setupExecutor = Executors.newSingleThreadScheduledExecutor()
            try {
                setupExecutor.scheduleWithFixedDelay({
                    try {
                        if (!process.isAlive) {
                            log.error("Node '${config.commonName}' has died.")
                            return@scheduleWithFixedDelay
                        }
                        val conn = client.start(user.username, user.password)
                        conn.close()

                        // Cancel the "setup" task now that we've created the RPC client.
                        setupExecutor.shutdown()
                    } catch (e: Exception) {
                        log.warn("Node '{}' not ready yet (Error: {})", config.commonName, e.message)
                    }
                }, 5, 1, SECONDS)

                val setupOK = setupExecutor.awaitTermination(120, SECONDS)
                check(setupOK && process.isAlive) { "Failed to create RPC connection" }
            } catch (e: Exception) {
                process.destroyForcibly()
                throw e
            } finally {
                setupExecutor.shutdownNow()
            }

            return NodeProcess(config, nodeDir, process, client)
        }

        private fun startNode(nodeDir: Path): Process {
            val builder = ProcessBuilder()
                    .command(javaPath.toString(), "-jar", cordaJar.toString())
                    .directory(nodeDir.toFile())

            builder.environment().putAll(mapOf(
                    "CAPSULE_CACHE_DIR" to (buildDirectory / "capsule").toString()
            ))

            return builder.start()
        }
    }
}
