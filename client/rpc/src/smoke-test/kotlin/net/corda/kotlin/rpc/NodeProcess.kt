package net.corda.kotlin.rpc

import com.google.common.net.HostAndPort
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.utilities.loggerFor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.*

class NodeProcess(
        val config: NodeConfig,
        val nodeDir: Path,
        private val node: Process,
        private val client: CordaRPCClient
) : AutoCloseable {
    private companion object {
        val log = loggerFor<NodeProcess>()
        val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
        val corda = File(this::class.java.getResource("/corda.jar").toURI())
        val buildDir: Path = Paths.get(System.getProperty("build.dir"))
        val capsuleDir: Path = buildDir.resolve("capsule")
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
        nodeDir.resolve("artemis").toFile().deleteRecursively()
    }

    class Factory(val nodesDir: Path) {
        init {
            assertTrue(nodesDir.toFile().forceDirectory(), "Directory '$nodesDir' does not exist")
        }

        fun create(config: NodeConfig): NodeProcess {
            val nodeDir = Files.createTempDirectory(nodesDir, config.commonName)
            log.info("Node directory: {}", nodeDir)

            val confFile = nodeDir.resolve("node.conf").toFile()
            confFile.writeText(config.toText())

            val process = startNode(nodeDir)
            val client = CordaRPCClient(HostAndPort.fromParts("localhost", config.rpcPort))
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
                assertTrue(setupOK && process.isAlive, "Failed to create RPC connection")
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
                .command(javaPath.toString(), "-jar", corda.path)
                .directory(nodeDir.toFile())

            builder.environment().putAll(mapOf(
                "CAPSULE_CACHE_DIR" to capsuleDir.toString()
            ))

            return builder.start()
        }
    }
}

private fun File.forceDirectory(): Boolean = this.isDirectory || this.mkdirs()

