/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.network

import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
import net.corda.core.contracts.ContractClassName
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.fork
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.ContractsJar
import net.corda.nodeapi.internal.ContractsJarFile
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
        private const val EXCLUDE_WHITELIST_FILE_NAME = "exclude_whitelist.txt"

        @JvmStatic
        fun main(args: Array<String>) {
            val baseNodeDirectory = requireNotNull(args.firstOrNull()) { "Expecting first argument which is the nodes' parent directory" }
            val cordappJars = if (args.size > 1) args.asList().drop(1).map { Paths.get(it) } else emptyList()
            NetworkBootstrapper().bootstrap(Paths.get(baseNodeDirectory).toAbsolutePath().normalize(), cordappJars)
        }
    }

    fun bootstrap(directory: Path, cordappJars: List<Path>) {
        directory.createDirectories()
        println("Bootstrapping local network in $directory")
        generateDirectoriesIfNeeded(directory, cordappJars)
        val nodeDirs = directory.list { paths -> paths.filter { (it / "corda.jar").exists() }.toList() }
        require(nodeDirs.isNotEmpty()) { "No nodes found" }
        println("Nodes found in the following sub-directories: ${nodeDirs.map { it.fileName }}")
        val processes = startNodeInfoGeneration(nodeDirs)
        initialiseSerialization()
        try {
            println("Waiting for all nodes to generate their node-info files...")
            val nodeInfoFiles = gatherNodeInfoFiles(processes, nodeDirs)
            println("Distributing all node-info files to all nodes")
            distributeNodeInfos(nodeDirs, nodeInfoFiles)
            print("Loading existing network parameters... ")
            val existingNetParams = loadNetworkParameters(nodeDirs)
            println(existingNetParams ?: "none found")
            println("Gathering notary identities")
            val notaryInfos = gatherNotaryInfos(nodeInfoFiles)
            println("Generating contract implementations whitelist")
            val newWhitelist = generateWhitelist(existingNetParams, readExcludeWhitelist(directory), cordappJars.map(::ContractsJarFile))
            val netParams = installNetworkParameters(notaryInfos, newWhitelist, existingNetParams, nodeDirs)
            println("${if (existingNetParams == null) "New" else "Updated"} $netParams")
            println("Bootstrapping complete!")
        } finally {
            _contextSerializationEnv.set(null)
            processes.forEach { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private fun generateDirectoriesIfNeeded(directory: Path, cordappJars: List<Path>) {
        val confFiles = directory.list { it.filter { it.toString().endsWith("_node.conf") }.toList() }
        val webServerConfFiles = directory.list { it.filter { it.toString().endsWith("_web-server.conf") }.toList() }
        if (confFiles.isEmpty()) return
        println("Node config files found in the root directory - generating node directories and copying CorDapp jars into them")
        val cordaJar = extractCordaJarTo(directory)
        for (confFile in confFiles) {
            val nodeName = confFile.fileName.toString().removeSuffix("_node.conf")
            println("Generating directory for $nodeName")
            val nodeDir = (directory / nodeName).createDirectories()
            confFile.moveTo(nodeDir / "node.conf", REPLACE_EXISTING)
            webServerConfFiles.firstOrNull { directory.relativize(it).toString().removeSuffix("_web-server.conf") == nodeName }?.moveTo(nodeDir / "web-server.conf", REPLACE_EXISTING)
            cordaJar.copyToDirectory(nodeDir, REPLACE_EXISTING)
            val cordappsDir = (nodeDir / "cordapps").createDirectories()
            cordappJars.forEach { it.copyToDirectory(cordappsDir) }
        }
        cordaJar.delete()
    }

    private fun extractCordaJarTo(directory: Path): Path {
        val cordaJarPath = directory / "corda.jar"
        if (!cordaJarPath.exists()) {
            Thread.currentThread().contextClassLoader.getResourceAsStream("corda.jar").use { it.copyTo(cordaJarPath) }
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
                nodeDir.list { paths -> paths.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.findFirst().get() }
            }
        }

        return try {
            future.getOrThrow(timeout = 60.seconds)
        } catch (e: TimeoutException) {
            println("...still waiting. If this is taking longer than usual, check the node logs.")
            future.getOrThrow()
        }
    }

    private fun distributeNodeInfos(nodeDirs: List<Path>, nodeInfoFiles: List<Path>) {
        for (nodeDir in nodeDirs) {
            val additionalNodeInfosDir = (nodeDir / CordformNode.NODE_INFO_DIRECTORY).createDirectories()
            for (nodeInfoFile in nodeInfoFiles) {
                nodeInfoFile.copyToDirectory(additionalNodeInfosDir, REPLACE_EXISTING)
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
                val nodeInfo = nodeInfoFile.readObject<SignedNodeInfo>().verified()
                NotaryInfo(nodeInfo.notaryIdentity(), validating)
            } else {
                null
            }
        }.distinct() // We need distinct as nodes part of a distributed notary share the same notary identity
    }

    private fun loadNetworkParameters(nodeDirs: List<Path>): NetworkParameters? {
        val netParamsFilesGrouped = nodeDirs.mapNotNull {
            val netParamsFile = it / NETWORK_PARAMS_FILE_NAME
            if (netParamsFile.exists()) netParamsFile else null
        }.groupBy { SerializedBytes<SignedNetworkParameters>(it.readAll()) }

        when (netParamsFilesGrouped.size) {
            0 -> return null
            1 -> return netParamsFilesGrouped.keys.first().deserialize().verifiedNetworkMapCert(DEV_ROOT_CA.certificate)
        }

        val msg = StringBuilder("Differing sets of network parameters were found. Make sure all the nodes have the same " +
                "network parameters by copying the correct $NETWORK_PARAMS_FILE_NAME file across.\n\n")

        netParamsFilesGrouped.forEach { bytes, netParamsFiles ->
            netParamsFiles.map { it.parent.fileName }.joinTo(msg, ", ")
            msg.append(":\n")
            val netParamsString = try {
                bytes.deserialize().verifiedNetworkMapCert(DEV_ROOT_CA.certificate).toString()
            } catch (e: Exception) {
                "Invalid network parameters file: $e"
            }
            msg.append(netParamsString)
            msg.append("\n\n")
        }

        throw IllegalStateException(msg.toString())
    }

    private fun installNetworkParameters(notaryInfos: List<NotaryInfo>,
                                         whitelist: Map<String, List<AttachmentId>>,
                                         existingNetParams: NetworkParameters?,
                                         nodeDirs: List<Path>): NetworkParameters {
        val networkParameters = if (existingNetParams != null) {
            existingNetParams.copy(
                    notaries = notaryInfos,
                    modifiedTime = Instant.now(),
                    whitelistedContractImplementations = whitelist,
                    epoch = existingNetParams.epoch + 1
            )
        } else {
            // TODO Add config for minimumPlatformVersion, maxMessageSize and maxTransactionSize
            NetworkParameters(
                    minimumPlatformVersion = 1,
                    notaries = notaryInfos,
                    modifiedTime = Instant.now(),
                    maxMessageSize = 10485760,
                    maxTransactionSize = Int.MAX_VALUE,
                    whitelistedContractImplementations = whitelist,
                    epoch = 1
            )
        }
        val copier = NetworkParametersCopier(networkParameters, overwriteFile = true)
        nodeDirs.forEach(copier::install)
        return networkParameters
    }

    @VisibleForTesting
    internal fun generateWhitelist(networkParameters: NetworkParameters?,
                                   excludeContracts: List<ContractClassName>,
                                   cordappJars: List<ContractsJar>): Map<ContractClassName, List<AttachmentId>> {
        val existingWhitelist = networkParameters?.whitelistedContractImplementations ?: emptyMap()

        if (excludeContracts.isNotEmpty()) {
            println("Exclude contracts from whitelist: ${excludeContracts.joinToString()}")
            existingWhitelist.keys.forEach {
                require(it !in excludeContracts) { "$it is already part of the existing whitelist and cannot be excluded." }
            }
        }

        val newWhiteList = cordappJars
                .flatMap { jar -> (jar.scan() - excludeContracts).map { it to jar.hash } }
                .toMultiMap()

        return (newWhiteList.keys + existingWhitelist.keys).associateBy({ it }) {
            val existingHashes = existingWhitelist[it] ?: emptyList()
            val newHashes = newWhiteList[it] ?: emptyList()
            (existingHashes + newHashes).distinct()
        }
    }

    private fun readExcludeWhitelist(directory: Path): List<String> {
        val file = directory / EXCLUDE_WHITELIST_FILE_NAME
        return if (file.exists()) file.readAllLines().map(String::trim) else emptyList()
    }

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
