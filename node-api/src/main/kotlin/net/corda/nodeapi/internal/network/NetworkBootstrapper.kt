package net.corda.nodeapi.internal.network

import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.fork
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.streams.toList

/**
 * Class to bootstrap a local network of Corda nodes on the same filesystem.
 */
class NetworkBootstrapper {
    companion object {
        // TODO This will probably need to change once we start using a bundled JVM
        private val nodeInfoGenCmd = listOf(
                "java",
                "-jar",
                "corda.jar",
                "--just-generate-node-info"
        )

        private const val LOGS_DIR_NAME = "logs"

        @JvmStatic
        fun main(args: Array<String>) {
            val arg = args.singleOrNull() ?: throw IllegalArgumentException("Expecting single argument which is the nodes' parent directory")
            NetworkBootstrapper().bootstrap(Paths.get(arg).toAbsolutePath().normalize())
        }
    }

    fun bootstrap(directory: Path) {
        directory.createDirectories()
        println("Bootstrapping local network in $directory")
        generateDirectoriesIfNeeded(directory)
        val nodeDirs = directory.list { paths -> paths.filter { (it / "corda.jar").exists() }.toList() }
        require(nodeDirs.isNotEmpty()) { "No nodes found" }
        println("Nodes found in the following sub-directories: ${nodeDirs.map { it.fileName }}")
        val processes = startNodeInfoGeneration(nodeDirs)
        initialiseSerialization()
        try {
            println("Waiting for all nodes to generate their node-info files...")
            val nodeInfoFiles = gatherNodeInfoFiles(processes, nodeDirs)
            println("Distributing all node info-files to all nodes")
            distributeNodeInfos(nodeDirs, nodeInfoFiles)
            println("Gathering notary identities")
            val notaryInfos = gatherNotaryInfos(nodeInfoFiles)
            println("Notary identities to be used in network-parameters file: ${notaryInfos.joinToString("; ") { it.prettyPrint() }}")
            installNetworkParameters(notaryInfos, nodeDirs)
            println("Bootstrapping complete!")
        } finally {
            _contextSerializationEnv.set(null)
            processes.forEach { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private fun generateDirectoriesIfNeeded(directory: Path) {
        val confFiles = directory.list { it.filter { it.toString().endsWith(".conf") }.toList() }
        if (confFiles.isEmpty()) return
        println("Node config files found in the root directory - generating node directories")
        val cordaJar = extractCordaJarTo(directory)
        for (confFile in confFiles) {
            val nodeName = confFile.fileName.toString().removeSuffix(".conf")
            println("Generating directory for $nodeName")
            val nodeDir = (directory / nodeName)
            if (!nodeDir.exists()) { nodeDir.createDirectory() }
            confFile.moveTo(nodeDir / "node.conf", StandardCopyOption.REPLACE_EXISTING)
            Files.copy(cordaJar, (nodeDir / "corda.jar"), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.delete(cordaJar)
    }

    private fun extractCordaJarTo(directory: Path): Path {
        val cordaJarPath = (directory / "corda.jar")
        if (!cordaJarPath.exists()) {
            println("No corda jar found in root directory. Extracting from jar")
            Thread.currentThread().contextClassLoader.getResourceAsStream("corda.jar").copyTo(cordaJarPath)
        }
        return cordaJarPath
    }

    private fun startNodeInfoGeneration(nodeDirs: List<Path>): List<Process> {
        return nodeDirs.map { nodeDir ->
            val logsDir = (nodeDir / LOGS_DIR_NAME).createDirectories()
            ProcessBuilder(nodeInfoGenCmd)
                    .directory(nodeDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput((logsDir / "node-info-gen.log").toFile())
                    .apply { environment()["CAPSULE_CACHE_DIR"] = "../.cache" }
                    .start()
        }
    }

    private fun gatherNodeInfoFiles(processes: List<Process>, nodeDirs: List<Path>): List<Path> {
        val executor = Executors.newSingleThreadExecutor()

        val future = executor.fork {
            processes.zip(nodeDirs).map { (process, nodeDir) ->
                check(process.waitFor() == 0) {
                    "Node in ${nodeDir.fileName} exited with ${process.exitValue()} when generating its node-info - see logs in ${nodeDir / LOGS_DIR_NAME}"
                }
                nodeDir.list { paths -> paths.filter { it.fileName.toString().startsWith("nodeInfo-") }.findFirst().get() }
            }
        }

        return try {
            future.getOrThrow(60.seconds)
        } catch (e: TimeoutException) {
            println("...still waiting. If this is taking longer than usual, check the node logs.")
            future.getOrThrow()
        }
    }

    private fun distributeNodeInfos(nodeDirs: List<Path>, nodeInfoFiles: List<Path>) {
        for (nodeDir in nodeDirs) {
            val additionalNodeInfosDir = (nodeDir / CordformNode.NODE_INFO_DIRECTORY).createDirectories()
            for (nodeInfoFile in nodeInfoFiles) {
                nodeInfoFile.copyToDirectory(additionalNodeInfosDir, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun gatherNotaryInfos(nodeInfoFiles: List<Path>): List<NotaryInfo> {
        return nodeInfoFiles.mapNotNull { nodeInfoFile ->
            // The config contains the notary type
            val nodeConfig = ConfigFactory.parseFile((nodeInfoFile.parent / "node.conf").toFile())
            if (nodeConfig.hasPath("notary")) {
                val validating = nodeConfig.getConfig("notary").getBoolean("validating")
                // And the node-info file contains the notary's identity
                val nodeInfo = nodeInfoFile.readAll().deserialize<SignedNodeInfo>().verified()
                NotaryInfo(nodeInfo.notaryIdentity(), validating)
            } else {
                null
            }
        }.distinct() // We need distinct as nodes part of a distributed notary share the same notary identity
    }

    private fun installNetworkParameters(notaryInfos: List<NotaryInfo>, nodeDirs: List<Path>) {
        // TODO Add config for minimumPlatformVersion, maxMessageSize and maxTransactionSize
        val copier = NetworkParametersCopier(NetworkParameters(
                minimumPlatformVersion = 1,
                notaries = notaryInfos,
                modifiedTime = Instant.now(),
                maxMessageSize = 10485760,
                maxTransactionSize = Int.MAX_VALUE,
                epoch = 1
        ), overwriteFile = true)

        nodeDirs.forEach { copier.install(it) }
    }

    private fun NotaryInfo.prettyPrint(): String = "${identity.name} (${if (validating) "" else "non-"}validating)"

    private fun NodeInfo.notaryIdentity(): Party {
        return when (legalIdentities.size) {
        // Single node notaries have just one identity like all other nodes. This identity is the notary identity
            1 -> legalIdentities[0]
        // Nodes which are part of a distributed notary have a second identity which is the composite identity of the
        // cluster and is shared by all the other members. This is the notary identity.
            2 -> legalIdentities[1]
            else -> throw IllegalArgumentException("Not sure how to get the notary identity in this scenerio: $this")
        }
    }

    // We need to to set serialization env, because generation of parameters is run from Cordform.
    // KryoServerSerializationScheme is not accessible from nodeapi.
    private fun initialiseSerialization() {
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoParametersSerializationScheme)
                    registerScheme(AMQPServerSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
        )
    }

    private object KryoParametersSerializationScheme : AbstractKryoSerializationScheme() {
        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == kryoMagic && target == SerializationContext.UseCase.P2P
        }

        override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    }
}
