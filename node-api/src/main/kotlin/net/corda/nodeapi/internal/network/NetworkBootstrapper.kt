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
import net.corda.core.crypto.SecureHash.Companion.parse
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.fork
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.scanJarForContracts
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic
import java.io.PrintStream
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
        private const val WHITELIST_FILE_NAME = "whitelist.txt"
        private const val EXCLUDE_WHITELIST_FILE_NAME = "exclude_whitelist.txt"

        @JvmStatic
        fun main(args: Array<String>) {
            val baseNodeDirectory = args.firstOrNull() ?: throw IllegalArgumentException("Expecting first argument which is the nodes' parent directory")
            val cordapps = if (args.size > 1) args.toList().drop(1) else null
            NetworkBootstrapper().bootstrap(Paths.get(baseNodeDirectory).toAbsolutePath().normalize(), cordapps)
        }
    }

    fun bootstrap(directory: Path, cordapps: List<String>?) {
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
            println("Notary identities to be used in network parameters: ${notaryInfos.joinToString("; ") { it.prettyPrint() }}")
            val mergedWhiteList = generateWhitelist(directory / WHITELIST_FILE_NAME, directory / EXCLUDE_WHITELIST_FILE_NAME, cordapps?.distinct())
            println("Updating whitelist")
            overwriteWhitelist(directory / WHITELIST_FILE_NAME, mergedWhiteList)
            installNetworkParameters(notaryInfos, nodeDirs, mergedWhiteList)
            println("Bootstrapping complete!")
        } finally {
            _contextSerializationEnv.set(null)
            processes.forEach { if (it.isAlive) it.destroyForcibly() }
        }
    }

    private fun generateDirectoriesIfNeeded(directory: Path) {
        val confFiles = directory.list { it.filter { it.toString().endsWith("_node.conf") }.toList() }
        val webServerConfFiles = directory.list { it.filter { it.toString().endsWith("_web-server.conf") }.toList() }
        if (confFiles.isEmpty()) return
        println("Node config files found in the root directory - generating node directories")
        val cordaJar = extractCordaJarTo(directory)
        for (confFile in confFiles) {
            val nodeName = confFile.fileName.toString().removeSuffix("_node.conf")
            println("Generating directory for $nodeName")
            val nodeDir = (directory / nodeName).createDirectories()
            confFile.moveTo(nodeDir / "node.conf", StandardCopyOption.REPLACE_EXISTING)
            webServerConfFiles.firstOrNull { directory.relativize(it).toString().removeSuffix("_web-server.conf") == nodeName }?.moveTo(nodeDir / "web-server.conf", StandardCopyOption.REPLACE_EXISTING)
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
                val nodeInfo = nodeInfoFile.readObject<SignedNodeInfo>().verified()
                NotaryInfo(nodeInfo.notaryIdentity(), validating)
            } else {
                null
            }
        }.distinct() // We need distinct as nodes part of a distributed notary share the same notary identity
    }

    private fun installNetworkParameters(notaryInfos: List<NotaryInfo>, nodeDirs: List<Path>, whitelist: Map<String, List<AttachmentId>>) {
        // TODO Add config for minimumPlatformVersion, maxMessageSize and maxTransactionSize
        val copier = NetworkParametersCopier(NetworkParameters(
                minimumPlatformVersion = 1,
                notaries = notaryInfos,
                modifiedTime = Instant.now(),
                maxMessageSize = 10485760,
                maxTransactionSize = Int.MAX_VALUE,
                epoch = 1,
                whitelistedContractImplementations = whitelist
        ), overwriteFile = true)

        nodeDirs.forEach { copier.install(it) }
    }

    private fun generateWhitelist(whitelistFile: Path, excludeWhitelistFile: Path, cordapps: List<String>?): Map<String, List<AttachmentId>> {
        val existingWhitelist = if (whitelistFile.exists()) readContractWhitelist(whitelistFile) else emptyMap()

        println(if (existingWhitelist.isEmpty()) "No existing whitelist file found." else "Found existing whitelist: $whitelistFile")

        val excludeContracts = if (excludeWhitelistFile.exists()) readExcludeWhitelist(excludeWhitelistFile) else emptyList()
        if (excludeContracts.isNotEmpty()) {
            println("Exclude contracts from whitelist: ${excludeContracts.joinToString()}}")
        }

        val newWhiteList = cordapps?.flatMap { cordappJarPath ->
            val jarHash = Paths.get(cordappJarPath).hash
            scanJarForContracts(cordappJarPath).map { contract ->
                contract to jarHash
            }
        }?.filter { (contractClassName, _) -> contractClassName !in excludeContracts }?.toMap() ?: emptyMap()

        println("Calculating whitelist for current installed CorDapps..")

        val merged = (newWhiteList.keys + existingWhitelist.keys).map { contractClassName ->
            val existing = existingWhitelist[contractClassName] ?: emptyList()
            val newHash = newWhiteList[contractClassName]
            contractClassName to (if (newHash == null || newHash in existing) existing else existing + newHash)
        }.toMap()

        println("CorDapp whitelist " + (if (existingWhitelist.isEmpty()) "generated" else "updated") + " in $whitelistFile")
        return merged
    }

    private fun overwriteWhitelist(whitelistFile: Path, mergedWhiteList: Map<String, List<AttachmentId>>) {
        PrintStream(whitelistFile.toFile().outputStream()).use { out ->
            mergedWhiteList.forEach { (contract, attachments) ->
                out.println("$contract:${attachments.joinToString(",")}")
            }
        }
    }

    private fun readContractWhitelist(file: Path): Map<String, List<AttachmentId>> {
        return file.readAllLines()
                .map { line -> line.split(":") }
                .map { (contract, attachmentIds) ->
                    contract to (attachmentIds.split(",").map(::parse))
                }.toMap()
    }

    private fun readExcludeWhitelist(file: Path): List<String> = file.readAllLines().map(String::trim)

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
