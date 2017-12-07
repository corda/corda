package net.corda.testing.node.internal

import com.google.common.collect.HashMultimap
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformNode
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.firstOf
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.concurrent.*
import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NotaryService
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.node.internal.Node
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.StartedNode
import net.corda.node.services.Permissions
import net.corda.node.services.config.*
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.RaftNonValidatingNotaryService
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.ServiceIdentityGenerator
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.addOrReplaceCertificate
import net.corda.nodeapi.internal.crypto.loadOrCreateKeyStore
import net.corda.nodeapi.internal.crypto.save
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.nodeapi.internal.network.NotaryInfo
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import net.corda.testing.DUMMY_BANK_A_NAME
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.driver.*
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.DriverDSLImpl.ClusterType.NON_VALIDATING_RAFT
import net.corda.testing.node.internal.DriverDSLImpl.ClusterType.VALIDATING_RAFT
import net.corda.testing.setGlobalSerialization
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import rx.observables.ConnectableObservable
import rx.schedulers.Schedulers
import java.net.ConnectException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class DriverDSLImpl(
        val portAllocation: PortAllocation,
        val debugPortAllocation: PortAllocation,
        val systemProperties: Map<String, String>,
        val driverDirectory: Path,
        val useTestClock: Boolean,
        val isDebug: Boolean,
        val startNodesInProcess: Boolean,
        val waitForNodesToFinish: Boolean,
        extraCordappPackagesToScan: List<String>,
        val jmxPolicy: JmxPolicy,
        val notarySpecs: List<NotarySpec>,
        val compatibilityZone: CompatibilityZoneParams?
) : InternalDriverDSL {
    private var _executorService: ScheduledExecutorService? = null
    val executorService get() = _executorService!!
    private var _shutdownManager: ShutdownManager? = null
    override val shutdownManager get() = _shutdownManager!!
    private val cordappPackages = extraCordappPackagesToScan + getCallerPackage()
    // TODO: this object will copy NodeInfo files from started nodes to other nodes additional-node-infos/
    // This uses the FileSystem and adds a delay (~5 seconds) given by the time we wait before polling the file system.
    // Investigate whether we can avoid that.
    private var nodeInfoFilesCopier: NodeInfoFilesCopier? = null
    // Map from a nodes legal name to an observable emitting the number of nodes in its network map.
    private val countObservables = mutableMapOf<CordaX500Name, Observable<Int>>()
    private lateinit var _notaries: List<NotaryHandle>
    override val notaryHandles: List<NotaryHandle> get() = _notaries
    private var networkParameters: NetworkParametersCopier? = null

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
        }
        catch(e: Exception) {
            log.warn("Unable to locate JAR `$jarNamePattern` on classpath: ${e.message}", e)
            throw e
        }
    }

    override fun shutdown() {
        if (waitForNodesToFinish) {
            state.locked {
                processes.forEach { it.waitFor() }
            }
        }
        _shutdownManager?.shutdown()
        _executorService?.shutdownNow()
    }

    private fun establishRpc(config: NodeConfiguration, processDeathFuture: CordaFuture<out Process>): CordaFuture<CordaRPCOps> {
        val rpcAddress = config.rpcAddress!!
        val client = CordaRPCClient(rpcAddress)
        val connectionFuture = poll(executorService, "RPC connection") {
            try {
                client.start(config.rpcUsers[0].username, config.rpcUsers[0].password)
            } catch (e: Exception) {
                if (processDeathFuture.isDone) throw e
                log.error("Exception $e, Retrying RPC connection at $rpcAddress")
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
        val name = providedName ?: CordaX500Name(organisation = "${oneOf(names).organisation}-${p2pAddress.port}", locality = "London", country = "GB")

        val registrationFuture = if (compatibilityZone?.rootCert != null) {
            nodeRegistration(name, compatibilityZone.rootCert, compatibilityZone.url)
        } else {
            doneFuture(Unit)
        }

        return registrationFuture.flatMap {
            val rpcAddress = portAllocation.nextHostAndPort()
            val webAddress = portAllocation.nextHostAndPort()
            val users = rpcUsers.map { it.copy(permissions = it.permissions + DRIVER_REQUIRED_PERMISSIONS) }
            val configMap = configOf(
                    "myLegalName" to name.toString(),
                    "p2pAddress" to p2pAddress.toString(),
                    "rpcAddress" to rpcAddress.toString(),
                    "webAddress" to webAddress.toString(),
                    "useTestClock" to useTestClock,
                    "rpcUsers" to if (users.isEmpty()) defaultRpcUserList else users.map { it.toConfig().root().unwrapped() },
                    "verifierType" to verifierType.name
            ) + customOverrides
            val config = ConfigHelper.loadConfig(
                    baseDirectory = baseDirectory(name),
                    allowMissingConfig = true,
                    configOverrides = if (compatibilityZone != null) {
                        configMap + mapOf("compatibilityZoneURL" to compatibilityZone.url.toString())
                    } else {
                        configMap
                    }
            )
            startNodeInternal(config, webAddress, startInSameProcess, maximumHeapSize)
        }
    }

    private fun nodeRegistration(providedName: CordaX500Name, rootCert: X509Certificate, compatibilityZoneURL: URL): CordaFuture<Unit> {
        val baseDirectory = baseDirectory(providedName).createDirectories()
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "p2pAddress" to "localhost:1222", // required argument, not really used
                        "compatibilityZoneURL" to compatibilityZoneURL.toString(),
                        "myLegalName" to providedName.toString())
        )
        val configuration = config.parseAsNodeConfiguration()

        configuration.trustStoreFile.parent.createDirectories()
        loadOrCreateKeyStore(configuration.trustStoreFile, configuration.trustStorePassword).also {
            it.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
            it.save(configuration.trustStoreFile, configuration.trustStorePassword)
        }

        return if (startNodesInProcess) {
            // This is a bit cheating, we're not starting a full node, we're just calling the code nodes call
            // when registering.
            NetworkRegistrationHelper(configuration, HTTPNetworkRegistrationService(compatibilityZoneURL)).buildKeystore()
            doneFuture(Unit)
        } else {
            startOutOfProcessNodeRegistration(config, configuration)
        }
    }

    private enum class ClusterType(val validating: Boolean, val clusterName: CordaX500Name) {
        VALIDATING_RAFT(true, CordaX500Name(RaftValidatingNotaryService.id, "Raft", "Zurich", "CH")),
        NON_VALIDATING_RAFT(false, CordaX500Name(RaftNonValidatingNotaryService.id, "Raft", "Zurich", "CH")),
        NON_VALIDATING_BFT(false, CordaX500Name(BFTNonValidatingNotaryService.id, "BFT", "Zurich", "CH"))
    }

    internal fun startCordformNodes(cordforms: List<CordformNode>): CordaFuture<*> {
        check(compatibilityZone == null) { "Cordform nodes should be run without compatibilityZone configuration" }
        val clusterNodes = HashMultimap.create<ClusterType, CordaX500Name>()
        val notaryInfos = ArrayList<NotaryInfo>()

        // Go though the node definitions and pick out the notaries so that we can generate their identities to be used
        // in the network parameters
        for (cordform in cordforms) {
            if (cordform.notary == null) continue
            val name = CordaX500Name.parse(cordform.name)
            val notaryConfig = ConfigFactory.parseMap(cordform.notary).parseAs<NotaryConfig>()
            // We need to first group the nodes that form part of a cluser. We assume for simplicity that nodes of the
            // same cluster type and validating flag are part of the same cluster.
            if (notaryConfig.raft != null) {
                val key = if (notaryConfig.validating) VALIDATING_RAFT else NON_VALIDATING_RAFT
                clusterNodes.put(key, name)
            } else if (notaryConfig.bftSMaRt != null) {
                clusterNodes.put(ClusterType.NON_VALIDATING_BFT, name)
            } else {
                // We have all we need here to generate the identity for single node notaries
                val identity = ServiceIdentityGenerator.generateToDisk(
                        dirs = listOf(baseDirectory(name)),
                        serviceName = name,
                        serviceId = "identity"
                )
                notaryInfos += NotaryInfo(identity, notaryConfig.validating)
            }
        }

        clusterNodes.asMap().forEach { type, nodeNames ->
            val identity = ServiceIdentityGenerator.generateToDisk(
                    dirs = nodeNames.map { baseDirectory(it) },
                    serviceName = type.clusterName,
                    serviceId = NotaryService.constructId(
                            validating = type.validating,
                            raft = type in setOf(VALIDATING_RAFT, NON_VALIDATING_RAFT),
                            bft = type == ClusterType.NON_VALIDATING_BFT
                    )
            )
            notaryInfos += NotaryInfo(identity, type.validating)
        }

        networkParameters = NetworkParametersCopier(testNetworkParameters(notaryInfos))

        return cordforms.map {
            val startedNode = startCordformNode(it)
            if (it.webAddress != null) {
                // Start a webserver if an address for it was specified
                startedNode.flatMap { startWebserver(it) }
            } else {
                startedNode
            }
        }.transpose()
    }

    private fun startCordformNode(cordform: CordformNode): CordaFuture<NodeHandle> {
        val name = CordaX500Name.parse(cordform.name)
        // TODO We shouldn't have to allocate an RPC or web address if they're not specified. We're having to do this because of startNodeInternal
        val rpcAddress = if (cordform.rpcAddress == null) mapOf("rpcAddress" to portAllocation.nextHostAndPort().toString()) else emptyMap()
        val webAddress = cordform.webAddress?.let { NetworkHostAndPort.parse(it) } ?: portAllocation.nextHostAndPort()
        val notary = if (cordform.notary != null) mapOf("notary" to cordform.notary) else emptyMap()
        val rpcUsers = cordform.rpcUsers
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory(name),
                allowMissingConfig = true,
                configOverrides = cordform.config + rpcAddress + notary + mapOf(
                        "rpcUsers" to if (rpcUsers.isEmpty()) defaultRpcUserList else rpcUsers
                )
        )
        return startNodeInternal(config, webAddress, null, "200m")
    }

    private fun queryWebserver(handle: NodeHandle, process: Process): WebserverHandle {
        val protocol = if (handle.configuration.useHTTPS) "https://" else "http://"
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
        val process = startWebserver(handle, debugPort, maximumHeapSize)
        shutdownManager.registerProcessShutdown(process)
        val webReadyFuture = addressMustBeBoundFuture(executorService, handle.webAddress, process)
        return webReadyFuture.map { queryWebserver(handle, process) }
    }

    override fun start() {
        if (startNodesInProcess) {
            Schedulers.reset()
        }
        _executorService = Executors.newScheduledThreadPool(2, ThreadFactoryBuilder().setNameFormat("driver-pool-thread-%d").build())
        _shutdownManager = ShutdownManager(executorService)
        if (compatibilityZone == null) {
            // Without a compatibility zone URL we have to copy the node info files ourselves to make sure the nodes see each other
            nodeInfoFilesCopier = NodeInfoFilesCopier().also {
                shutdownManager.registerShutdown(it::close)
            }
        }
        val notaryInfos = generateNotaryIdentities()
        // The network parameters must be serialised before starting any of the nodes
        if (compatibilityZone == null) networkParameters = NetworkParametersCopier(testNetworkParameters(notaryInfos))
        val nodeHandles = startNotaries()
        _notaries = notaryInfos.zip(nodeHandles) { (identity, validating), nodes -> NotaryHandle(identity, validating, nodes) }
    }

    private fun generateNotaryIdentities(): List<NotaryInfo> {
        return notarySpecs.map { spec ->
            val identity = if (spec.cluster == null) {
                ServiceIdentityGenerator.generateToDisk(
                        dirs = listOf(baseDirectory(spec.name)),
                        serviceName = spec.name,
                        serviceId = "identity",
                        customRootCert = compatibilityZone?.rootCert
                )
            } else {
                ServiceIdentityGenerator.generateToDisk(
                        dirs = generateNodeNames(spec).map { baseDirectory(it) },
                        serviceName = spec.name,
                        serviceId = NotaryService.constructId(
                                validating = spec.validating,
                                raft = spec.cluster is ClusterSpec.Raft
                        ),
                        customRootCert = compatibilityZone?.rootCert
                )
            }
            NotaryInfo(identity, spec.validating)
        }
    }

    private fun generateNodeNames(spec: NotarySpec): List<CordaX500Name> {
        return (0 until spec.cluster!!.clusterSize).map { spec.name.copy(organisation = "${spec.name.organisation}-$it") }
    }

    private fun startNotaries(): List<CordaFuture<List<NodeHandle>>> {
        return notarySpecs.map {
            when {
                it.cluster == null -> startSingleNotary(it)
                it.cluster is ClusterSpec.Raft -> startRaftNotaryCluster(it)
                else -> throw IllegalArgumentException("BFT-SMaRt not supported")
            }
        }
    }

    // TODO This mapping is done is several places including the gradle plugin. In general we need a better way of
    // generating the configs for the nodes, probably making use of Any.toConfig()
    private fun NotaryConfig.toConfigMap(): Map<String, Any> = mapOf("notary" to toConfig().root().unwrapped())

    private fun startSingleNotary(spec: NotarySpec): CordaFuture<List<NodeHandle>> {
        return startNode(
                providedName = spec.name,
                rpcUsers = spec.rpcUsers,
                verifierType = spec.verifierType,
                customOverrides = NotaryConfig(spec.validating).toConfigMap()
        ).map { listOf(it) }
    }

    private fun startRaftNotaryCluster(spec: NotarySpec): CordaFuture<List<NodeHandle>> {
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
        val firstNodeFuture = startNode(
                providedName = nodeNames[0],
                rpcUsers = spec.rpcUsers,
                verifierType = spec.verifierType,
                customOverrides = notaryConfig(clusterAddress) + mapOf(
                        "database.serverNameTablePrefix" to nodeNames[0].toString().replace(Regex("[^0-9A-Za-z]+"), "")
                )
        )

        // All other nodes will join the cluster
        val restNodeFutures = nodeNames.drop(1).map {
            val nodeAddress = portAllocation.nextHostAndPort()
            startNode(
                    providedName = it,
                    rpcUsers = spec.rpcUsers,
                    verifierType = spec.verifierType,
                    customOverrides = notaryConfig(nodeAddress, clusterAddress) + mapOf(
                            "database.serverNameTablePrefix" to it.toString().replace(Regex("[^0-9A-Za-z]+"), "")
                    )
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

    override fun baseDirectory(nodeName: String): Path = baseDirectory(CordaX500Name.parse(nodeName))

    /**
     * @param initial number of nodes currently in the network map of a running node.
     * @param networkMapCacheChangeObservable an observable returning the updates to the node network map.
     * @return a [ConnectableObservable] which emits a new [Int] every time the number of registered nodes changes
     *   the initial value emitted is always [initial]
     */
    private fun nodeCountObservable(initial: Int, networkMapCacheChangeObservable: Observable<NetworkMapCache.MapChange>):
            ConnectableObservable<Int> {
        val count = AtomicInteger(initial)
        return networkMapCacheChangeObservable.map { it ->
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

    private fun startOutOfProcessNodeRegistration(config: Config, configuration: NodeConfiguration): CordaFuture<Unit> {
        val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
        val monitorPort = if (jmxPolicy.startJmxHttpServer) jmxPolicy.jmxHttpServerPortAllocation?.nextPort() else null
        val process = startOutOfProcessNode(configuration, config, quasarJarPath, debugPort, jolokiaJarPath, monitorPort,
                systemProperties, cordappPackages, "200m", initialRegistration = true)

        return poll(executorService, "node registration (${configuration.myLegalName})") {
            if (process.isAlive) null else Unit
        }
    }

    private fun startNodeInternal(config: Config,
                                  webAddress: NetworkHostAndPort,
                                  startInProcess: Boolean?,
                                  maximumHeapSize: String): CordaFuture<NodeHandle> {
        val configuration = config.parseAsNodeConfiguration()
        val baseDirectory = configuration.baseDirectory.createDirectories()
        nodeInfoFilesCopier?.addConfig(baseDirectory)
        networkParameters?.install(baseDirectory)
        val onNodeExit: () -> Unit = {
            nodeInfoFilesCopier?.removeConfig(baseDirectory)
            countObservables.remove(configuration.myLegalName)
        }
        if (startInProcess ?: startNodesInProcess) {
            val nodeAndThreadFuture = startInProcessNode(executorService, configuration, config, cordappPackages)
            shutdownManager.registerShutdown(
                    nodeAndThreadFuture.map { (node, thread) ->
                        {
                            node.dispose()
                            thread.interrupt()
                        }
                    }
            )
            return nodeAndThreadFuture.flatMap { (node, thread) ->
                establishRpc(configuration, openFuture()).flatMap { rpc ->
                    allNodesConnected(rpc).map {
                        NodeHandle.InProcess(rpc.nodeInfo(), rpc, configuration, webAddress, node, thread, onNodeExit)
                    }
                }
            }
        } else {
            val debugPort = if (isDebug) debugPortAllocation.nextPort() else null
            val monitorPort = if (jmxPolicy.startJmxHttpServer) jmxPolicy.jmxHttpServerPortAllocation?.nextPort() else null
            val process = startOutOfProcessNode(configuration, config, quasarJarPath, debugPort, jolokiaJarPath, monitorPort, systemProperties, cordappPackages, maximumHeapSize, initialRegistration = false)
            if (waitForNodesToFinish) {
                state.locked {
                    processes += process
                }
            } else {
                shutdownManager.registerProcessShutdown(process)
            }
            val p2pReadyFuture = addressMustBeBoundFuture(executorService, configuration.p2pAddress, process)
            return p2pReadyFuture.flatMap {
                val processDeathFuture = poll(executorService, "process death while waiting for RPC (${configuration.myLegalName})") {
                    if (process.isAlive) null else process
                }
                establishRpc(configuration, processDeathFuture).flatMap { rpc ->
                    // Check for all nodes to have all other nodes in background in case RPC is failing over:
                    val networkMapFuture = executorService.fork { allNodesConnected(rpc) }.flatMap { it }
                    firstOf(processDeathFuture, networkMapFuture) {
                        if (it == processDeathFuture) {
                            throw ListenProcessDeathException(configuration.p2pAddress, process)
                        }
                        processDeathFuture.cancel(false)
                        log.info("Node handle is ready. NodeInfo: ${rpc.nodeInfo()}, WebAddress: $webAddress")
                        NodeHandle.OutOfProcess(rpc.nodeInfo(), rpc, configuration, webAddress, debugPort, process,
                                onNodeExit)
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

    companion object {
        internal val log = contextLogger()

        private val defaultRpcUserList = listOf(User("default", "default", setOf("ALL")).toConfig().root().unwrapped())
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
                nodeConf: NodeConfiguration,
                config: Config,
                cordappPackages: List<String>
        ): CordaFuture<Pair<StartedNode<Node>, Thread>> {
            return executorService.fork {
                log.info("Starting in-process Node ${nodeConf.myLegalName.organisation}")
                // Write node.conf
                writeConfig(nodeConf.baseDirectory, "node.conf", config)
                // TODO pass the version in?
                val node = InProcessNode(nodeConf, MOCK_VERSION_INFO, cordappPackages).start()
                val nodeThread = thread(name = nodeConf.myLegalName.organisation) {
                    node.internals.run()
                }
                node to nodeThread
            }.flatMap { nodeAndThread ->
                addressMustBeBoundFuture(executorService, nodeConf.p2pAddress).map { nodeAndThread }
            }
        }

        private fun startOutOfProcessNode(
                nodeConf: NodeConfiguration,
                config: Config,
                quasarJarPath: String,
                debugPort: Int?,
                jolokiaJarPath: String,
                monitorPort: Int?,
                overriddenSystemProperties: Map<String, String>,
                cordappPackages: List<String>,
                maximumHeapSize: String,
                initialRegistration: Boolean
        ): Process {
            log.info("Starting out-of-process Node ${nodeConf.myLegalName.organisation}, debug port is " + (debugPort ?: "not enabled") + ", jolokia monitoring port is " + (monitorPort ?: "not enabled"))
            // Write node.conf
            writeConfig(nodeConf.baseDirectory, "node.conf", config)

            val systemProperties = mutableMapOf(
                    "name" to nodeConf.myLegalName,
                    "visualvm.display.name" to "corda-${nodeConf.myLegalName}",
                    "java.io.tmpdir" to System.getProperty("java.io.tmpdir"), // Inherit from parent process
                    "log4j2.debug" to if(debugPort != null) "true" else "false"
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
                    "org.objenesis**;org.slf4j**;org.w3c**;org.xml**;org.yaml**;reflectasm**;rx**)"
            val extraJvmArguments = systemProperties.removeResolvedClasspath().map { "-D${it.key}=${it.value}" } +
                    "-javaagent:$quasarJarPath=$excludePattern"
            val jolokiaAgent = monitorPort?.let { "-javaagent:$jolokiaJarPath=port=$monitorPort,host=localhost" }
            val loggingLevel = if (debugPort == null) "INFO" else "DEBUG"

            val arguments = mutableListOf(
                    "--base-directory=${nodeConf.baseDirectory}",
                    "--logging-level=$loggingLevel",
                    "--no-local-shell").also {
                if (initialRegistration) {
                    it += "--initial-registration"
                }
            }.toList()

            return ProcessUtilities.startCordaProcess(
                    className = "net.corda.node.Corda", // cannot directly get class for this, so just use string
                    arguments = arguments,
                    jdwpPort = debugPort,
                    extraJvmArguments = extraJvmArguments + listOfNotNull(jolokiaAgent),
                    errorLogPath = nodeConf.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME / "error.log",
                    workingDirectory = nodeConf.baseDirectory,
                    maximumHeapSize = maximumHeapSize
            )
        }

        private fun startWebserver(handle: NodeHandle, debugPort: Int?, maximumHeapSize: String): Process {
            val className = "net.corda.webserver.WebServer"
            return ProcessUtilities.startCordaProcess(
                    className = className, // cannot directly get class for this, so just use string
                    arguments = listOf("--base-directory", handle.configuration.baseDirectory.toString()),
                    jdwpPort = debugPort,
                    extraJvmArguments = listOf(
                            "-Dname=node-${handle.configuration.p2pAddress}-webserver",
                            "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}" // Inherit from parent process
                    ),
                    errorLogPath = Paths.get("error.$className.log"),
                    workingDirectory = null,
                    maximumHeapSize = maximumHeapSize
            )
        }

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
        isDebug: Boolean = defaultParameters.isDebug,
        driverDirectory: Path = defaultParameters.driverDirectory,
        portAllocation: PortAllocation = defaultParameters.portAllocation,
        debugPortAllocation: PortAllocation = defaultParameters.debugPortAllocation,
        systemProperties: Map<String, String> = defaultParameters.systemProperties,
        useTestClock: Boolean = defaultParameters.useTestClock,
        initialiseSerialization: Boolean = defaultParameters.initialiseSerialization,
        waitForNodesToFinish: Boolean = defaultParameters.waitForAllNodesToFinish,
        startNodesInProcess: Boolean = defaultParameters.startNodesInProcess,
        notarySpecs: List<NotarySpec>,
        extraCordappPackagesToScan: List<String> = defaultParameters.extraCordappPackagesToScan,
        jmxPolicy: JmxPolicy = JmxPolicy(),
        driverDslWrapper: (DriverDSLImpl) -> D,
        coerce: (D) -> DI, dsl: DI.() -> A
): A {
    val serializationEnv = setGlobalSerialization(initialiseSerialization)
    val driverDsl = driverDslWrapper(
            DriverDSLImpl(
                    portAllocation = portAllocation,
                    debugPortAllocation = debugPortAllocation,
                    systemProperties = systemProperties,
                    driverDirectory = driverDirectory.toAbsolutePath(),
                    useTestClock = useTestClock,
                    isDebug = isDebug,
                    startNodesInProcess = startNodesInProcess,
                    waitForNodesToFinish = waitForNodesToFinish,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    jmxPolicy = jmxPolicy,
                    notarySpecs = notarySpecs,
                    compatibilityZone = null
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
 * @property url The base CZ URL for registration and network map updates
 * @property rootCert If specified then the node will register itself using [url] and expect the registration response
 * to be rooted at this cert.
 */
data class CompatibilityZoneParams(val url: URL, val rootCert: X509Certificate? = null)

fun <A> internalDriver(
        isDebug: Boolean = DriverParameters().isDebug,
        driverDirectory: Path = DriverParameters().driverDirectory,
        portAllocation: PortAllocation = DriverParameters().portAllocation,
        debugPortAllocation: PortAllocation = DriverParameters().debugPortAllocation,
        systemProperties: Map<String, String> = DriverParameters().systemProperties,
        useTestClock: Boolean = DriverParameters().useTestClock,
        initialiseSerialization: Boolean = DriverParameters().initialiseSerialization,
        startNodesInProcess: Boolean = DriverParameters().startNodesInProcess,
        waitForAllNodesToFinish: Boolean = DriverParameters().waitForAllNodesToFinish,
        notarySpecs: List<NotarySpec> = DriverParameters().notarySpecs,
        extraCordappPackagesToScan: List<String> = DriverParameters().extraCordappPackagesToScan,
        jmxPolicy: JmxPolicy = DriverParameters().jmxPolicy,
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
                    waitForNodesToFinish = waitForAllNodesToFinish,
                    notarySpecs = notarySpecs,
                    extraCordappPackagesToScan = extraCordappPackagesToScan,
                    jmxPolicy = jmxPolicy,
                    compatibilityZone = compatibilityZone
            ),
            coerce = { it },
            dsl = dsl,
            initialiseSerialization = initialiseSerialization
    )
}

fun getTimestampAsDirectoryName(): String {
    // Add a random number in case 2 tests are started in the same instant.
    return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now()) + random63BitValue()
}

fun writeConfig(path: Path, filename: String, config: Config) {
    val configString = config.root().render(ConfigRenderOptions.defaults())
    configString.byteInputStream().copyTo(path / filename, StandardCopyOption.REPLACE_EXISTING)
}
