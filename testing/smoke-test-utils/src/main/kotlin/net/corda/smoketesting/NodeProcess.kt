/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.smoketesting

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.internal.*
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.asContextEnv
import net.corda.testing.common.internal.testNetworkParameters
import java.io.File
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
        (nodeDir / "artemis").deleteRecursively()
    }

    // TODO All use of this factory have duplicate code which is either bundling the calling module or a 3rd party module
    // as a CorDapp for the nodes.
    class Factory(
            private val buildDirectory: Path = Paths.get("build"),
            private val extraJvmArgs: Array<out String> = emptyArray(),
            private val redirectConsoleTo: File? = null
    ) {
        val cordaJar: Path by lazy {
            val cordaJarUrl = requireNotNull(this::class.java.getResource("/corda.jar")) {
                "corda.jar could not be found in classpath"
            }
            cordaJarUrl.toPath()
        }

        private companion object {
            val javaPath: Path = Paths.get(System.getProperty("java.home"), "bin", "java")
            val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(systemDefault())
            val defaultNetworkParameters = run {
                KryoClientSerializationScheme.createSerializationEnv().asContextEnv {
                    // There are no notaries in the network parameters for smoke test nodes. If this is required then we would
                    // need to introduce the concept of a "network" which predefines the notaries, like the driver and MockNetwork
                    NetworkParametersCopier(testNetworkParameters())
                }
            }

            init {
                try {
                    Class.forName("net.corda.node.Corda")
                    throw Error("Smoke test has the node in its classpath. Please remove the offending dependency.")
                } catch (e: ClassNotFoundException) {
                    // If the class can't be found then we're good!
                }
            }
        }

        private val nodesDirectory = (buildDirectory / formatter.format(Instant.now())).createDirectories()

        fun baseDirectory(config: NodeConfig): Path = nodesDirectory / config.commonName

        fun create(config: NodeConfig): NodeProcess {
            val nodeDir = baseDirectory(config).createDirectories()
            log.info("Node directory: {}", nodeDir)

            (nodeDir / "node.conf").writeText(config.toText())
            defaultNetworkParameters.install(nodeDir)

            val process = startNode(nodeDir)
            val client = CordaRPCClient(NetworkHostAndPort("localhost", config.rpcPort))
            waitForNode(process, config, client)
            return NodeProcess(config, nodeDir, process, client)
        }

        fun setupPlugins(config: NodeConfig, jarPaths: List<String>): Factory {
            (baseDirectory(config) / "drivers").createDirectories().also {
                jarPaths.forEach { jar -> Paths.get(jar).copyToDirectory(it) }
            }
            return this
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
            val redirectTo = redirectConsoleTo?.let {
                ProcessBuilder.Redirect.appendTo(it)
            } ?: ProcessBuilder.Redirect.INHERIT

            val builder = ProcessBuilder()
                    .command(javaPath.toString(), "-Dcapsule.log=verbose",
                            "-jar", cordaJar.toString(), *extraJvmArgs)
                    .directory(nodeDir.toFile())
                    .redirectError(redirectTo)
                    .redirectOutput(redirectTo)

            builder.environment().putAll(mapOf(
                    "CAPSULE_CACHE_DIR" to (buildDirectory / "capsule").toString()
            ))

            return builder.start()
        }
    }
}
