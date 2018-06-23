package net.corda.nodeapi.internal.network

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cordform.CordformNode
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
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.concurrent.schedule
import kotlin.streams.toList

/**
 * Class to bootstrap a local network of Corda nodes on the same filesystem.
 */
// TODO Move this to tools:bootstrapper
class NetworkBootstrapper
    @VisibleForTesting
    internal constructor(private val initSerEnv: Boolean,
                         private val embeddedCordaJar: () -> InputStream,
                         private val nodeInfosGenerator: (List<Path>) -> List<Path>,
                         private val contractsJarConverter: (Path) -> ContractsJar) {

    constructor() : this(
            initSerEnv = true,
            embeddedCordaJar = Companion::extractEmbeddedCordaJar,
            nodeInfosGenerator = Companion::generateNodeInfos,
            contractsJarConverter = ::ContractsJarFile
    )

    companion object {
        // TODO This will probably need to change once we start using a bundled JVM
        private val nodeInfoGenCmd = listOf(
                "java",
                "-jar",
                "corda.jar",
                "--just-generate-node-info"
        )

        private const val LOGS_DIR_NAME = "logs"

        private fun extractEmbeddedCordaJar(): InputStream {
            return Thread.currentThread().contextClassLoader.getResourceAsStream("corda.jar")
        }

        private fun generateNodeInfos(nodeDirs: List<Path>): List<Path> {
            val numParallelProcesses = Runtime.getRuntime().availableProcessors()
            val timePerNode = 40.seconds // On the test machine, generating the node info takes 7 seconds for a single node.
            val tExpected = maxOf(timePerNode, timePerNode * nodeDirs.size.toLong() / numParallelProcesses.toLong())
            val warningTimer = Timer("WarnOnSlowMachines", false).schedule(tExpected.toMillis()) {
                println("... still waiting. If this is taking longer than usual, check the node logs.")
            }
            val executor = Executors.newFixedThreadPool(numParallelProcesses)
            return try {
                nodeDirs.map { executor.fork { generateNodeInfo(it) } }.transpose().getOrThrow()
            } finally {
                warningTimer.cancel()
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
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw IllegalStateException("Error while generating node info file. Please check the logs in $logsDir.")
            }
            check(process.exitValue() == 0) {  "Error while generating node info file. Please check the logs in $logsDir." }
            return nodeDir.list { paths ->
                paths.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.findFirst().get()
            }
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
                is NotaryCluster.BFT -> DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                        directories,
                        cluster.name,
                        threshold = 1 + 2 * directories.size / 3
                )
                is NotaryCluster.CFT -> DevIdentityGenerator.generateDistributedNotarySingularIdentity(directories, cluster.name)
            }
        }
    }

    /** Entry point for Cordform */
    fun bootstrap(directory: Path, cordappJars: List<Path>) {
        bootstrap(directory, cordappJars, copyCordapps = true, fromCordform = true)
    }

    /** Entry point for the tool */
    fun bootstrap(directory: Path, copyCordapps: Boolean) {
        // Don't accidently include the bootstrapper jar as a CorDapp!
        val bootstrapperJar = javaClass.location.toPath()
        val cordappJars = directory.list { paths ->
            paths.filter { it.toString().endsWith(".jar") && !it.isSameAs(bootstrapperJar) && it.fileName.toString() != "corda.jar" }.toList()
        }
        bootstrap(directory, cordappJars, copyCordapps, fromCordform = false)
    }

    private fun bootstrap(directory: Path, cordappJars: List<Path>, copyCordapps: Boolean, fromCordform: Boolean) {
        directory.createDirectories()
        println("Bootstrapping local test network in $directory")
        if (!fromCordform) {
            println("Found the following CorDapps: ${cordappJars.map { it.fileName }}")
        }
        createNodeDirectoriesIfNeeded(directory, fromCordform)
        val nodeDirs = gatherNodeDirectories(directory)

        require(nodeDirs.isNotEmpty()) { "No nodes found" }
        if (!fromCordform) {
            println("Nodes found in the following sub-directories: ${nodeDirs.map { it.fileName }}")
        }

        val configs = nodeDirs.associateBy({ it }, { ConfigFactory.parseFile((it / "node.conf").toFile()) })
        checkForDuplicateLegalNames(configs.values)
        if (copyCordapps && cordappJars.isNotEmpty()) {
            println("Copying CorDapp JARs into node directories")
            for (nodeDir in nodeDirs) {
                val cordappsDir = (nodeDir / "cordapps").createDirectories()
                cordappJars.forEach { it.copyToDirectory(cordappsDir) }
            }
        }
        generateServiceIdentitiesForNotaryClusters(configs)
        if (initSerEnv) {
            initialiseSerialization()
        }
        try {
            println("Waiting for all nodes to generate their node-info files...")
            val nodeInfoFiles = nodeInfosGenerator(nodeDirs)
            println("Distributing all node-info files to all nodes")
            distributeNodeInfos(nodeDirs, nodeInfoFiles)
            print("Loading existing network parameters... ")
            val existingNetParams = loadNetworkParameters(nodeDirs)
            println(existingNetParams ?: "none found")
            println("Gathering notary identities")
            val notaryInfos = gatherNotaryInfos(nodeInfoFiles, configs)
            println("Generating contract implementations whitelist")
            val newWhitelist = generateWhitelist(existingNetParams, readExcludeWhitelist(directory), cordappJars.map(contractsJarConverter))
            val newNetParams = installNetworkParameters(notaryInfos, newWhitelist, existingNetParams, nodeDirs)
            if (newNetParams != existingNetParams) {
                println("${if (existingNetParams == null) "New" else "Updated"} $newNetParams")
            } else {
                println("Network parameters unchanged")
            }
            println("Bootstrapping complete!")
        } finally {
            if (initSerEnv) {
                _contextSerializationEnv.set(null)
            }
        }
    }

    private fun createNodeDirectoriesIfNeeded(directory: Path, fromCordform: Boolean) {
        val cordaJar = directory / "corda.jar"
        var usingEmbedded = false
        if (!cordaJar.exists()) {
            embeddedCordaJar().use { it.copyTo(cordaJar) }
            usingEmbedded = true
        } else if (!fromCordform) {
            println("Using corda.jar in root directory")
        }

        val confFiles = directory.list { it.filter { it.toString().endsWith("_node.conf") }.toList() }
        val webServerConfFiles = directory.list { it.filter { it.toString().endsWith("_web-server.conf") }.toList() }

        for (confFile in confFiles) {
            val nodeName = confFile.fileName.toString().removeSuffix("_node.conf")
            println("Generating node directory for $nodeName")
            val nodeDir = (directory / nodeName).createDirectories()
            confFile.copyTo(nodeDir / "node.conf", REPLACE_EXISTING)
            webServerConfFiles.firstOrNull { directory.relativize(it).toString().removeSuffix("_web-server.conf") == nodeName }?.copyTo(nodeDir / "web-server.conf", REPLACE_EXISTING)
            cordaJar.copyToDirectory(nodeDir, REPLACE_EXISTING)
        }

        directory.list { paths ->
            paths.filter { (it / "node.conf").exists() && !(it / "corda.jar").exists() }.forEach {
                println("Copying corda.jar into node directory ${it.fileName}")
                cordaJar.copyToDirectory(it)
            }
        }

        if (fromCordform) {
            confFiles.forEach(Path::delete)
            webServerConfFiles.forEach(Path::delete)
        }

        if (fromCordform || usingEmbedded) {
            cordaJar.delete()
        }
    }

    private fun gatherNodeDirectories(directory: Path): List<Path> {
        return directory.list { paths ->
            paths.filter {
                val exists = (it / "corda.jar").exists()
                if (exists) {
                    require((it / "node.conf").exists()) { "Missing node.conf in node directory ${it.fileName}" }
                }
                exists
            }.toList()
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

    private fun checkForDuplicateLegalNames(nodeConfigs: Collection<Config>) {
        val duplicateLegalNames = nodeConfigs
                .groupBy { it.getString("myLegalName") }
                .mapNotNull { if (it.value.size > 1) it.key else null }
        check(duplicateLegalNames.isEmpty()) {
            "Nodes must have unique legal names. The following are used more than once: $duplicateLegalNames"
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
        // TODO Add config for minimumPlatformVersion, maxMessageSize and maxTransactionSize
        val netParams = if (existingNetParams != null) {
            if (existingNetParams.whitelistedContractImplementations == whitelist && existingNetParams.notaries == notaryInfos) {
                existingNetParams
            } else {
                existingNetParams.copy(
                        notaries = notaryInfos,
                        modifiedTime = Instant.now(),
                        whitelistedContractImplementations = whitelist,
                        epoch = existingNetParams.epoch + 1
                )
            }
        } else {
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
        val copier = NetworkParametersCopier(netParams, overwriteFile = true)
        nodeDirs.forEach(copier::install)
        return netParams
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
