package net.corda.node.internal

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import com.github.benmanes.caffeine.cache.Caffeine
import com.palominolabs.metrics.newrelic.AllEnabledMetricAttributeFilter
import com.palominolabs.metrics.newrelic.NewRelicReporter
import io.netty.util.NettyRuntime
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.cliutils.ShellConstants
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.Emoji
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.internal.div
import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.internal.getJavaUpdateVersion
import net.corda.core.internal.notary.NotaryService
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.VersionInfo
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.artemis.BrokerAddresses
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.internal.security.RPCSecurityManagerWithAdditionalUser
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoCheckpointSerializer
import net.corda.node.services.Permissions
import net.corda.node.services.api.FlowStarter
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.node.services.rpc.ArtemisRpcBroker
import net.corda.node.services.rpc.InternalRPCMessagingClient
import net.corda.node.services.rpc.RPCServerConfiguration
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.*
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_SHELL_USER
import net.corda.nodeapi.internal.ShutdownHook
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.nodeapi.internal.bridging.BridgeControlListener
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import org.apache.commons.lang3.SystemUtils
import org.h2.jdbc.JdbcSQLNonTransientConnectionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Scheduler
import rx.schedulers.Schedulers
import java.lang.Long.max
import java.lang.Long.min
import java.net.BindException
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.management.ObjectName
import kotlin.system.exitProcess

class NodeWithInfo(val node: Node, val info: NodeInfo) {
    val services: StartedNodeServices = object : StartedNodeServices, ServiceHubInternal by node.services, FlowStarter by node.flowStarter {}
    fun dispose() = node.stop()
    fun <T : FlowLogic<*>> registerInitiatedFlow(initiatedFlowClass: Class<T>) = node.registerInitiatedFlow(node.smm, initiatedFlowClass)
}

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param configuration This is typically loaded from a TypeSafe HOCON configuration file.
 */
open class Node(configuration: NodeConfiguration,
                versionInfo: VersionInfo,
                private val initialiseSerialization: Boolean = true,
                flowManager: FlowManager = NodeFlowManager(configuration.flowOverrides),
                cacheFactoryPrototype: BindableNamedCacheFactory = DefaultNamedCacheFactory()
) : AbstractNode<NodeInfo>(
        configuration,
        createClock(configuration),
        cacheFactoryPrototype,
        versionInfo,
        flowManager,
        // Under normal (non-test execution) it will always be "1"
        AffinityExecutor.ServiceAffinityExecutor("Node thread-${sameVmNodeCounter.incrementAndGet()}", 1)
) {

    override fun createStartedNode(nodeInfo: NodeInfo, rpcOps: CordaRPCOps, notaryService: NotaryService?): NodeInfo =
            nodeInfo

    companion object {
        private val staticLog = contextLogger()
        var renderBasicInfoToConsole = true

        /** Used for useful info that we always want to show, even when not logging to the console */
        fun printBasicNodeInfo(description: String, info: String? = null) {
            val msg = if (info == null) description else "${description.padEnd(40)}: $info"
            val loggerName = if (renderBasicInfoToConsole) "BasicInfo" else "Main"
            LoggerFactory.getLogger(loggerName).info(msg)
        }

        fun printInRed(message: String) {
            println("${ShellConstants.RED}$message${ShellConstants.RESET}")
        }

        fun printWarning(message: String) {
            Emoji.renderIfSupported {
                printInRed("${Emoji.warningSign} ATTENTION: $message")
            }
            staticLog.warn(message)
        }

        internal fun failStartUp(message: String): Nothing {
            println(message)
            println("Corda will now exit...")
            exitProcess(1)
        }

        private fun createClock(configuration: NodeConfiguration): CordaClock {
            return (if (configuration.useTestClock) ::DemoClock else ::SimpleClock)(Clock.systemUTC())
        }

        private val sameVmNodeCounter = AtomicInteger()

        // TODO: make this configurable.
        const val MAX_RPC_MESSAGE_SIZE = 10485760

        fun isInvalidJavaVersion(): Boolean {
            if (!hasMinimumJavaVersion()) {
                println("You are using a version of Java that is not supported (${SystemUtils.JAVA_VERSION}). Please upgrade to the latest version of Java 8.")
                println("Corda will now exit...")
                return true
            }
            return false
        }

        private fun hasMinimumJavaVersion(): Boolean {
            // when the ext.java8_minUpdateVersion gradle constant changes, so must this check
            return try {
                val update = getJavaUpdateVersion(SystemUtils.JAVA_VERSION) // To filter out cases like 1.8.0_202-ea
                SystemUtils.IS_JAVA_1_8 && update >= 171
            } catch (e: NumberFormatException) { // custom JDKs may not have the update version (e.g. 1.8.0-adoptopenjdk)
                false
            }
        }
    }

    override val log: Logger get() = staticLog
    override val transactionVerifierWorkerCount: Int get() = 4

    private var internalRpcMessagingClient: InternalRPCMessagingClient? = null
    private var rpcBroker: ArtemisBroker? = null

    private var shutdownHook: ShutdownHook? = null

    // DISCUSSION
    //
    // We use a single server thread for now, which means all message handling is serialized.
    //
    // Writing thread safe code is hard. In this project we are writing most node services and code to be thread safe, but
    // the possibility of mistakes is always present. Thus we make a deliberate decision here to trade off some multi-core
    // scalability in order to gain developer productivity by setting the size of the serverThread pool to one, which will
    // reduce the number of threading bugs we will need to tackle.
    //
    // This leaves us with four possibilities in future:
    //
    // (1) We discover that processing messages is fast and that our eventual use cases do not need very high
    //     processing rates. We have benefited from the higher productivity and not lost anything.
    //
    // (2) We discover that we need greater multi-core scalability, but that the bulk of our time goes into particular CPU
    //     hotspots that are easily multi-threaded e.g. signature checking. We successfully multi-thread those hotspots
    //     and find that our software now scales sufficiently well to satisfy our user's needs.
    //
    // (3) We discover that it wasn't enough, but that we only need to run some messages in parallel and that the bulk of
    //     the work can stay single threaded. For example perhaps we find that latency sensitive UI requests must be handled
    //     on a separate thread pool where long blocking operations are not allowed, but that the bulk of the heavy lifting
    //     can stay single threaded. In this case we would need a separate thread pool, but we still minimise the amount of
    //     thread safe code we need to write and test.
    //
    // (4) None of the above are sufficient and we need to run all messages in parallel to get maximum (single machine)
    //     scalability and fully saturate all cores. In that case we can go fully free-threaded, e.g. change the number '1'
    //     below to some multiple of the core count. Alternatively by using the ForkJoinPool and let it figure out the right
    //     number of threads by itself. This will require some investment in stress testing to build confidence that we
    //     haven't made any mistakes, but it will only be necessary if eventual deployment scenarios demand it.
    //
    // Note that the messaging subsystem schedules work onto this thread in a blocking manner. That means if the server
    // thread becomes too slow and a backlog of work starts to builds up it propagates back through into the messaging
    // layer, which can then react to the backpressure. Artemis MQ in particular knows how to do flow control by paging
    // messages to disk rather than letting us run out of RAM.
    //
    // The primary work done by the server thread is execution of flow logics, and related
    // serialisation/deserialisation work.

    override fun makeMessagingService(): MessagingService {
        return P2PMessagingClient(
                config = configuration,
                versionInfo = versionInfo,
                serverAddress = configuration.messagingServerAddress
                        ?: NetworkHostAndPort("localhost", configuration.p2pAddress.port),
                nodeExecutor = serverThread,
                database = database,
                networkMap = networkMapCache,
                isDrainingModeOn = nodeProperties.flowsDrainingMode::isEnabled,
                drainingModeWasChangedEvents = nodeProperties.flowsDrainingMode.values,
                metricRegistry = metricRegistry,
                cacheFactory = cacheFactory
        )
    }

    override fun startMessagingService(rpcOps: RPCOps, nodeInfo: NodeInfo, myNotaryIdentity: PartyAndCertificate?, networkParameters: NetworkParameters) {
        require(nodeInfo.legalIdentities.size in 1..2) { "Currently nodes must have a primary address and optionally one serviced address" }

        network as P2PMessagingClient

        if (System.getProperty("io.netty.allocator.numHeapArenas").isNullOrBlank()) {
            // Netty arenas are approx 16MB each when max'd out.  Set arenas based on memory, not core count, unless memory is abundant.
            val memBasedArenas = max(Runtime.getRuntime().maxMemory() / 256.MB, 1L)
            // We set the min of the above and the default.
            System.setProperty("io.netty.allocator.numHeapArenas", min(memBasedArenas, NettyRuntime.availableProcessors() * 2L).toString())
        }

        // Construct security manager reading users data either from the 'security' config section
        // if present or from rpcUsers list if the former is missing from config.
        val securityManagerConfig = configuration.security?.authService
                ?: SecurityConfiguration.AuthService.fromUsers(configuration.rpcUsers)

        val securityManager = with(RPCSecurityManagerImpl(securityManagerConfig, cacheFactory)) {
            if (configuration.shouldStartLocalShell()) RPCSecurityManagerWithAdditionalUser(this, User(INTERNAL_SHELL_USER, INTERNAL_SHELL_USER, setOf(Permissions.all()))) else this
        }

        val messageBroker = if (!configuration.messagingServerExternal) {
            val brokerBindAddress = configuration.messagingServerAddress
                    ?: NetworkHostAndPort("0.0.0.0", configuration.p2pAddress.port)
            ArtemisMessagingServer(configuration, brokerBindAddress, networkParameters.maxMessageSize)
        } else {
            null
        }

        val rpcServerAddresses = if (configuration.rpcOptions.standAloneBroker) {
            BrokerAddresses(configuration.rpcOptions.address, configuration.rpcOptions.adminAddress)
        } else {
            startLocalRpcBroker(securityManager)
        }

        val bridgeControlListener = makeBridgeControlListener(network.serverAddress, networkParameters)

        printBasicNodeInfo("Advertised P2P messaging addresses", nodeInfo.addresses.joinToString())
        val rpcServerConfiguration = RPCServerConfiguration.DEFAULT
        rpcServerAddresses?.let {
            internalRpcMessagingClient = InternalRPCMessagingClient(configuration.p2pSslOptions, it.admin, MAX_RPC_MESSAGE_SIZE, CordaX500Name.build(configuration.p2pSslOptions.keyStore.get()[X509Utilities.CORDA_CLIENT_TLS].subjectX500Principal), rpcServerConfiguration)
            printBasicNodeInfo("RPC connection address", it.primary.toString())
            printBasicNodeInfo("RPC admin connection address", it.admin.toString())
        }

        // Start up the embedded MQ server
        messageBroker?.apply {
            closeOnStop()
            start()
        }
        rpcBroker?.apply {
            closeOnStop()
            start()
        }
        // Start P2P bridge service
        bridgeControlListener.apply {
            closeOnStop()
            start()
        }
        // Start up the MQ clients.
        internalRpcMessagingClient?.run {
            closeOnStop()
            init(rpcOps, securityManager, cacheFactory)
        }
        network.closeOnStop()
        network.start(
                myIdentity = nodeInfo.legalIdentities[0].owningKey,
                serviceIdentity = if (nodeInfo.legalIdentities.size == 1) null else nodeInfo.legalIdentities[1].owningKey,
                advertisedAddress = nodeInfo.addresses[0],
                maxMessageSize = networkParameters.maxMessageSize
        )
    }

    private fun makeBridgeControlListener(serverAddress: NetworkHostAndPort, networkParameters: NetworkParameters) : BridgeControlListener {
        val artemisMessagingClientFactory = {
            ArtemisMessagingClient(
                    configuration.p2pSslOptions,
                    serverAddress,
                    networkParameters.maxMessageSize,
                    failoverCallback =  { errorAndTerminate("ArtemisMessagingClient failed. Shutting down.", null) }
            )
        }
        return BridgeControlListener(configuration.p2pSslOptions, networkParameters.maxMessageSize, configuration.crlCheckSoftFail, artemisMessagingClientFactory)
    }

    private fun startLocalRpcBroker(securityManager: RPCSecurityManager): BrokerAddresses? {
        return with(configuration) {
            rpcOptions.address.let {
                val rpcBrokerDirectory: Path = baseDirectory / "brokers" / "rpc"
                with(rpcOptions) {
                    rpcBroker = if (useSsl) {
                        ArtemisRpcBroker.withSsl(configuration.p2pSslOptions, this.address, adminAddress, sslConfig!!, securityManager, MAX_RPC_MESSAGE_SIZE, jmxMonitoringHttpPort != null, rpcBrokerDirectory, shouldStartLocalShell())
                    } else {
                        ArtemisRpcBroker.withoutSsl(configuration.p2pSslOptions, this.address, adminAddress, securityManager, MAX_RPC_MESSAGE_SIZE, jmxMonitoringHttpPort != null, rpcBrokerDirectory, shouldStartLocalShell())
                    }
                }
                rpcBroker!!.addresses
            }
        }
    }

    override fun myAddresses(): List<NetworkHostAndPort> = listOf(getAdvertisedAddress()) + configuration.additionalP2PAddresses

    private fun getAdvertisedAddress(): NetworkHostAndPort {
        return with(configuration) {
            require(p2pAddress.host != "0.0.0.0") {
                "Invalid p2pAddress: $p2pAddress contains 0.0.0.0 which is not suitable as an advertised node address"
            }
            val host = if (detectPublicIp) {
                tryDetectIfNotPublicHost(p2pAddress.host) ?: p2pAddress.host
            } else {
                p2pAddress.host
            }
            NetworkHostAndPort(host, p2pAddress.port)
        }
    }

    /**
     * Checks whether the specified [host] is a public IP address or hostname. If not, tries to discover the current
     * machine's public IP address to be used instead by looking through the network interfaces.
     */
    private fun tryDetectIfNotPublicHost(host: String): String? {
        return if (host.toLowerCase() == "localhost") {
            log.warn("p2pAddress specified as localhost. Trying to autodetect a suitable public address to advertise in network map." +
                    "To disable autodetect set detectPublicIp = false in the node.conf, or consider using messagingServerAddress and messagingServerExternal")
            val foundPublicIP = AddressUtils.tryDetectPublicIP()
            if (foundPublicIP == null) {
                try {
                    val retrievedHostName = networkMapClient?.myPublicHostname()
                    if (retrievedHostName != null) {
                        log.info("Retrieved public IP from Network Map Service: $this. This will be used instead of the provided \"$host\" as the advertised address.")
                    }
                    retrievedHostName
                } catch (ignore: Exception) {
                    // Cannot reach the network map service, ignore the exception and use provided P2P address instead.
                    log.warn("Cannot connect to the network map service for public IP detection.")
                    null
                }
            } else {
                log.info("Detected public IP: ${foundPublicIP.hostAddress}. This will be used instead of the provided \"$host\" as the advertised address.")
                foundPublicIP.hostAddress
            }
        } else {
            null
        }
    }

    /**
     * If the node is persisting to an embedded H2 database, then expose this via TCP with a DB URL of the form:
     * jdbc:h2:tcp://<host>:<port>/node
     * with username and password as per the DataSource connection details.  The key element to enabling this support is to
     * ensure that you specify a DB connection URL of the form jdbc:h2:file: in the node config and that you include
     * the H2 option AUTO_SERVER_PORT set to the port you desire to use (0 will give a dynamically allocated port number)
     * but exclude the H2 option AUTO_SERVER=TRUE.
     * This is not using the H2 "automatic mixed mode" directly but leans on many of the underpinnings.  For more details
     * on H2 URLs and configuration see: http://www.h2database.com/html/features.html#database_url
     */
    override fun startDatabase() {
        val databaseUrl = configuration.dataSourceProperties.getProperty("dataSource.url")
        val h2Prefix = "jdbc:h2:file:"

        if (databaseUrl != null && databaseUrl.startsWith(h2Prefix)) {
            val effectiveH2Settings = configuration.effectiveH2Settings
            //forbid execution of arbitrary code via SQL except those classes required by H2 itself
            System.setProperty("h2.allowedClasses", "org.h2.mvstore.db.MVTableEngine,org.locationtech.jts.geom.Geometry,org.h2.server.TcpServer")
            if (effectiveH2Settings?.address != null) {
                if (!InetAddress.getByName(effectiveH2Settings.address.host).isLoopbackAddress
                        && configuration.dataSourceProperties.getProperty("dataSource.password").isBlank()) {
                    throw CouldNotCreateDataSourceException("Database password is required for H2 server listening on ${InetAddress.getByName(effectiveH2Settings.address.host)}.")
                }
                val databaseName = databaseUrl.removePrefix(h2Prefix).substringBefore(';')
                val baseDir = Paths.get(databaseName).parent.toString()
                val server = org.h2.tools.Server.createTcpServer(
                        "-tcpPort", effectiveH2Settings.address.port.toString(),
                        "-tcpAllowOthers",
                        "-tcpDaemon",
                        "-baseDir", baseDir,
                        "-key", "node", databaseName)
                // override interface that createTcpServer listens on (which is always 0.0.0.0)
                System.setProperty("h2.bindAddress", effectiveH2Settings.address.host)
                runOnStop += server::stop
                val url = try {
                    server.start().url
                } catch (e: JdbcSQLNonTransientConnectionException) {
                    if (e.cause is BindException) {
                        throw AddressBindingException(effectiveH2Settings.address)
                    } else {
                        throw e
                    }
                }
                printBasicNodeInfo("Database connection url is", "jdbc:h2:$url/node")
            }
        }

        super.startDatabase()
        database.closeOnStop()
    }

    private val _startupComplete = openFuture<Unit>()
    val startupComplete: CordaFuture<Unit> get() = _startupComplete

    override fun generateAndSaveNodeInfo(): NodeInfo {
        initialiseSerialization()
        return super.generateAndSaveNodeInfo()
    }

    override fun start(): NodeInfo {
        registerDefaultExceptionHandler()
        initialiseSerialization()
        val nodeInfo: NodeInfo = super.start()
        nodeReadyFuture.thenMatch({
            serverThread.execute {

                registerJmxReporter(services.monitoringService.metrics)

                _startupComplete.set(Unit)
            }
        },
                { th -> staticLog.error("Unexpected exception", th) } // XXX: Why not use log?
        )
        shutdownHook = addShutdownHook {
            stop()
        }
        return nodeInfo
    }

    /**
     * Register a default exception handler for all threads that terminates the process if the database connection goes away and
     * cannot be recovered.
     */
    private fun registerDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(DbExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()))
    }

    /**
     * A hook to allow configuration override of the JmxReporter being used.
     */
    fun registerJmxReporter(metrics: MetricRegistry) {
        log.info("Registering JMX reporter:")
        when (configuration.jmxReporterType) {
            JmxReporterType.JOLOKIA -> registerJolokiaReporter(metrics)
            JmxReporterType.NEW_RELIC -> registerNewRelicReporter(metrics)
        }
    }

    private fun registerJolokiaReporter(registry: MetricRegistry) {
        log.info("Registering Jolokia JMX reporter:")
        // Begin exporting our own metrics via JMX. These can be monitored using any agent, e.g. Jolokia:
        //
        // https://jolokia.org/agent/jvm.html
        JmxReporter.forRegistry(registry).inDomain("net.corda").createsObjectNamesWith { _, domain, name ->
            // Make the JMX hierarchy a bit better organised.
            val category = name.substringBefore('.').substringBeforeLast('/')
            val component = name.substringBefore('.').substringAfterLast('/', "")
            val subName = name.substringAfter('.', "")
            (if (subName == "")
                ObjectName("$domain:name=$category${if (component.isNotEmpty()) ",component=$component," else ""}")
            else
                ObjectName("$domain:type=$category,${if (component.isNotEmpty()) "component=$component," else ""}name=$subName"))
        }.build().start()
    }

    private fun registerNewRelicReporter(registry: MetricRegistry) {
        log.info("Registering New Relic JMX Reporter:")
        val reporter = NewRelicReporter.forRegistry(registry)
                .name("New Relic Reporter")
                .filter(MetricFilter.ALL)
                .attributeFilter(AllEnabledMetricAttributeFilter())
                .rateUnit(TimeUnit.SECONDS)
                .durationUnit(TimeUnit.MILLISECONDS)
                .metricNamePrefix("corda/")
                .build()

        reporter.start(1, TimeUnit.MINUTES)
    }

    override val rxIoScheduler: Scheduler get() = Schedulers.io()

    private fun initialiseSerialization() {
        if (!initialiseSerialization) return
        val classloader = cordappLoader.appClassLoader
        nodeSerializationEnv = SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPServerSerializationScheme(cordappLoader.cordapps, Caffeine.newBuilder().maximumSize(128).build<SerializationFactoryCacheKey, SerializerFactory>().asMap()))
                    registerScheme(AMQPClientSerializationScheme(cordappLoader.cordapps, Caffeine.newBuilder().maximumSize(128).build<SerializationFactoryCacheKey, SerializerFactory>().asMap()))
                },
                p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader),
                rpcClientContext = if (configuration.shouldInitCrashShell()) AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader) else null, //even Shell embeded in the node connects via RPC to the node
                storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),

                checkpointSerializer = KryoCheckpointSerializer,
                checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader)
            )
    }

    /** Starts a blocking event loop for message dispatch. */
    fun run() {
        internalRpcMessagingClient?.start(rpcBroker!!.serverControl)
        (network as P2PMessagingClient).run()
    }

    private var shutdown = false

    override fun stop() {
        check(!serverThread.isOnThread)
        synchronized(this) {
            if (shutdown) return
            shutdown = true
            // Unregister shutdown hook to prevent any unnecessary second calls to stop
            shutdownHook?.cancel()
            shutdownHook = null
        }
        printBasicNodeInfo("Shutting down ...")

        // All the Node started subsystems were registered with the runOnStop list at creation.
        // So now simply call the parent to stop everything in reverse order.
        // In particular this prevents premature shutdown of the Database by AbstractNode whilst the serverThread is active
        super.stop()

        shutdown = false

        log.info("Shutdown complete")
    }

    fun <T : FlowLogic<*>> registerInitiatedFlow(smm: StateMachineManager, initiatedFlowClass: Class<T>) {
        this.flowManager.registerInitiatedFlow(initiatedFlowClass)
    }
}
