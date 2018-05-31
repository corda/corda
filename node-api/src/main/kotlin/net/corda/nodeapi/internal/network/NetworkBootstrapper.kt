package net.corda.nodeapi.internal.network

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
import net.corda.core.contracts.ContractClassName
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.days
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
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
            // TODO: Use Picocli once the bootstrapper has moved into the tools package.
            val baseNodeDirectory = requireNotNull(args.firstOrNull()) { "Expecting first argument which is the nodes' parent directory" }
            val cordappJars = if (args.size > 1) args.asList().drop(1).map { Paths.get(it) } else emptyList()
            NetworkBootstrapper().bootstrap(Paths.get(baseNodeDirectory).toAbsolutePath().normalize(), cordappJars, Runtime.getRuntime().availableProcessors())
        }
    }

    sealed class NotaryCluster {
        data class BFT(val name: CordaX500Name) : NotaryCluster()
        data class CFT(val name: CordaX500Name) : NotaryCluster()
    }

    data class DirectoryAndConfig(val directory: Path, val config: Config)

    private fun notaryClusters(configs: Map<Path, Config>): Map<NotaryCluster, List<Path>> {
        val clusteredNotaries = configs.flatMap { (path, config) ->
            if (config.hasPath("notary.serviceLegalName")) {
                listOf(CordaX500Name.parse(config.getString("notary.serviceLegalName")) to DirectoryAndConfig(path, config))
            } else {
                emptyList()
            }
        }
        return clusteredNotaries.groupBy { it.first }.map { (k, vs) ->
            val cs = vs.map { it.second.config }
            if (cs.any { it.hasPath("notary.bftSMaRt") }) {
                require(cs.all { it.hasPath("notary.bftSMaRt") }) { "Mix of BFT and non-BFT notaries with service name $k" }
                NotaryCluster.BFT(k) to vs.map { it.second.directory }
            } else {
                NotaryCluster.CFT(k) to vs.map { it.second.directory }
            }
        }.toMap()
    }

    private fun generateServiceIdentitiesForNotaryClusters(configs: Map<Path, Config>) {
        notaryClusters(configs).forEach { (cluster, directories) ->
            when (cluster) {
                is NotaryCluster.BFT ->
                    DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(directories, cluster.name, threshold = 1 + 2 * directories.size / 3)
                is NotaryCluster.CFT ->
                    DevIdentityGenerator.generateDistributedNotarySingularIdentity(directories, cluster.name)
            }
        }
    }

    fun bootstrap(directory: Path, cordappJars: List<Path>, numConcurrentProcesses: Int) {
        directory.createDirectories()
        println("Bootstrapping local network in $directory")
        generateDirectoriesIfNeeded(directory, cordappJars)
        val nodeDirs = directory.list { paths -> paths.filter { (it / "corda.jar").exists() }.toList() }
        require(nodeDirs.isNotEmpty()) { "No nodes found" }
        println("Nodes found in the following sub-directories: ${nodeDirs.map { it.fileName }}")
        val configs = nodeDirs.associateBy({ it }, { ConfigFactory.parseFile((it / "node.conf").toFile()) })
        generateServiceIdentitiesForNotaryClusters(configs)
        initialiseSerialization()
        try {
            println("Waiting for all nodes to generate their node-info files...")
            val nodeInfoFiles = generateNodeInfos(nodeDirs, numConcurrentProcesses)
            println("Checking for duplicate nodes")
            checkForDuplicateLegalNames(nodeInfoFiles)
            println("Distributing all node-info files to all nodes")
            distributeNodeInfos(nodeDirs, nodeInfoFiles)
            print("Loading existing network parameters... ")
            val existingNetParams = loadNetworkParameters(nodeDirs)
            println(existingNetParams ?: "none found")
            println("Gathering notary identities")
            val notaryInfos = gatherNotaryInfos(nodeInfoFiles, configs)
            println("Generating contract implementations whitelist")
            val newWhitelist = generateWhitelist(existingNetParams, readExcludeWhitelist(directory), cordappJars.map(::ContractsJarFile))
            val netParams = installNetworkParameters(notaryInfos, newWhitelist, existingNetParams, nodeDirs)
            println("${if (existingNetParams == null) "New" else "Updated"} $netParams")
            println("Bootstrapping complete!")
        } finally {
            _contextSerializationEnv.set(null)
        }
    }

    private fun generateNodeInfos(nodeDirs: List<Path>, numConcurrentProcesses: Int): List<Path> {
        val executor = Executors.newFixedThreadPool(numConcurrentProcesses)
        return try {
            nodeDirs.map { executor.fork { generateNodeInfo(it) } }.transpose().get()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun generateNodeInfo(nodeDir: Path): Path {
        val logsDir = (nodeDir / LOGS_DIR_NAME).createDirectories()
        val process = ProcessBuilder(nodeInfoGenCmd)
                .directory(nodeDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput((logsDir / "node-info-gen.log").toFile())
                .apply { environment()["CAPSULE_CACHE_DIR"] = "../.cache" }
                .start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Error while generating node info file. Please check the logs in $logsDir.")
        }
        check(process.exitValue() == 0) {  "Error while generating node info file. Please check the logs in $logsDir." }
        return nodeDir.list { paths -> paths.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.findFirst().get() }
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

    private fun distributeNodeInfos(nodeDirs: List<Path>, nodeInfoFiles: List<Path>) {
        for (nodeDir in nodeDirs) {
            val additionalNodeInfosDir = (nodeDir / CordformNode.NODE_INFO_DIRECTORY).createDirectories()
            for (nodeInfoFile in nodeInfoFiles) {
                nodeInfoFile.copyToDirectory(additionalNodeInfosDir, REPLACE_EXISTING)
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

    private fun gatherNotaryInfos(nodeInfoFiles: List<Path>, configs: Map<Path, Config>): List<NotaryInfo> {
        return nodeInfoFiles.mapNotNull { nodeInfoFile ->
            // The config contains the notary type
            val nodeConfig = configs[nodeInfoFile.parent]!!
            if (nodeConfig.hasPath("notary")) {
                val validating = nodeConfig.getBoolean("notary.validating")
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
                    epoch = 1,
                    eventHorizon = 30.days
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
                    registerScheme(AMQPParametersSerializationScheme)
                },
                AMQP_P2P_CONTEXT)
        )
    }

    private object AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic && target == SerializationContext.UseCase.P2P
        }
    }
}
