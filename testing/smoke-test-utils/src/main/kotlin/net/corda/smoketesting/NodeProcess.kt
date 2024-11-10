package net.corda.smoketesting

import com.google.common.collect.Lists
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.toPath
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.testing.common.internal.asContextEnv
import net.corda.testing.common.internal.checkNotOnClasspath
import net.corda.testing.common.internal.testNetworkParameters
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.writeText

class NodeProcess(
        private val config: NodeParams,
        val nodeDir: Path,
        private val node: Process,
        private val client: CordaRPCClient
) : AutoCloseable {
    companion object {
        const val CORDAPPS_DIR_NAME = "cordapps"
        private val log = contextLogger()
        private const val schemaCreationTimeOutSeconds: Long = 180
    }

    fun connect(user: User): CordaRPCConnection {
        return client.start(user.username, user.password)
    }

    override fun close() {
        if (!node.isAlive) return
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
    class Factory(
            private val baseNetworkParameters: NetworkParameters = testNetworkParameters(minimumPlatformVersion = PLATFORM_VERSION),
            private val buildDirectory: Path = Paths.get("build")
    ) : AutoCloseable {
        companion object {
            private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS").withZone(systemDefault())
            private val cordaJars = ConcurrentHashMap<String, CordaJar>()

            init {
                checkNotOnClasspath("net.corda.node.Corda") {
                    "Smoke test has the node in its classpath. Please remove the offending dependency."
                }
            }

            @Suppress("MagicNumber")
            private fun getCordaJarInfo(version: String): CordaJar {
                return cordaJars.computeIfAbsent(version) {
                    val (javaHome, versionSuffix) = if (version.isEmpty()) {
                        System.getProperty("java.home") to ""
                    } else {
                        val javaHome = if (version.split(".")[1].toInt() > 11) {
                            System.getProperty("java.home")
                        } else {
                            // 4.11 and below need JDK 8 to run
                            checkNotNull(System.getenv("JAVA_8_HOME")) { "Please set JAVA_8_HOME env variable to home directory of JDK 8" }
                        }
                        javaHome to "-$version"
                    }
                    val cordaJar = this::class.java.getResource("/corda$versionSuffix.jar")!!.toPath()
                    CordaJar(cordaJar, Path(javaHome, "bin", "java"))
                }
            }

            fun getCordaJar(version: String? = null): Path = getCordaJarInfo(version ?: "").jarPath
        }

        private val nodesDirectory: Path = (buildDirectory / "smoke-testing" / formatter.format(Instant.now())).createDirectories()
        private val nodeInfoFilesCopier = NodeInfoFilesCopier()
        private var nodes: MutableList<NodeProcess>? = ArrayList()
        private lateinit var networkParametersCopier: NetworkParametersCopier

        fun baseDirectory(config: NodeParams): Path = nodesDirectory / config.commonName

        fun createNotaries(first: NodeParams, vararg rest: NodeParams): List<NodeProcess> {
            check(!::networkParametersCopier.isInitialized) { "Notaries have already been created" }

            val notariesParams = Lists.asList(first, rest)
            val notaryInfos = notariesParams.map { notaryParams ->
                val nodeDir = baseDirectory(notaryParams).createDirectories()
                val notaryParty = DevIdentityGenerator.installKeyStoreWithNodeIdentity(nodeDir, notaryParams.legalName)
                NotaryInfo(notaryParty, true)
            }
            val networkParameters = baseNetworkParameters.copy(notaries = notaryInfos)
            networkParametersCopier = try {
                NetworkParametersCopier(networkParameters)
            } catch (_: IllegalStateException) {
                // Assuming serialization env not in context.
                AMQPClientSerializationScheme.createSerializationEnv().asContextEnv {
                    NetworkParametersCopier(networkParameters)
                }
            }

            return notariesParams.map { createNode(it, isNotary = true) }
        }

        fun createNode(params: NodeParams): NodeProcess = createNode(params, isNotary = false)

        private fun createNode(params: NodeParams, isNotary: Boolean): NodeProcess {
            check(::networkParametersCopier.isInitialized) { "Notary not created. Please call `creatNotaries` first." }

            val nodeDir = baseDirectory(params).createDirectories()
            log.info("Node directory: {}", nodeDir)
            val cordappsDir = (nodeDir / CORDAPPS_DIR_NAME).createDirectory()
            params.cordappJars.forEach { it.copyToDirectory(cordappsDir) }
            if (params.legacyContractJars.isNotEmpty()) {
                val legacyContractsDir = (nodeDir / "legacy-contracts").createDirectories()
                params.legacyContractJars.forEach { it.copyToDirectory(legacyContractsDir) }
            }
            (nodeDir / "node.conf").writeText(params.createNodeConfig(isNotary))
            networkParametersCopier.install(nodeDir)
            nodeInfoFilesCopier.addConfig(nodeDir)

            createSchema(nodeDir, params.version)
            val process = startNode(nodeDir, params.version)
            val client = CordaRPCClient(NetworkHostAndPort("localhost", params.rpcPort), params.clientRpcConfig)
            waitForNode(process, params, client)
            val nodeProcess = NodeProcess(params, nodeDir, process, client)
            nodes!! += nodeProcess
            return nodeProcess
        }

        private fun waitForNode(process: Process, config: NodeParams, client: CordaRPCClient) {
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
                        log.debug("Node '{}' not ready yet (Error: {})", config.commonName, e.message)
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

        class SchemaCreationTimedOutError(nodeDir: Path) : Exception("Creating node schema timed out for $nodeDir")
        class SchemaCreationFailedError(nodeDir: Path) : Exception("Creating node schema failed for $nodeDir")


        private fun createSchema(nodeDir: Path, version: String?) {
            val process = startNode(nodeDir, version, "run-migration-scripts", "--core-schemas", "--app-schemas")
            if (!process.waitFor(schemaCreationTimeOutSeconds, SECONDS)) {
                process.destroyForcibly()
                throw SchemaCreationTimedOutError(nodeDir)
            }
            if (process.exitValue() != 0) {
                throw SchemaCreationFailedError(nodeDir)
            }
        }

        private fun startNode(nodeDir: Path, version: String?, vararg extraArgs: String): Process {
            val cordaJar = getCordaJarInfo(version ?: "")
            val command = arrayListOf("${cordaJar.javaPath}", "-Dcapsule.log=verbose", "-jar", "${cordaJar.jarPath}", "--logging-level=debug")
            command += extraArgs
            val now = formatter.format(Instant.now())
            val builder = ProcessBuilder()
                    .command(command)
                    .directory(nodeDir.toFile())
                    .redirectError((nodeDir / "$now-stderr.log").toFile())
                    .redirectOutput((nodeDir / "$now-stdout.log").toFile())
            builder.environment().putAll(mapOf(
                    "CAPSULE_CACHE_DIR" to (buildDirectory / "capsule").toString()
            ))

            val process = builder.start()
            Runtime.getRuntime().addShutdownHook(Thread(process::destroyForcibly))
            return process
        }

        override fun close() {
            nodes?.parallelStream()?.forEach { it.close() }
            nodes = null
            nodeInfoFilesCopier.close()
        }

        private data class CordaJar(val jarPath: Path, val javaPath: Path)
    }
}
