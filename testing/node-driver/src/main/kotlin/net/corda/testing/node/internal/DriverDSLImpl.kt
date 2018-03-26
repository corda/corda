/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.internal.createCordaRPCClientWithSsl
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformNode
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.firstOf
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.node.NodeRegistrationOption
import net.corda.node.internal.Node
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.StartedNode
import net.corda.node.services.Permissions
import net.corda.node.services.config.*
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.*
import net.corda.testing.driver.VerifierType
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.driver.internal.OutOfProcessImpl
import net.corda.testing.internal.setGlobalSerialization
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DriverDSLImpl.ClusterType.NON_VALIDATING_RAFT
import net.corda.testing.node.internal.DriverDSLImpl.ClusterType.VALIDATING_RAFT
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import rx.observables.ConnectableObservable
import rx.schedulers.Schedulers
import java.lang.management.ManagementFactory
import java.net.ConnectException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import net.corda.nodeapi.internal.config.User as InternalUser

class DriverDSLImpl(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val startNodesInProcess: Boolean,
        val waitForAllNodesToFinish: Boolean,
        extraCordappPackagesToScan: List<String>,
        val jmxPolicy: JmxPolicy,
        val notarySpecs: List<NotarySpec>,
        val compatibilityZone: CompatibilityZoneParams?,
        val networkParameters: NetworkParameters
) : InternalDriverDSL {
    private var _executorService: ScheduledExecutorService? = null
    val executorService get() = _executorService!!
    private var _shutdownManager: ShutdownManager? = null
    override val shutdownManager get() = _shutdownManager!!
    private val cordappPackages = extraCordappPackagesToScan + getCallerPackage()
    // Map from a nodes legal name to an observable emitting the number of nodes in its network map.
    private val countObservables = mutableMapOf<CordaX500Name, Observable<Int>>()
    private val nodeNames = mutableSetOf<CordaX500Name>()
    /**
     * Future which completes when the network map is available, whether a local one or one from the CZ. This future acts
     * as a gate to prevent nodes from starting too early. The value of the future is a [LocalNetworkMap] object, which
     * is null if the network map is being provided by the CZ.
     */
    private lateinit var networkMapAvailability: CordaFuture<LocalNetworkMap?>
    private lateinit var _notaries: CordaFuture<List<NotaryHandle>>
    override val notaryHandles: List<NotaryHandle> get() = _notaries.getOrThrow()

    class State {
        val processes = ArrayList<Process>()
    }

    private val state = ThreadBox(State())

    //TODO: remove this once we can bundle quasar properly.
    private val quasarJarPath: String by lazy {
        resolveJar(".*quasar.*\\.jar$")
    }

    private val jolokiaJarPath: String by lazy {
        resolveJar(".*jolokia-jvm-.*-agent\\.jar$")
    }

    private fun resolveJar(jarNamePattern: String): String {
        return try {
            val cl = ClassLoader.getSystemClassLoader()
            val urls = (cl as URLClassLoader).urLs
            val jarPattern = jarNamePattern.toRegex()
            val jarFileUrl = urls.first { jarPattern.matches(it.path) }
            Paths.get(jarFileUrl.toURI()).toString()
        } catch (e: Exception) {
            log.warn("Unable to locate JAR `$jarNamePattern` on classpath: ${e.message}", e)
            throw e
        }
    }

    override fun shutdown() {
        if (waitForAllNodesToFinish) {
            state.locked {
                processes.forEach { it.waitFor() }
            }
        }
        _shutdownManager?.shutdown()
        _executorService?.shutdownNow()
    }

    private fun establishRpc(config: NodeConfig, processDeathFuture: CordaFuture<out Process>): CordaFuture<CordaRPCOps> {
        val rpcAddress = config.corda.rpcOptions.address!!
        val client = if (config.corda.rpcOptions.useSsl) {
            createCordaRPCClientWithSsl(rpcAddress, sslConfiguration = config.corda.rpcOptions.sslConfig)
        } else {
            CordaRPCClient(rpcAddress)
        }
        val connectionFuture = poll(executorService, "RPC connection") {
            try {
                config.corda.rpcUsers[0].run { client.start(username, password) }
            } catch (e: Exception) {
                if (processDeathFuture.isDone) throw e
                log.info("Exception while connecting to RPC, retrying to connect at $rpcAddress", e)
                null
            }
        }
        return firstOf(connectionFuture, processDeathFuture) {
            if (it == processDeathFuture) {
                throw ListenProcessDeathException(rpcAddress, processDeathFuture.getOrThrow())
            }
            val connection = connectionFuture.getOrThrow()
            shutdownManager.registerShutdown(connection::close)
            connection.proxy
        }
    }

    override fun startNode(
            defaultParameters: NodeParameters,
            providedName: CordaX500Name?,
            rpcUsers: List<User>,
            verifierType: VerifierType,
            customOverrides: Map<String, Any?>,
            startInSameProcess: Boolean?,
            maximumHeapSize: String
    ): CordaFuture<NodeHandle> {
        val p2pAddress = portAllocation.nextHostAndPort()
        // TODO: Derive name from the full picked name, don't just wrap the common name
        val name = providedName ?: CordaX500Name("${oneOf(names).organisation}-${p2pAddress.port}", "London", "GB")
        synchronized(nodeNames) {
            val wasANewNode = nodeNames.add(name)
            if (!wasANewNode) {
                throw IllegalArgumentException("Node with name $name is already started or starting.")
            }
        }
        val registrationFuture = if (compatibilityZone?.rootCert != null) {
            // We don't need the network map to be available to be able to register the node
            startNodeRegistration(name, compatibilityZone.rootCert, compatibilityZone.url)
        } else {
            doneFuture(Unit)
        }

        return registrationFuture.flatMap {
            networkMapAvailability.flatMap {
                // But starting the node proper does require the network map
                startRegisteredNode(name, it, rpcUsers, verifierType, customOverrides, startInSameProcess, maximumHeapSize, p2pAddress)
            }
        }
    }

    private fun startRegisteredNode(name: CordaX500Name,
                                    localNetworkMap: LocalNetworkMap?,
                                    rpcUsers: List<User>,
                                    verifierType: VerifierType,
                                    customOverrides: Map<String, Any?>,
                                    startInSameProcess: Boolean? = null,
                                    maximumHeapSize: String = "512m",
                                    p2pAddress: NetworkHostAndPort = portAllocation.nextHostAndPort()): CordaFuture<NodeHandle> {
        val rpcAddress = portAllocation.nextHostAndPort()
        val rpcAdminAddress = portAllocation.nextHostAndPort()
        val webAddress = portAllocation.nextHostAndPort()
        val users = rpcUsers.map { it.copy(permissions = it.permissions + DRIVER_REQUIRED_PERMISSIONS) }
        val czUrlConfig = if (compatibilityZone != null) mapOf("compatibilityZoneURL" to compatibilityZone.url.toString()) else emptyMap()
        val config = NodeConfig(ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to name.toString(),
                        "p2pAddress" to p2pAddress.toString(),
                        "rpcSettings.address" to rpcAddress.toString(),
                        "rpcSettings.adminAddress" to rpcAdminAddress.toString(),
                        "useTestClock" to useTestClock,
                        "rpcUsers" to if (users.isEmpty()) defaultRpcUserList else users.map { it.toConfig().root().unwrapped() },
                        "verifierType" to verifierType.name
                ) + czUrlConfig + customOverrides
        ))
        return startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize, localNetworkMap)
    }

    private fun startNodeRegistration(providedName: CordaX500Name, rootCert: X509Certificate, compatibilityZoneURL: URL): CordaFuture<NodeConfig> {
        val baseDirectory = baseDirectory(providedName).createDirectories()
        val config = NodeConfig(ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "p2pAddress" to "localhost:1222", // required argument, not really used
                        "compatibilityZoneURL" to compatibilityZoneURL.toString(),
                        "myLegalName" to providedName.toString())
        ))

        config.corda.certificatesDirectory.createDirectories()
        // Create network root truststore.
        val rootTruststorePath = config.corda.certificatesDirectory / "network-root-truststore.jks"
        // The network truststore will be provided by the network operator via out-of-band communication.
        val rootTruststorePassword = "corda-root-password"
        X509KeyStore.fromFile(rootTruststorePath, rootTruststorePassword, createNew = true).update {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        }

        return if (startNodesInProcess) {
            executorService.fork {
                NetworkRegistrationHelper(config.corda, HTTPNetworkRegistrationService(compatibilityZoneURL), NodeRegistrationOption(rootTruststorePath, rootTruststorePassword)).buildKeystore()
                config
            }
        } else {
            startOutOfProcessMiniNode(config,
                    "--initial-registration",
                    "--network-root-truststore=${rootTruststorePath.toAbsolutePath()}",
                    "--network-root-truststore-password=$rootTruststorePassword").map { config }
        }
    }

    private enum class ClusterType(val validating: Boolean, val clusterName: CordaX500Name) {
        VALIDATING_RAFT(true, CordaX500Name("Raft", "Zurich", "CH")),
        NON_VALIDATING_RAFT(false, CordaX500Name("Raft", "Zurich", "CH")),
        NON_VALIDATING_BFT(false, CordaX500Name("BFT", "Zurich", "CH"))
    }

    internal fun startCordformNodes(cordforms: List<CordformNode>): CordaFuture<*> {
        check(notarySpecs.isEmpty()) { "Specify notaries in the CordformDefinition" }
        check(compatibilityZone == null) { "Cordform nodes cannot be run with compatibilityZoneURL" }

        val clusterNodes = HashMultimap.create<ClusterType, CordaX500Name>()
        val notaryInfos = ArrayList<NotaryInfo>()

        // Go though the node definitions and pick out the notaries so that we can generate their identities to be used
        // in the network parameters
        for (cordform in cordforms) {
            if (cordform.notary == null) continue
            val name = CordaX500Name.parse(cordform.name)
            val notaryConfig = ConfigFactory.parseMap(cordform.notary).parseAs<NotaryConfig>()
            // We need to first group the nodes that form part of a cluster. We assume for simplicity that nodes of the
            // same cluster type and validating flag are part of the same cluster.
            if (notaryConfig.raft != null) {
                val key = if (notaryConfig.validating) VALIDATING_RAFT else NON_VALIDATING_RAFT
                clusterNodes.put(key, name)
            } else if (notaryConfig.bftSMaRt != null) {
                clusterNodes.put(ClusterType.NON_VALIDATING_BFT, name)
            } else {
                // We have all we need here to generate the identity for single node notaries
                val identity = DevIdentityGenerator.installKeyStoreWithNodeIdentity(baseDirectory(name), legalName = name)
                notaryInfos += NotaryInfo(identity, notaryConfig.validating)
            }
        }

        clusterNodes.asMap().forEach { type, nodeNames ->
            val identity = if (type == ClusterType.NON_VALIDATING_RAFT || type == ClusterType.VALIDATING_RAFT) {
                DevIdentityGenerator.generateDistributedNotarySingularIdentity(
                        dirs = nodeNames.map { baseDirectory(it) },
                        notaryName = type.clusterName
                )
            } else {
                DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                        dirs = nodeNames.map { baseDirectory(it) },
                        notaryName = type.clusterName
                )
            }
            notaryInfos += NotaryInfo(identity, type.validating)
        }

        val localNetworkMap = LocalNetworkMap(notaryInfos)

        return cordforms.map {
            val startedNode = startCordformNode(it, localNetworkMap)
            if (it.webAddress != null) {
                // Start a webserver if an address for it was specified
                startedNode.flatMap { startWebserver(it) }
            } else {
                startedNode
            }
        }.transpose()
    }

    private fun startCordformNode(cordform: CordformNode, localNetworkMap: LocalNetworkMap): CordaFuture<NodeHandle> {
        val name = CordaX500Name.parse(cordform.name)
        // TODO We shouldn't have to allocate an RPC or web address if they're not specified. We're having to do this because of startNodeInternal
        val rpcAddress = if (cordform.rpcAddress == null) {
            val overrides = mutableMapOf<String, Any>("rpcSettings.address" to portAllocation.nextHostAndPort().toString())
            cordform.config.apply {
                if (!hasPath("rpcSettings.useSsl") || !getBoolean("rpcSettings.useSsl")) {
                    overrides += "rpcSettings.adminAddress" to portAllocation.nextHostAndPort().toString()
                }
            }
            overrides
        } else {
            val overrides = mutableMapOf<String, Any>()
            cordform.config.apply {
                if ((!hasPath("rpcSettings.useSsl") || !getBoolean("rpcSettings.useSsl")) && !hasPath("rpcSettings.adminAddress")) {
                    overrides += "rpcSettings.adminAddress" to portAllocation.nextHostAndPort().toString()
                }
            }
            overrides
        }
        val webAddress = cordform.webAddress?.let { NetworkHostAndPort.parse(it) } ?: portAllocation.nextHostAndPort()
        val notary = if (cordform.notary != null) mapOf("notary" to cordform.notary) else emptyMap()
        val rpcUsers = cordform.rpcUsers

        val rawConfig = cordform.config + rpcAddress + notary + mapOf(
                "rpcUsers" to if (rpcUsers.isEmpty()) defaultRpcUserList else rpcUsers
        )
        val typesafe = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = rawConfig.toNodeOnly()
        )
        val cordaConfig = typesafe.parseAsNodeConfiguration()
        val config = NodeConfig(rawConfig, cordaConfig)
        return startNodeInternal(config, webAddress, null, "512m", localNetworkMap)
    }

    private fun queryWebserver(handle: NodeHandle, process: Process): WebserverHandle {
        val protocol = if ((handle as NodeHandleInternal).useHTTPS) "https://" else "http://"
        val url = URL("$protocol${handle.webAddress}/api/status")
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        while (process.isAlive) try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful && (response.body().string() == "started")) {
                return WebserverHandle(handle.webAddress, process)
            }
        } catch (e: ConnectException) {
            log.debug("Retrying webserver info at ${handle.webAddress}")
        }

        throw IllegalStateException("Webserver at ${handle.webAddress} has died")
    }

    override fun startWebserver(handle: NodeHandle, maximumHeapSize: String): CordaFuture<WebserverHandle> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val process = startWebserver(handle as NodeHandleInternal, debugPort, maximumHeapSize)
        shutdownManager.registerProcessShutdown(process)
        val webReadyFuture = addressMustBeBoundFuture(executorService, handle.webAddress, process)
        return webReadyFuture.map { queryWebserver(handle, process) }
    }

    override fun start() {
        if (startNodesInProcess) {
            Schedulers.reset()
        }
        require(networkParameters.notaries.isEmpty()) { "Define notaries using notarySpecs" }
        _executorService = Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("driver-pool-thread-%d").build())
        _shutdownManager = ShutdownManager(executorService)
        val notaryInfosFuture = if (compatibilityZone == null) {
            // If no CZ is specified then the driver does the generation of the network parameters and the copying of the
            // node info files.
            startNotaryIdentityGeneration().map { notaryInfos -> Pair(notaryInfos, LocalNetworkMap(notaryInfos)) }
        } else {
            // Otherwise it's the CZ's job to distribute thse via the HTTP network map, as that is what the nodes will be expecting.
            val notaryInfosFuture = if (compatibilityZone.rootCert == null) {
                // No root cert specified so we use the dev root cert to generate the notary identities.
                startNotaryIdentityGeneration()
            } else {
                // With a root cert specified we delegate generation of the notary identities to the CZ.
                startAllNotaryRegistrations(compatibilityZone.rootCert, compatibilityZone.url)
            }
            notaryInfosFuture.map { notaryInfos ->
                compatibilityZone.publishNotaries(notaryInfos)
                Pair(notaryInfos, null)
            }
        }

        networkMapAvailability = notaryInfosFuture.map { it.second }

        _notaries = notaryInfosFuture.map { (notaryInfos, localNetworkMap) ->
            val listOfFutureNodeHandles = startNotaries(localNetworkMap)
            notaryInfos.zip(listOfFutureNodeHandles) { (identity, validating), nodeHandlesFuture ->
                NotaryHandle(identity, validating, nodeHandlesFuture)
            }
        }
    }

    private fun startNotaryIdentityGeneration(): CordaFuture<List<NotaryInfo>> {
        return executorService.fork {
            notarySpecs.map { spec ->
                val identity = when (spec.cluster) {
                    null -> {
                        DevIdentityGenerator.installKeyStoreWithNodeIdentity(baseDirectory(spec.name), spec.name)
                    }
                    is ClusterSpec.Raft -> {
                        DevIdentityGenerator.generateDistributedNotarySingularIdentity(
                                dirs = generateNodeNames(spec).map { baseDirectory(it) },
                                notaryName = spec.name
                        )
                    }
                    is DummyClusterSpec -> {
                        if (spec.cluster.compositeServiceIdentity) {
                            DevIdentityGenerator.generateDistributedNotarySingularIdentity(
                                    dirs = generateNodeNames(spec).map { baseDirectory(it) },
                                    notaryName = spec.name
                            )
                        } else {
                            DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                                    dirs = generateNodeNames(spec).map { baseDirectory(it) },
                                    notaryName = spec.name
                            )
                        }
                    }
                    else -> throw UnsupportedOperationException("Cluster spec ${spec.cluster} not supported by Driver")
                }
                NotaryInfo(identity, spec.validating)
            }
        }
    }

    private fun startAllNotaryRegistrations(rootCert: X509Certificate, compatibilityZoneURL: URL): CordaFuture<List<NotaryInfo>> {
        // Start the registration process for all the notaries together then wait for their responses.
        return notarySpecs.map { spec ->
            require(spec.cluster == null) { "Registering distributed notaries not supported" }
            startNotaryRegistration(spec, rootCert, compatibilityZoneURL)
        }.transpose()
    }

    private fun startNotaryRegistration(spec: NotarySpec, rootCert: X509Certificate, compatibilityZoneURL: URL): CordaFuture<NotaryInfo> {
        return startNodeRegistration(spec.name, rootCert, compatibilityZoneURL).flatMap { config ->
            // Node registration only gives us the node CA cert, not the identity cert. That is only created on first
            // startup or when the node is told to just generate its node info file. We do that here.
            if (startNodesInProcess) {
                executorService.fork {
                    val nodeInfo = Node(config.corda, MOCK_VERSION_INFO, initialiseSerialization = false).generateAndSaveNodeInfo()
                    NotaryInfo(nodeInfo.legalIdentities[0], spec.validating)
                }
            } else {
                // TODO The config we use here is uses a hardocded p2p port which changes when the node is run proper
                // This causes two node info files to be generated.
                startOutOfProcessMiniNode(config, "--just-generate-node-info").map {
                    // Once done we have to read the signed node info file that's been generated
                    val nodeInfoFile = config.corda.baseDirectory.list { paths ->
                        paths.filter { it.fileName.toString().startsWith(NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX) }.findFirst().get()
                    }
                    val nodeInfo = nodeInfoFile.readObject<SignedNodeInfo>().verified()
                    NotaryInfo(nodeInfo.legalIdentities[0], spec.validating)
                }
            }
        }
    }

    private fun generateNodeNames(spec: NotarySpec): List<CordaX500Name> {
        return (0 until spec.cluster!!.clusterSize).map { spec.name.copy(organisation = "${spec.name.organisation}-$it") }
    }

    private fun startNotaries(localNetworkMap: LocalNetworkMap?): List<CordaFuture<List<NodeHandle>>> {
        return notarySpecs.map {
            when (it.cluster) {
                null -> startSingleNotary(it, localNetworkMap)
                is ClusterSpec.Raft,
                    // DummyCluster is used for testing the notary communication path, and it does not matter
                    // which underlying consensus algorithm is used, so we just stick to Raft
                is DummyClusterSpec -> startRaftNotaryCluster(it, localNetworkMap)
                else -> throw IllegalArgumentException("BFT-SMaRt not supported")
            }
        }
    }

    // TODO This mapping is done is several places including the gradle plugin. In general we need a better way of
    // generating the configs for the nodes, probably making use of Any.toConfig()
    private fun NotaryConfig.toConfigMap(): Map<String, Any> = mapOf("notary" to toConfig().root().unwrapped())

    private fun startSingleNotary(spec: NotarySpec, localNetworkMap: LocalNetworkMap?): CordaFuture<List<NodeHandle>> {
        return startRegisteredNode(
                spec.name,
                localNetworkMap,
                spec.rpcUsers,
                spec.verifierType,
                customOverrides = NotaryConfig(spec.validating).toConfigMap()
        ).map { listOf(it) }
    }

    private fun startRaftNotaryCluster(spec: NotarySpec, localNetworkMap: LocalNetworkMap?): CordaFuture<List<NodeHandle>> {
        fun notaryConfig(nodeAddress: NetworkHostAndPort, clusterAddress: NetworkHostAndPort? = null): Map<String, Any> {
            val clusterAddresses = if (clusterAddress != null) listOf(clusterAddress) else emptyList()
            val config = NotaryConfig(
                    validating = spec.validating,
                    raft = RaftConfig(nodeAddress = nodeAddress, clusterAddresses = clusterAddresses))
            return config.toConfigMap()
        }

        val nodeNames = generateNodeNames(spec)
        val clusterAddress = portAllocation.nextHostAndPort()

        // Start the first node that will bootstrap the cluster
        val firstNodeFuture = startRegisteredNode(
                nodeNames[0],
                localNetworkMap,
                spec.rpcUsers,
                spec.verifierType,
                customOverrides = notaryConfig(clusterAddress)
        )

        // All other nodes will join the cluster
        val restNodeFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            startRegisteredNode(
                    it,
                    localNetworkMap,
                    spec.rpcUsers,
                    spec.verifierType,
                    customOverrides = notaryConfig(nodeAddress, clusterAddress)
            )
        }

        return firstNodeFuture.flatMap { first ->
            restNodeFutures.transpose().map { rest -> listOf(first) + rest }
        }
    }

    override fun baseDirectory(nodeName: CordaX500Name): Path {
        val nodeDirectoryName = nodeName.organisation.filter { !it.isWhitespace() }
        return driverDirectory / nodeDirectoryName
    }

    /**
     * @param initial number of nodes currently in the network map of a running node.
     * @param networkMapCacheChangeObservable an observable returning the updates to the node network map.
     * @return a [ConnectableObservable] which emits a new [Int] every time the number of registered nodes changes
     *   the initial value emitted is always [initial]
     */
    private fun nodeCountObservable(initial: Int, networkMapCacheChangeObservable: Observable<NetworkMapCache.MapChange>):
            ConnectableObservable<Int> {
        val count = AtomicInteger(initial)
        return networkMapCacheChangeObservable.map {
            when (it) {
                is NetworkMapCache.MapChange.Added -> count.incrementAndGet()
                is NetworkMapCache.MapChange.Removed -> count.decrementAndGet()
                is NetworkMapCache.MapChange.Modified -> count.get()
            }
        }.startWith(initial).replay()
    }

    /**
     * @param rpc the [CordaRPCOps] of a newly started node.
     * @return a [CordaFuture] which resolves when every node started by driver has in its network map a number of nodes
     *   equal to the number of running nodes. The future will yield the number of connected nodes.
     */
    private fun allNodesConnected(rpc: CordaRPCOps): CordaFuture<Int> {
        val (snapshot, updates) = rpc.networkMapFeed()
        val counterObservable = nodeCountObservable(snapshot.size, updates)
        countObservables[rpc.nodeInfo().legalIdentities[0].name] = counterObservable
        /* TODO: this might not always be the exact number of nodes one has to wait for,
         * for example in the following sequence
         * 1 start 3 nodes in order, A, B, C.
         * 2 before the future returned by this function resolves, kill B
         * At that point this future won't ever resolve as it will wait for nodes to know 3 other nodes.
         */
        val requiredNodes = countObservables.size

        // This is an observable which yield the minimum number of nodes in each node network map.
        val smallestSeenNetworkMapSize = Observable.combineLatest(countObservables.values.toList()) { args: Array<Any> ->
            args.map { it as Int }.min() ?: 0
        }
        val future = smallestSeenNetworkMapSize.filter { it >= requiredNodes }.toFuture()
        counterObservable.connect()
        return future
    }

    /**
     * Start the node with the given flag which is expected to start the node for some function, which once complete will
     * terminate the node.
     */
    private fun startOutOfProcessMiniNode(config: NodeConfig, vararg extraCmdLineFlag: String): CordaFuture<Unit> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val monitorPort = if (jmxPolicy.startJmxHttpServer) jmxPolicy.jmxHttpServerPortAllocation?.nextPort() else null
        val process = startOutOfProcessNode(
                config,
                quasarJarPath,
                debugPort,
                jolokiaJarPath,
                monitorPort,
                systemProperties,
                cordappPackages,
                "512m",
                *extraCmdLineFlag
        )

        return poll(executorService, "$extraCmdLineFlag (${config.corda.myLegalName})") {
            if (process.isAlive) null else Unit
        }
    }

    private fun startNodeInternal(config: NodeConfig,
                                  webAddress: NetworkHostAndPort,
                                  startInProcess: Boolean?,
                                  maximumHeapSize: String,
                                  localNetworkMap: LocalNetworkMap?): CordaFuture<NodeHandle> {
        val baseDirectory = config.corda.baseDirectory.createDirectories()
        localNetworkMap?.networkParametersCopier?.install(baseDirectory)
        localNetworkMap?.nodeInfosCopier?.addConfig(baseDirectory)

        val onNodeExit: () -> Unit = {
            localNetworkMap?.nodeInfosCopier?.removeConfig(baseDirectory)
            countObservables.remove(config.corda.myLegalName)
            synchronized(nodeNames) {
                nodeNames.remove(config.corda.myLegalName)
            }
        }

        val useHTTPS = config.typesafe.run { hasPath("useHTTPS") && getBoolean("useHTTPS") }

        if (startInProcess ?: startNodesInProcess) {
            val nodeAndThreadFuture = startInProcessNode(executorService, config, cordappPackages)
            shutdownManager.registerShutdown(
                    nodeAndThreadFuture.map { (node, thread) ->
                        {
                            node.dispose()
                            thread.interrupt()
                        }
                    }
            )
            return nodeAndThreadFuture.flatMap { (node, thread) ->
                establishRpc(config, openFuture()).flatMap { rpc ->
                    allNodesConnected(rpc).map {
                        InProcessImpl(rpc.nodeInfo(), rpc, config.corda, webAddress, useHTTPS, thread, onNodeExit, node)
                    }
                }
            }
        } else {
            val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
            val monitorPort = if (jmxPolicy.startJmxHttpServer) jmxPolicy.jmxHttpServerPortAllocation?.nextPort() else null
            val process = startOutOfProcessNode(config, quasarJarPath, debugPort, jolokiaJarPath, monitorPort, systemProperties, cordappPackages, maximumHeapSize)
            if (waitForAllNodesToFinish) {
                state.locked {
                    processes += process
                }
            } else {
                shutdownManager.registerProcessShutdown(process)
            }
            val p2pReadyFuture = addressMustBeBoundFuture(executorService, config.corda.p2pAddress, process)
            return p2pReadyFuture.flatMap {
                val processDeathFuture = poll(executorService, "process death while waiting for RPC (${config.corda.myLegalName})") {
                    if (process.isAlive) null else process
                }
                establishRpc(config, processDeathFuture).flatMap { rpc ->
                    // Check for all nodes to have all other nodes in background in case RPC is failing over:
                    val networkMapFuture = executorService.fork { allNodesConnected(rpc) }.flatMap { it }
                    firstOf(processDeathFuture, networkMapFuture) {
                        if (it == processDeathFuture) {
                            throw ListenProcessDeathException(config.corda.p2pAddress, process)
                        }
                        processDeathFuture.cancel(false)
                        log.info("Node handle is ready. NodeInfo: ${rpc.nodeInfo()}, WebAddress: $webAddress")
                        OutOfProcessImpl(rpc.nodeInfo(), rpc, config.corda, webAddress, useHTTPS, debugPort, process, onNodeExit)
                    }
                }
            }
        }
    }

    override fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration, warnCount: Int, check: () -> A?): CordaFuture<A> {
        val pollFuture = poll(executorService, pollName, pollInterval, warnCount, check)
        shutdownManager.registerShutdown { pollFuture.cancel(true) }
        return pollFuture
    }

    /**
     * The local version of the network map, which is a bunch of classes that copy the relevant files to the node directories.
     */
    private inner class LocalNetworkMap(notaryInfos: List<NotaryInfo>) {
        val networkParametersCopier = NetworkParametersCopier(networkParameters.copy(notaries = notaryInfos))
        // TODO: this object will copy NodeInfo files from started nodes to other nodes additional-node-infos/
        // This uses the FileSystem and adds a delay (~5 seconds) given by the time we wait before polling the file system.
        // Investigate whether we can avoid that.
        val nodeInfosCopier = NodeInfoFilesCopier().also { shutdownManager.registerShutdown(it::close) }
    }

    /**
     * Simple holder class to capture the node configuration both as the raw [Config] object and the parsed [NodeConfiguration].
     * Keeping [Config] around is needed as the user may specify extra config options not specified in [NodeConfiguration].
     */
    private class NodeConfig(val typesafe: Config, val corda: NodeConfiguration = typesafe.parseAsNodeConfiguration().also { nodeConfiguration ->
        val errors = nodeConfiguration.validate()
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Invalid node configuration. Errors where:${System.lineSeparator()}${errors.joinToString(System.lineSeparator())}")
        }
    })

    companion object {
        internal val log = contextLogger()

        private val defaultRpcUserList = listOf(InternalUser("default", "default", setOf("ALL")).toConfig().root().unwrapped())
        private val names = arrayOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME)
        /**
         * A sub-set of permissions that grant most of the essential operations used in the unit/integration tests as well as
         * in demo application like NodeExplorer.
         */
        private val DRIVER_REQUIRED_PERMISSIONS = setOf(
                Permissions.invokeRpc(CordaRPCOps::nodeInfo),
                Permissions.invokeRpc(CordaRPCOps::networkMapFeed),
                Permissions.invokeRpc(CordaRPCOps::networkMapSnapshot),
                Permissions.invokeRpc(CordaRPCOps::notaryIdentities),
                Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
                Permissions.invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingFeed),
                Permissions.invokeRpc(CordaRPCOps::nodeInfoFromParty),
                Permissions.invokeRpc(CordaRPCOps::internalVerifiedTransactionsFeed),
                Permissions.invokeRpc("vaultQueryBy"),
                Permissions.invokeRpc("vaultTrackBy"),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows)
        )

        private fun <A> oneOf(array: Array<A>) = array[Random().nextInt(array.size)]

        private fun startInProcessNode(
                executorService: ScheduledExecutorService,
                config: NodeConfig,
                cordappPackages: List<String>
        ): CordaFuture<Pair<StartedNode<Node>, Thread>> {
            return executorService.fork {
                log.info("Starting in-process Node ${config.corda.myLegalName.organisation}")
                if (!(ManagementFactory.getRuntimeMXBean().inputArguments.any { it.contains("quasar") })) {
                    throw IllegalStateException("No quasar agent: -javaagent:lib/quasar.jar and working directory project root might fix")
                }
                // Write node.conf
                writeConfig(config.corda.baseDirectory, "node.conf", config.typesafe.toNodeOnly())
                // TODO pass the version in?
                val node = InProcessNode(config.corda, MOCK_VERSION_INFO, cordappPackages).start()
                val nodeThread = thread(name = config.corda.myLegalName.organisation) {
                    node.internals.run()
                }
                node to nodeThread
            }.flatMap { nodeAndThread ->
                addressMustBeBoundFuture(executorService, config.corda.p2pAddress).map { nodeAndThread }
            }
        }

        private fun startOutOfProcessNode(
                config: NodeConfig,
                quasarJarPath: String,
                debugPort: Int?,
                jolokiaJarPath: String,
                monitorPort: Int?,
                overriddenSystemProperties: Map<String, String>,
                cordappPackages: List<String>,
                maximumHeapSize: String,
                vararg extraCmdLineFlag: String
        ): Process {
            log.info("Starting out-of-process Node ${config.corda.myLegalName.organisation}, " +
                    "debug port is " + (debugPort ?: "not enabled") + ", " +
                    "jolokia monitoring port is " + (monitorPort ?: "not enabled"))
            // Write node.conf
            writeConfig(config.corda.baseDirectory, "node.conf", config.typesafe.toNodeOnly())

            val systemProperties = mutableMapOf(
                    "name" to config.corda.myLegalName,
                    "visualvm.display.name" to "corda-${config.corda.myLegalName}",
                    "java.io.tmpdir" to System.getProperty("java.io.tmpdir"), // Inherit from parent process
                    "log4j2.debug" to if (debugPort != null) "true" else "false"
            )

            if (cordappPackages.isNotEmpty()) {
                systemProperties += Node.scanPackagesSystemProperty to cordappPackages.joinToString(Node.scanPackagesSeparator)
            }

            systemProperties += overriddenSystemProperties

            // See experimental/quasar-hook/README.md for how to generate.
            val excludePattern = "x(antlr**;bftsmart**;ch**;co.paralleluniverse**;com.codahale**;com.esotericsoftware**;" +
                    "com.fasterxml**;com.google**;com.ibm**;com.intellij**;com.jcabi**;com.nhaarman**;com.opengamma**;" +
                    "com.typesafe**;com.zaxxer**;de.javakaffee**;groovy**;groovyjarjarantlr**;groovyjarjarasm**;io.atomix**;" +
                    "io.github**;io.netty**;jdk**;joptsimple**;junit**;kotlin**;net.bytebuddy**;net.i2p**;org.apache**;" +
                    "org.assertj**;org.bouncycastle**;org.codehaus**;org.crsh**;org.dom4j**;org.fusesource**;org.h2**;" +
                    "org.hamcrest**;org.hibernate**;org.jboss**;org.jcp**;org.joda**;org.junit**;org.mockito**;org.objectweb**;" +
                    "org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**;org.jolokia**;)"
            val extraJvmArguments = systemProperties.removeResolvedClasspath().map { "-D${it.key}=${it.value}" } +
                    "-javaagent:$quasarJarPath=$excludePattern"
            val jolokiaAgent = monitorPort?.let { "-javaagent:$jolokiaJarPath=port=$monitorPort,host=localhost" }
            val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

            val arguments = mutableListOf(
                    "--base-directory=${config.corda.baseDirectory}",
                    "--logging-level=$loggingLevel",
                    "--no-local-shell").also {
                it += extraCmdLineFlag
            }.toList()

            return ProcessUtilities.startCordaProcess(
                    className = "net.corda.node.Corda", // cannot directly get class for this, so just use string
                    arguments = arguments,
                    jdwpPort = debugPort,
                    extraJvmArguments = extraJvmArguments + listOfNotNull(jolokiaAgent),
                    errorLogPath = config.corda.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME / "error.log",
                    workingDirectory = config.corda.baseDirectory,
                    maximumHeapSize = maximumHeapSize
            )
        }

        private fun startWebserver(handle: NodeHandleInternal, debugPort: Int?, maximumHeapSize: String): Process {
            val className = "net.corda.webserver.WebServer"
            writeConfig(handle.baseDirectory, "web-server.conf", handle.toWebServerConfig())
            return ProcessUtilities.startCordaProcess(
                    className = className, // cannot directly get class for this, so just use string
                    arguments = listOf("--base-directory", handle.baseDirectory.toString()),
                    jdwpPort = debugPort,
                    extraJvmArguments = listOf(
                            "-Dname=node-${handle.p2pAddress}-webserver",
                            "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}" // Inherit from parent process
                    ),
                    errorLogPath = Paths.get("error.$className.log"),
                    workingDirectory = null,
                    maximumHeapSize = maximumHeapSize
            )
        }

        private fun NodeHandleInternal.toWebServerConfig(): Config {

            var config = ConfigFactory.empty()
            config += "webAddress" to webAddress.toString()
            config += "myLegalName" to configuration.myLegalName.toString()
            config += "rpcAddress" to configuration.rpcOptions.address!!.toString()
            config += "rpcUsers" to configuration.toConfig().getValue("rpcUsers")
            config += "useHTTPS" to useHTTPS
            config += "baseDirectory" to configuration.baseDirectory.toAbsolutePath().toString()
            config += "keyStorePassword" to configuration.keyStorePassword
            config += "trustStorePassword" to configuration.trustStorePassword
            return config
        }

        private operator fun Config.plus(property: Pair<String, Any>) = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))

        /**
         * Get the package of the caller to the driver so that it can be added to the list of packages the nodes will scan.
         * This makes the driver automatically pick the CorDapp module that it's run from.
         *
         * This returns List<String> rather than String? to make it easier to bolt onto extraCordappPackagesToScan.
         */
        private fun getCallerPackage(): List<String> {
            val stackTrace = Throwable().stackTrace
            val index = stackTrace.indexOfLast { it.className == "net.corda.testing.driver.Driver" }
            // In this case we're dealing with the the RPCDriver or one of it's cousins which are internal and we don't care about them
            if (index == -1) return emptyList()
            val callerPackage = Class.forName(stackTrace[index + 1].className).`package` ?:
                    throw IllegalStateException("Function instantiating driver must be defined in a package.")
            return listOf(callerPackage.name)
        }

        /**
         * We have an alternative way of specifying classpath for spawned process: by using "-cp" option. So duplicating the setting of this
         * rather long string is un-necessary and can be harmful on Windows.
         */
        private fun Map<String, Any>.removeResolvedClasspath(): Map<String, Any> {
            return filterNot { it.key == "java.class.path" }
        }
    }
}

interface InternalDriverDSL : DriverDSL, CordformContext {
    private companion object {
        private val DEFAULT_POLL_INTERVAL = 500.millis
        private const val DEFAULT_WARN_COUNT = 120
    }

    val shutdownManager: ShutdownManager

    override fun baseDirectory(nodeName: String): Path = baseDirectory(CordaX500Name.parse(nodeName))

    /**
     * Polls a function until it returns a non-null value. Note that there is no timeout on the polling.
     *
     * @param pollName A description of what is being polled.
     * @param pollInterval The interval of polling.
     * @param warnCount The number of polls after the Driver gives a warning.
     * @param check The function being polled.
     * @return A future that completes with the non-null value [check] has returned.
     */
    fun <A> pollUntilNonNull(pollName: String, pollInterval: Duration = DEFAULT_POLL_INTERVAL, warnCount: Int = DEFAULT_WARN_COUNT, check: () -> A?): CordaFuture<A>

    /**
     * Polls the given function until it returns true.
     * @see pollUntilNonNull
     */
    fun pollUntilTrue(pollName: String, pollInterval: Duration = DEFAULT_POLL_INTERVAL, warnCount: Int = DEFAULT_WARN_COUNT, check: () -> Boolean): CordaFuture<Unit> {
        return pollUntilNonNull(pollName, pollInterval, warnCount) { if (check()) Unit else null }
    }

    fun start()

    fun shutdown()
}

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSL
 *   interface SomeOtherInternalDSLInterface : InternalDriverDSL, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSLImpl) : InternalDriverDSL by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSL, D : InternalDriverDSL, A> genericDriver(
        driverDsl: D,
        initialiseSerialization: Boolean = true,
        coerce: (D) -> DI,
        dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(initialiseSerialization)
    val shutdownHook = addShutdownHook(driverDsl::shutdown)
    try {
        driverDsl.start()
        return dsl(coerce(driverDsl))
    } catch (exception: Throwable) {
        DriverDSLImpl.log.error("Driver shutting down because of exception", exception)
        throw exception
    } finally {
        driverDsl.shutdown()
        shutdownHook.cancel()
        serializationEnv.unset()
    }
}

/**
 * This is a helper method to allow extending of the DSL, along the lines of
 *   interface SomeOtherExposedDSLInterface : DriverDSL
 *   interface SomeOtherInternalDSLInterface : InternalDriverDSL, SomeOtherExposedDSLInterface
 *   class SomeOtherDSL(val driverDSL : DriverDSLImpl) : InternalDriverDSL by driverDSL, SomeOtherInternalDSLInterface
 *
 * @param coerce We need this explicit coercion witness because we can't put an extra DI : D bound in a `where` clause.
 */
fun <DI : DriverDSL, D : InternalDriverDSL, A> genericDriver(
        defaultParameters: DriverParameters = DriverParameters(),
        driverDslWrapper: (DriverDSLImpl) -> D,
        coerce: (D) -> DI, dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(defaultParameters.initialiseSerialization)
    val driverDsl = driverDslWrapper(
            DriverDSLImpl(
                    portAllocation = defaultParameters.portAllocation,
                    debugPortAllocation = defaultParameters.debugPortAllocation,
                    systemProperties = defaultParameters.systemProperties,
                    driverDirectory = defaultParameters.driverDirectory.toAbsolutePath(),
                    useTestClock = defaultParameters.useTestClock,
                    isDebug = defaultParameters.isDebug,
                    startNodesInProcess = defaultParameters.startNodesInProcess,
                    waitForAllNodesToFinish = defaultParameters.waitForAllNodesToFinish,
                    extraCordappPackagesToScan = defaultParameters.extraCordappPackagesToScan,
                    jmxPolicy = defaultParameters.jmxPolicy,
                    notarySpecs = defaultParameters.notarySpecs,
                    compatibilityZone = null,
                    networkParameters = defaultParameters.networkParameters
            )
    )
    val shutdownHook = addShutdownHook(driverDsl::shutdown)
    try {
        driverDsl.start()
        return dsl(coerce(driverDsl))
    } catch (exception: Throwable) {
        DriverDSLImpl.log.error("Driver shutting down because of exception", exception)
        throw exception
    } finally {
        driverDsl.shutdown()
        shutdownHook.cancel()
        serializationEnv.unset()
    }
}

/**
 * Internal API to enable testing of the network map service and node registration process using the internal driver.
 * @property url The base CZ URL for registration and network map updates
 * @property publishNotaries Hook for a network map server to capture the generated [NotaryInfo] objects needed for
 * creating the network parameters. This is needed as the network map server is expected to distribute it. The callback
 * will occur on a different thread to the driver-calling thread.
 * @property rootCert If specified then the nodes will register themselves with the doorman service using [url] and expect
 * the registration response to be rooted at this cert. If not specified then no registration is performed and the dev
 * root cert is used as normal.
 */
data class CompatibilityZoneParams(val url: URL, val publishNotaries: (List<NotaryInfo>) -> Unit, val rootCert: X509Certificate? = null)

fun <A> internalDriver(
        isDebug: Boolean = DriverParameters().isDebug,
        driverDirectory: Path = DriverParameters().driverDirectory,
        portAllocation: PortAllocation = DriverParameters().portAllocation,
        debugPortAllocation: PortAllocation = DriverParameters().debugPortAllocation,
        systemProperties: Map<String, String> = DriverParameters().systemProperties,
        useTestClock: Boolean = DriverParameters().useTestClock,
        initialiseSerialization: Boolean = true,
        startNodesInProcess: Boolean = DriverParameters().startNodesInProcess,
        waitForAllNodesToFinish: Boolean = DriverParameters().waitForAllNodesToFinish,
        notarySpecs: List<NotarySpec> = DriverParameters().notarySpecs,
        extraCordappPackagesToScan: List<String> = DriverParameters().extraCordappPackagesToScan,
        jmxPolicy: JmxPolicy = DriverParameters().jmxPolicy,
        networkParameters: NetworkParameters = DriverParameters().networkParameters,
        compatibilityZone: CompatibilityZoneParams? = null,
        dsl: DriverDSLImpl.() -> A
): A {
    return genericDriver(
            driverDsl = DriverDSLImpl(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForAllNodesToFinish = waitForAllNodesToFinish,
                    notarySpecs = notarySpecs,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    jmxPolicy = jmxPolicy,
                    compatibilityZone = compatibilityZone,
                    networkParameters = networkParameters
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = initialiseSerialization
    )
}

fun getTimestampAsDirectoryName(): String {
    return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSS").withZone(UTC).format(Instant.now())
}

fun writeConfig(path: Path, filename: String, config: Config) {
    val configString = config.root().render(ConfigRenderOptions.defaults())
    configString.byteInputStream().copyTo(path / filename, StandardCopyOption.REPLACE_EXISTING)
}

private fun Config.toNodeOnly(): Config {

    return if (hasPath("webAddress")) {
        withoutPath("webAddress").withoutPath("useHTTPS")
    } else {
        this
    }
}

private operator fun Config.plus(property: Pair<String, Any>) = withValue(property.first, ConfigValueFactory.fromAnyRef(property.second))