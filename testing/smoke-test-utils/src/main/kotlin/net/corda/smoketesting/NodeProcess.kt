package net.corda.smoketesting

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.asContextEnv
import net.corda.testing.common.internal.checkNotOnClasspath
import net.corda.testing.common.internal.testNetworkParameters
import java.lang.IllegalStateException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class NodeProcess(
        private val config: NodeConfig,
        private val nodeDir: Path,
        private val node: Process,
        private val client: CordaRPCClient
) : AutoCloseable {
    companion object {
        const val CORDAPPS_DIR_NAME = "cordapps"
        private val log = contextLogger()
    }

    fun connect(user: User): CordaRPCConnection {
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
        (nodeDir / "artemis").deleteRecursively()
    }

    // TODO All use of this factory have duplicate code which is either bundling the calling module or a 3rd party module
    // as a CorDapp for the nodes.
    class Factory(private val buildDirectory: Path = Paths.get("build")) {
        val cordaJar: Path by lazy {
            val cordaJarUrl = requireNotNull(this::class.java.getResource("/corda.jar")) {
                "corda.jar could not be found in classpath"
            }
            cordaJarUrl.toPath()
        }

        private companion object {
            val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
            val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS").withZone(systemDefault())
            init {
                checkNotOnClasspath("net.corda.node.Corda") {
                    "Smoke test has the node in its classpath. Please remove the offending dependency."
                }
            }
        }

        private lateinit var networkParametersCopier: NetworkParametersCopier

        private val nodesDirectory = (buildDirectory / formatter.format(Instant.now())).createDirectories()

        private var notaryParty: Party? = null

        private fun createNetworkParameters(notaryInfo: NotaryInfo, nodeDir: Path) {
            try {
                networkParametersCopier = NetworkParametersCopier(testNetworkParameters(notaries = listOf(notaryInfo)))
            } catch (_: IllegalStateException) {
                // Assuming serialization env not in context.
                AMQPClientSerializationScheme.createSerializationEnv().asContextEnv {
                    networkParametersCopier = NetworkParametersCopier(testNetworkParameters(notaries = listOf(notaryInfo)))
                }
            }
            networkParametersCopier.install(nodeDir)
        }

        fun baseDirectory(config: NodeConfig): Path = nodesDirectory / config.commonName

        fun create(config: NodeConfig): NodeProcess {
            val nodeDir = baseDirectory(config).createDirectories()
            log.info("Node directory: {}", nodeDir)
            if (config.isNotary) {
                require(notaryParty == null) { "Only one notary can be created." }
                notaryParty = DevIdentityGenerator.installKeyStoreWithNodeIdentity(nodeDir, config.legalName)
            } else {
                require(notaryParty != null) { "Notary not created. Please call `create` with a notary config first." }
            }

            (nodeDir / "node.conf").writeText(config.toText())
            createNetworkParameters(NotaryInfo(notaryParty!!, true), nodeDir)

            val process = startNode(nodeDir)
            val client = CordaRPCClient(NetworkHostAndPort("localhost", config.rpcPort))
            waitForNode(process, config, client)
            return NodeProcess(config, nodeDir, process, client)
        }

        private fun waitForNode(process: Process, config: NodeConfig, client: CordaRPCClient) {
            val executor = Executors.newSingleThreadScheduledExecutor()
            try {
                executor.scheduleWithFixedDelay({
                    try {
                        if (!process.isAlive) {
                            log.error("Node '${config.commonName}' has died.")
                            return@scheduleWithFixedDelay
                        }
                        val rpcConnection = config.users[0].let { client.start(it.username, it.password) }
                        rpcConnection.close()

                        // Cancel the "setup" task now that we've created the RPC client.
                        executor.shutdown()
                    } catch (e: Exception) {
                        log.warn("Node '{}' not ready yet (Error: {})", config.commonName, e.message)
                    }
                }, 5, 1, SECONDS)

                val setupOK = executor.awaitTermination(120, SECONDS)
                check(setupOK && process.isAlive) { "Failed to create RPC connection" }
            } catch (e: Exception) {
                process.destroyForcibly()
                throw e
            } finally {
                executor.shutdownNow()
            }
        }

        private fun startNode(nodeDir: Path): Process {
            val builder = ProcessBuilder()
                    .command(javaPath.toString(), "-Dcapsule.log=verbose", "-jar", cordaJar.toString())
                    .directory(nodeDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)

            builder.environment().putAll(mapOf(
                    "CAPSULE_CACHE_DIR" to (buildDirectory / "capsule").toString()
            ))

            return builder.start()
        }
    }
}
