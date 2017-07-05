package net.corda.node.internal

import com.codahale.metrics.JmxReporter
import net.corda.core.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.concurrent.doneFuture
import net.corda.core.concurrent.openFuture
import net.corda.core.messaging.RPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.parseNetworkHostAndPort
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.node.serialization.NodeClock
import net.corda.node.services.RPCUserService
import net.corda.node.services.RPCUserServiceImpl
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.ArtemisMessagingServer.Companion.ipDetectRequestProperty
import net.corda.node.services.messaging.ArtemisMessagingServer.Companion.ipDetectResponseProperty
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.utilities.AddressUtils
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.IP_REQUEST_PREFIX
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.ArtemisMessagingComponent.NetworkMapAddress
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.ShutdownHook
import net.corda.nodeapi.internal.addShutdownHook
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.util.*
import javax.management.ObjectName
import kotlin.system.exitProcess

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param configuration This is typically loaded from a TypeSafe HOCON configuration file.
 * @param advertisedServices The services this node advertises. This must be a subset of the services it runs,
 * but nodes are not required to advertise services they run (hence subset).
 * @param clock The clock used within the node and by all flows etc.
 */
open class Node(override val configuration: FullNodeConfiguration,
                advertisedServices: Set<ServiceInfo>,
                val versionInfo: VersionInfo,
                clock: Clock = NodeClock()) : AbstractNode(configuration, advertisedServices, clock) {
    companion object {
        private val logger = loggerFor<Node>()
        var renderBasicInfoToConsole = true

        /** Used for useful info that we always want to show, even when not logging to the console */
        fun printBasicNodeInfo(description: String, info: String? = null) {
            val msg = if (info == null) description else "${description.padEnd(40)}: $info"
            val loggerName = if (renderBasicInfoToConsole) "BasicInfo" else "Main"
            LoggerFactory.getLogger(loggerName).info(msg)
        }

        internal fun failStartUp(message: String): Nothing {
            println(message)
            println("Corda will now exit...")
            exitProcess(1)
        }
    }

    override val log: Logger get() = logger
    override val platformVersion: Int get() = versionInfo.platformVersion
    override val networkMapAddress: NetworkMapAddress? get() = configuration.networkMapService?.address?.let(::NetworkMapAddress)
    override fun makeTransactionVerifierService() = (network as NodeMessagingClient).verifierService

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
    override val serverThread = AffinityExecutor.ServiceAffinityExecutor("Node thread", 1)

    var messageBroker: ArtemisMessagingServer? = null

    private var shutdownHook: ShutdownHook? = null

    private lateinit var userService: RPCUserService

    override fun makeMessagingService(): MessagingService {
        userService = RPCUserServiceImpl(configuration.rpcUsers)

        val (serverAddress, advertisedAddress) = with(configuration) {
            if (messagingServerAddress != null) {
                // External broker
                messagingServerAddress to messagingServerAddress
            } else {
                makeLocalMessageBroker() to getAdvertisedAddress()
            }
        }

        printBasicNodeInfo("Incoming connection address", advertisedAddress.toString())

        val myIdentityOrNullIfNetworkMapService = if (networkMapAddress != null) obtainLegalIdentity().owningKey else null
        return NodeMessagingClient(
                configuration,
                versionInfo,
                serverAddress,
                myIdentityOrNullIfNetworkMapService,
                serverThread,
                database,
                networkMapRegistrationFuture,
                services.monitoringService,
                advertisedAddress)
    }

    private fun makeLocalMessageBroker(): NetworkHostAndPort {
        with(configuration) {
            messageBroker = ArtemisMessagingServer(this, p2pAddress.port, rpcAddress?.port, services.networkMapCache, userService)
            return NetworkHostAndPort("localhost", p2pAddress.port)
        }
    }

    private fun getAdvertisedAddress(): NetworkHostAndPort {
        return with(configuration) {
            val useHost = if (detectPublicIp) {
                tryDetectIfNotPublicHost(p2pAddress.host) ?: p2pAddress.host
            } else {
                p2pAddress.host
            }
            NetworkHostAndPort(useHost, p2pAddress.port)
        }
    }

    /**
     * Checks whether the specified [host] is a public IP address or hostname. If not, tries to discover the current
     * machine's public IP address to be used instead. It first looks through the network interfaces, and if no public IP
     * is found, asks the network map service to provide it.
     */
    private fun tryDetectIfNotPublicHost(host: String): String? {
        if (!AddressUtils.isPublic(host)) {
            val foundPublicIP = AddressUtils.tryDetectPublicIP()

            if (foundPublicIP == null) {
                networkMapAddress?.let { return discoverPublicHost(it.hostAndPort) }
            } else {
                log.info("Detected public IP: ${foundPublicIP.hostAddress}. This will be used instead of the provided \"$host\" as the advertised address.")
                return foundPublicIP.hostAddress
            }
        }
        return null
    }

    /**
     * Asks the network map service to provide this node's public IP address:
     * - Connects to the network map service's message broker and creates a special IP request queue with a custom
     * request id. Marks the established session with the same request id.
     * - On the server side a special post-queue-creation callback is fired. It finds the session matching the request id
     * encoded in the queue name. It then extracts the remote IP from the session details and posts a message containing
     * it back to the queue.
     * - Once the message is received the session is closed and the queue deleted.
     */
    private fun discoverPublicHost(serverAddress: NetworkHostAndPort): String? {
        log.trace { "Trying to detect public hostname through the Network Map Service at $serverAddress" }
        val tcpTransport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), serverAddress, configuration)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            initialConnectAttempts = 5
            retryInterval = 5.seconds.toMillis()
            retryIntervalMultiplier = 1.5
            maxRetryInterval = 3.minutes.toMillis()
        }
        val clientFactory = try {
            locator.createSessionFactory()
        } catch (e: ActiveMQNotConnectedException) {
            throw IOException("Unable to connect to the Network Map Service at $serverAddress for IP address discovery", e)
        }

        val session = clientFactory.createSession(PEER_USER, PEER_USER, false, true, true, locator.isPreAcknowledge, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
        val requestId = UUID.randomUUID().toString()
        session.addMetaData(ipDetectRequestProperty, requestId)
        session.start()

        val queueName = "$IP_REQUEST_PREFIX$requestId"
        session.createQueue(queueName, RoutingType.MULTICAST, queueName, false)

        val consumer = session.createConsumer(queueName)
        val artemisMessage: ClientMessage = consumer.receive(10.seconds.toMillis()) ?:
                throw IOException("Did not receive a response from the Network Map Service at $serverAddress")
        val publicHostAndPort = artemisMessage.getStringProperty(ipDetectResponseProperty)
        log.info("Detected public address: $publicHostAndPort")

        consumer.close()
        session.deleteQueue(queueName)
        clientFactory.close()

        return publicHostAndPort.removePrefix("/").parseNetworkHostAndPort().host
    }

    override fun startMessagingService(rpcOps: RPCOps) {
        // Start up the embedded MQ server
        messageBroker?.apply {
            runOnStop += this::stop
            start()
        }

        // Start up the MQ client.
        (network as NodeMessagingClient).start(rpcOps, userService)
    }

    /**
     * Insert an initial step in the registration process which will throw an exception if a non-recoverable error is
     * encountered when trying to connect to the network map node.
     */
    override fun registerWithNetworkMap(): CordaFuture<Unit> {
        val networkMapConnection = messageBroker?.networkMapConnectionFuture ?: doneFuture(Unit)
        return networkMapConnection.flatMap { super.registerWithNetworkMap() }
    }

    override fun myAddresses(): List<NetworkHostAndPort> {
        val address = network.myAddress as ArtemisMessagingComponent.ArtemisPeerAddress
        return listOf(address.hostAndPort)
    }

    /**
     * If the node is persisting to an embedded H2 database, then expose this via TCP with a JDBC URL of the form:
     * jdbc:h2:tcp://<host>:<port>/node
     * with username and password as per the DataSource connection details.  The key element to enabling this support is to
     * ensure that you specify a JDBC connection URL of the form jdbc:h2:file: in the node config and that you include
     * the H2 option AUTO_SERVER_PORT set to the port you desire to use (0 will give a dynamically allocated port number)
     * but exclude the H2 option AUTO_SERVER=TRUE.
     * This is not using the H2 "automatic mixed mode" directly but leans on many of the underpinnings.  For more details
     * on H2 URLs and configuration see: http://www.h2database.com/html/features.html#database_url
     */
    override fun initialiseDatabasePersistence(insideTransaction: () -> Unit) {
        val databaseUrl = configuration.dataSourceProperties.getProperty("dataSource.url")
        val h2Prefix = "jdbc:h2:file:"
        if (databaseUrl != null && databaseUrl.startsWith(h2Prefix)) {
            val h2Port = databaseUrl.substringAfter(";AUTO_SERVER_PORT=", "").substringBefore(';')
            if (h2Port.isNotBlank()) {
                val databaseName = databaseUrl.removePrefix(h2Prefix).substringBefore(';')
                val server = org.h2.tools.Server.createTcpServer(
                        "-tcpPort", h2Port,
                        "-tcpAllowOthers",
                        "-tcpDaemon",
                        "-key", "node", databaseName)
                runOnStop += server::stop
                val url = server.start().url
                printBasicNodeInfo("Database connection url is", "jdbc:h2:$url/node")
            }
        }
        super.initialiseDatabasePersistence(insideTransaction)
    }

    private val _startupComplete = openFuture<Unit>()
    val startupComplete: CordaFuture<Unit> get() = _startupComplete

    override fun start(): Node {
        super.start()

        networkMapRegistrationFuture.thenMatch({
            serverThread.execute {
                // Begin exporting our own metrics via JMX. These can be monitored using any agent, e.g. Jolokia:
                //
                // https://jolokia.org/agent/jvm.html
                JmxReporter.
                        forRegistry(services.monitoringService.metrics).
                        inDomain("net.corda").
                        createsObjectNamesWith { _, domain, name ->
                            // Make the JMX hierarchy a bit better organised.
                            val category = name.substringBefore('.')
                            val subName = name.substringAfter('.', "")
                            if (subName == "")
                                ObjectName("$domain:name=$category")
                            else
                                ObjectName("$domain:type=$category,name=$subName")
                        }.
                        build().
                        start()

                _startupComplete.set(Unit)
            }
        }, {})
        shutdownHook = addShutdownHook {
            stop()
        }
        return this
    }

    /** Starts a blocking event loop for message dispatch. */
    fun run() {
        (network as NodeMessagingClient).run(messageBroker!!.serverControl)
    }

    // TODO: Do we really need setup?
    override fun setup(): Node {
        super.setup()
        return this
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

        log.info("Shutdown complete")
    }
}

class ConfigurationException(message: String) : Exception(message)

data class NetworkMapInfo(val address: NetworkHostAndPort, val legalName: X500Name)
