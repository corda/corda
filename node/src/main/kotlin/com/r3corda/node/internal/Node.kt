package com.r3corda.node.internal

import com.codahale.metrics.JmxReporter
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.then
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.api.MessagingServiceInternal
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingServer
import com.r3corda.node.services.messaging.CordaRPCOps
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.transactions.PersistentUniquenessProvider
import com.r3corda.node.servlets.AttachmentDownloadServlet
import com.r3corda.node.servlets.Config
import com.r3corda.node.servlets.DataUploadServlet
import com.r3corda.node.servlets.ResponseFilter
import com.r3corda.node.utilities.AffinityExecutor
import com.r3corda.node.utilities.databaseTransaction
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.servlet.ServletContainer
import org.jetbrains.exposed.sql.Database
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.channels.FileLock
import java.time.Clock
import java.util.*
import javax.management.ObjectName
import javax.servlet.*
import kotlin.concurrent.thread

class ConfigurationException(message: String) : Exception(message)

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param configuration This is typically loaded from a TypeSafe HOCON configuration file.
 * @param networkMapAddress An external network map service to use. Should only ever be null when creating the first
 * network map service, while bootstrapping a network.
 * @param advertisedServices The services this node advertises. This must be a subset of the services it runs,
 * but nodes are not required to advertise services they run (hence subset).
 * @param clock The clock used within the node and by all protocols etc.
 */
class Node(override val configuration: FullNodeConfiguration, networkMapAddress: SingleMessageRecipient?,
           advertisedServices: Set<ServiceInfo>, clock: Clock = NodeClock()) : AbstractNode(configuration, networkMapAddress, advertisedServices, clock) {
    companion object {
        /** The port that is used by default if none is specified. As you know, 31337 is the most elite number. */
        @JvmField
        val DEFAULT_PORT = 31337
    }

    override val log = loggerFor<Node>()

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
    // The primary work done by the server thread is execution of protocol logics, and related
    // serialisation/deserialisation work.
    override val serverThread = AffinityExecutor.ServiceAffinityExecutor("Node thread", 1)

    lateinit var webServer: Server
    var messageBroker: ArtemisMessagingServer? = null

    // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
    // when our process shuts down, but we try in stop() anyway just to be nice.
    private var nodeFileLock: FileLock? = null

    private var shutdownThread: Thread? = null

    override fun makeMessagingService(): MessagingServiceInternal {
        val serverAddr = with(configuration) {
            messagingServerAddress ?: {
                messageBroker = ArtemisMessagingServer(this, artemisAddress, services.networkMapCache)
                artemisAddress
            }()
        }
        val legalIdentity = obtainLegalIdentity()
        val myIdentityOrNullIfNetworkMapService = if (networkMapService != null) legalIdentity.owningKey else null
        return NodeMessagingClient(configuration, serverAddr, myIdentityOrNullIfNetworkMapService, serverThread, database)
    }

    override fun startMessagingService(cordaRPCOps: CordaRPCOps) {
        // Start up the embedded MQ server
        messageBroker?.apply {
            runOnStop += Runnable { messageBroker?.stop() }
            start()
            bridgeToNetworkMapService(networkMapService)
        }

        // Start up the MQ client.
        val net = net as NodeMessagingClient
        net.configureWithDevSSLCertificate() // TODO: Client might need a separate certificate
        net.start(cordaRPCOps)
    }

    private fun initWebServer(): Server {
        // Note that the web server handlers will all run concurrently, and not on the node thread.
        val handlerCollection = HandlerCollection()

        // Export JMX monitoring statistics and data over REST/JSON.
        if (configuration.exportJMXto.split(',').contains("http")) {
            handlerCollection.addHandler(WebAppContext().apply {
                // Find the jolokia WAR file on the classpath.
                contextPath = "/monitoring/json"
                setInitParameter("mimeType", "application/json")
                val classpath = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
                war = classpath.first { it.contains("jolokia-agent-war-2") && it.endsWith(".war") }
            })
        }

        // API, data upload and download to services (attachments, rates oracles etc)
        handlerCollection.addHandler(buildServletContextHandler())

        val server = Server()

        if (configuration.useHTTPS) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            val sslContextFactory = SslContextFactory()
            sslContextFactory.setKeyStorePath(configuration.keyStorePath.toString())
            sslContextFactory.setKeyStorePassword(configuration.keyStorePassword)
            sslContextFactory.setKeyManagerPassword(configuration.keyStorePassword)
            sslContextFactory.setTrustStorePath(configuration.trustStorePath.toString())
            sslContextFactory.setTrustStorePassword(configuration.trustStorePassword)
            sslContextFactory.setExcludeProtocols("SSL.*", "TLSv1", "TLSv1.1")
            sslContextFactory.setIncludeProtocols("TLSv1.2")
            sslContextFactory.setExcludeCipherSuites(".*NULL.*", ".*RC4.*", ".*MD5.*", ".*DES.*", ".*DSS.*")
            sslContextFactory.setIncludeCipherSuites(".*AES.*GCM.*")
            val sslConnector = ServerConnector(server, SslConnectionFactory(sslContextFactory, "http/1.1"), HttpConnectionFactory(httpsConfiguration))
            sslConnector.port = configuration.webAddress.port
            server.connectors = arrayOf<Connector>(sslConnector)
        } else {
            val httpConfiguration = HttpConfiguration()
            httpConfiguration.outputBufferSize = 32768
            val httpConnector = ServerConnector(server, HttpConnectionFactory(httpConfiguration))
            httpConnector.port = configuration.webAddress.port
            server.connectors = arrayOf<Connector>(httpConnector)
        }

        server.handler = handlerCollection
        runOnStop += Runnable { server.stop() }
        server.start()
        return server
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            setAttribute("node", this@Node)
            addServlet(DataUploadServlet::class.java, "/upload/*")
            addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")

            val resourceConfig = ResourceConfig()
            // Add your API provider classes (annotated for JAX-RS) here
            resourceConfig.register(Config(services))
            resourceConfig.register(ResponseFilter())
            resourceConfig.register(api)

            val webAPIsOnClasspath = pluginRegistries.flatMap { x -> x.webApis }
            for (webapi in webAPIsOnClasspath) {
                log.info("Add Plugin web API from attachment ${webapi.name}")
                val customAPI = webapi.getConstructor(ServiceHub::class.java).newInstance(services)
                resourceConfig.register(customAPI)
            }

            val staticDirMaps = pluginRegistries.map { x -> x.staticServeDirs }
            val staticDirs = staticDirMaps.flatMap { it.keys }.zip(staticDirMaps.flatMap { it.values })
            staticDirs.forEach {
                val staticDir = ServletHolder(DefaultServlet::class.java)
                staticDir.setInitParameter("resourceBase", it.second)
                staticDir.setInitParameter("dirAllowed", "true")
                staticDir.setInitParameter("pathInfoOnly", "true")
                addServlet(staticDir, "/web/${it.first}/*")
            }

            // Give the app a slightly better name in JMX rather than a randomly generated one and enable JMX
            resourceConfig.addProperties(mapOf(ServerProperties.APPLICATION_NAME to "node.api",
                    ServerProperties.MONITORING_STATISTICS_MBEANS_ENABLED to "true"))

            val container = ServletContainer(resourceConfig)
            val jerseyServlet = ServletHolder(container)
            addServlet(jerseyServlet, "/api/*")
            jerseyServlet.initOrder = 0 // Initialise at server start

            // Wrap all API calls in a database transaction.
            val filterHolder = FilterHolder(DatabaseTransactionFilter(database))
            addFilter(filterHolder, "/api/*", EnumSet.of(DispatcherType.REQUEST))
            addFilter(filterHolder, "/upload/*", EnumSet.of(DispatcherType.REQUEST))
        }
    }

    override fun makeUniquenessProvider() = PersistentUniquenessProvider()

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
                log.info("H2 JDBC url is jdbc:h2:$url/node")
            }
        }
        super.initialiseDatabasePersistence(insideTransaction)
    }

    override fun start(): Node {
        alreadyRunningNodeCheck()
        super.start()
        // Only start the service API requests once the network map registration is complete
        networkMapRegistrationFuture.then {
            webServer = initWebServer()
            // Begin exporting our own metrics via JMX.
            JmxReporter.
                    forRegistry(services.monitoringService.metrics).
                    inDomain("com.r3cev.corda").
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
        log.info("Shutting down ...")

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
        val pidPath = configuration.basedir.resolve("process-id")
        val file = pidPath.toFile()
        if (!file.exists()) {
            file.createNewFile()
        }
        file.deleteOnExit()
        val f = RandomAccessFile(file, "rw")
        val l = f.channel.tryLock()
        if (l == null) {
            log.error("It appears there is already a node running with the specified data directory ${configuration.basedir}")
            log.error("Shut that other node down and try again. It may have process ID ${file.readText()}")
            System.exit(1)
        }

        nodeFileLock = l
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        f.setLength(0)
        f.write(ourProcessID.toByteArray())
    }

    // Servlet filter to wrap API requests with a database transaction.
    private class DatabaseTransactionFilter(val database: Database) : Filter {
        override fun init(filterConfig: FilterConfig?) {
        }

        override fun destroy() {
        }

        override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
            databaseTransaction(database) {
                chain.doFilter(request, response)
            }
        }
    }
}
