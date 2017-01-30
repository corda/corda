package net.corda.node.internal

import com.codahale.metrics.JmxReporter
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.div
import net.corda.core.flatMap
import net.corda.core.getOrThrow
import net.corda.core.messaging.RPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.utilities.loggerFor
import net.corda.node.printBasicNodeInfo
import net.corda.node.serialization.NodeClock
import net.corda.node.services.RPCUserService
import net.corda.node.services.RPCUserServiceImpl
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent.NetworkMapAddress
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.node.services.messaging.NodeMessagingClient
import net.corda.node.services.transactions.*
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.Database
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import javax.management.ObjectName
import kotlin.concurrent.thread

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param configuration This is typically loaded from a TypeSafe HOCON configuration file.
 * @param advertisedServices The services this node advertises. This must be a subset of the services it runs,
 * but nodes are not required to advertise services they run (hence subset).
 * @param clock The clock used within the node and by all flows etc.
 */
class Node(override val configuration: FullNodeConfiguration,
           advertisedServices: Set<ServiceInfo>,
           clock: Clock = NodeClock()) : AbstractNode(configuration, advertisedServices, clock) {
    override val log = loggerFor<Node>()
    override val networkMapAddress: NetworkMapAddress? get() = configuration.networkMapService?.address?.let(::NetworkMapAddress)

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

    // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
    // when our process shuts down, but we try in stop() anyway just to be nice.
    private var nodeFileLock: FileLock? = null

    private var shutdownThread: Thread? = null

    private lateinit var userService: RPCUserService

    init {
        checkVersionUnchanged()
    }

    /**
     * Abort starting the node if an existing deployment with a different version is detected in the current directory.
     * The current version is expected to be specified as a system property. If not provided, the check will be ignored.
     */
    private fun checkVersionUnchanged() {
        val currentVersion = System.getProperty("corda.version") ?: return
        val versionFile = Paths.get("version")
        if (Files.exists(versionFile)) {
            val existingVersion = Files.readAllLines(versionFile)[0]
            check(existingVersion == currentVersion) {
                "Version change detected - current: $currentVersion, existing: $existingVersion. Node upgrades are not yet supported."
            }
        } else {
            Files.write(versionFile, currentVersion.toByteArray())
        }
    }

    override fun makeMessagingService(): MessagingServiceInternal {
        userService = RPCUserServiceImpl(configuration)

        val serverAddress = with(configuration) {
            messagingServerAddress ?: {
                messageBroker = ArtemisMessagingServer(this, artemisAddress, rpcAddress, services.networkMapCache, userService)
                artemisAddress
            }()
        }
        val myIdentityOrNullIfNetworkMapService = if (networkMapAddress != null) obtainLegalIdentity().owningKey else null
        return NodeMessagingClient(configuration, serverAddress, myIdentityOrNullIfNetworkMapService, serverThread, database,
                networkMapRegistrationFuture)
    }

    override fun startMessagingService(rpcOps: RPCOps) {
        // Start up the embedded MQ server
        messageBroker?.apply {
            runOnStop += Runnable { stop() }
            start()
        }

        // Start up the MQ client.
        val net = net as NodeMessagingClient
        net.start(rpcOps, userService)
    }

    /**
     * Insert an initial step in the registration process which will throw an exception if a non-recoverable error is
     * encountered when trying to connect to the network map node.
     */
    override fun registerWithNetworkMap(): ListenableFuture<Unit> {
        val networkMapConnection = messageBroker?.networkMapConnectionFuture ?: Futures.immediateFuture(Unit)
        return networkMapConnection.flatMap { super.registerWithNetworkMap() }
    }

    override fun makeUniquenessProvider(type: ServiceType): UniquenessProvider {
        return when (type) {
            RaftValidatingNotaryService.type -> with(configuration) {
                RaftUniquenessProvider(baseDirectory, notaryNodeAddress!!, notaryClusterAddresses, database, configuration)
            }
            BFTValidatingNotaryService.type -> with(configuration) {
                BFTSmartUniquenessProvider(notaryNodeAddress!!, notaryClusterAddresses, database)
            }
            else -> PersistentUniquenessProvider()
        }
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
                val url = server.start().url
                printBasicNodeInfo("Database connection url is", "jdbc:h2:$url/node")
            }
        }
        super.initialiseDatabasePersistence(insideTransaction)
    }

    override fun start(): Node {
        alreadyRunningNodeCheck()
        super.start()
        // Only start the service API requests once the network map registration is complete
        thread(name = "WebServer") {
            networkMapRegistrationFuture.getOrThrow()
            // Begin exporting our own metrics via JMX.
            JmxReporter.
                    forRegistry(services.monitoringService.metrics).
                    inDomain("net.corda").
                    createsObjectNamesWith { type, domain, name ->
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
        }

        shutdownThread = thread(start = false) {
            stop()
        }
        Runtime.getRuntime().addShutdownHook(shutdownThread)

        return this
    }

    /** Starts a blocking event loop for message dispatch. */
    fun run() {
        (net as NodeMessagingClient).run()
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
            if ((shutdownThread != null) && (Thread.currentThread() != shutdownThread)) {
                Runtime.getRuntime().removeShutdownHook(shutdownThread)
                shutdownThread = null
            }
        }
        printBasicNodeInfo("Shutting down ...")

        // All the Node started subsystems were registered with the runOnStop list at creation.
        // So now simply call the parent to stop everything in reverse order.
        // In particular this prevents premature shutdown of the Database by AbstractNode whilst the serverThread is active
        super.stop()

        nodeFileLock!!.release()
        log.info("Shutdown complete")
    }

    private fun alreadyRunningNodeCheck() {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidPath = configuration.baseDirectory / "process-id"
        val file = pidPath.toFile()
        if (!file.exists()) {
            file.createNewFile()
        }
        file.deleteOnExit()
        val f = RandomAccessFile(file, "rw")
        val l = f.channel.tryLock()
        if (l == null) {
            log.error("It appears there is already a node running with the specified data directory ${configuration.baseDirectory}")
            log.error("Shut that other node down and try again. It may have process ID ${file.readText()}")
            System.exit(1)
        }

        nodeFileLock = l
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        f.setLength(0)
        f.write(ourProcessID.toByteArray())
    }
}

class ConfigurationException(message: String) : Exception(message)

data class NetworkMapInfo(val address: HostAndPort, val legalName: String)
