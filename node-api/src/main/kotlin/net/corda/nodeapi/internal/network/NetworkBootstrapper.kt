package net.corda.nodeapi.internal.network

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
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
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.days
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.scanJarForContracts
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.streams.toList
import kotlin.collections.HashSet
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

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
            val baseNodeDirectory = args.firstOrNull()
                    ?: throw IllegalArgumentException("Expecting first argument which is the nodes' parent directory")
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
            println("Checking for duplicate nodes")
            checkForDuplicateLegalNames(nodeInfoFiles)
            println("Distributing all node-info files to all nodes")
            distributeNodeInfos(nodeDirs, nodeInfoFiles)
            println("Gathering notary identities")
            val notaryInfos = gatherNotaryInfos(nodeInfoFiles)
            println("Notary identities to be used in network parameters: ${notaryInfos.joinToString("; ") { it.prettyPrint() }}")
            val mergedWhiteList = generateWhitelist(directory / WHITELIST_FILE_NAME, directory / EXCLUDE_WHITELIST_FILE_NAME, cordapps)
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
        val confFiles = directory.list { it.filter { it.toString().endsWith(".conf") }.toList() }
        if (confFiles.isEmpty()) return
        println("Node config files found in the root directory - generating node directories")
        val cordaJar = extractCordaJarTo(directory)
        for (confFile in confFiles) {
            val nodeName = confFile.fileName.toString().removeSuffix(".conf")
            println("Generating directory for $nodeName")
            val nodeDir = (directory / nodeName).createDirectories()
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

    /*the function checks for duplicate myLegalName in the all the *_node.conf files
    All the myLegalName values are added to a HashSet - this helps detect duplicate values.
    If a duplicate name is found the process is aborted with an error message
    */
    private fun checkForDuplicateLegalNames(nodeInfoFiles: List<Path>) {
      val legalNames = HashSet<String>()
      for (nodeInfoFile in nodeInfoFiles) {
        val nodeConfig = ConfigFactory.parseFile((nodeInfoFile.parent / "node.conf").toFile())
        val legalName = nodeConfig.getString("myLegalName")
        if(!legalNames.add(legalName)){
          println("Duplicate Node Found - ensure every node has a unique legal name");
          throw IllegalArgumentException("Duplicate Node Found - $legalName");
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
                whitelistedContractImplementations = whitelist,
                eventHorizon = 30.days
        ), overwriteFile = true)

        nodeDirs.forEach { copier.install(it) }
    }

    private fun generateWhitelist(whitelistFile: Path, excludeWhitelistFile: Path, cordapps: List<String>?): Map<String, List<AttachmentId>> {

        val existingWhitelist = if (whitelistFile.exists()) readContractWhitelist(whitelistFile) else emptyMap()

        println(if (existingWhitelist.isEmpty()) "No existing whitelist file found." else "Found existing whitelist: ${whitelistFile}")

        val excludeContracts = if (excludeWhitelistFile.exists()) readExcludeWhitelist(excludeWhitelistFile) else emptyList()
        if (excludeContracts.isNotEmpty()) {
            println("Exclude contracts from whitelist: ${excludeContracts.joinToString()}}")
        }

        val newWhiteList: Map<ContractClassName, AttachmentId> = cordapps?.flatMap { cordappJarPath ->
            val jarHash = getJarHash(cordappJarPath)
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

        println("CorDapp whitelist " + (if (existingWhitelist.isEmpty()) "generated" else "updated") + " in ${whitelistFile}")
        return merged
    }

    private fun overwriteWhitelist(whitelistFile: Path, mergedWhiteList: Map<String, List<AttachmentId>>) {
        PrintStream(whitelistFile.toFile().outputStream()).use { out ->
            mergedWhiteList.forEach {
                out.println(it.outputString())
            }
        }
    }

    private fun getJarHash(cordappPath: String): AttachmentId = File(cordappPath).inputStream().use { jar ->
        val hs = HashingInputStream(Hashing.sha256(), jar)
        hs.readBytes()
        SecureHash.SHA256(hs.hash().asBytes())
    }

    private fun readContractWhitelist(file: Path): Map<String, List<AttachmentId>> = file.readAllLines()
            .map { line -> line.split(":") }
            .map { (contract, attachmentIds) ->
                contract to (attachmentIds.split(",").map(::parse))
            }.toMap()

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

    private fun Map.Entry<ContractClassName, List<AttachmentId>>.outputString() = "$key:${value.joinToString(",")}"

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
        override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
            return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
        }

        override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    }
}
