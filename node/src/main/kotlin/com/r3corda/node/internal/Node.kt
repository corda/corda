package com.r3corda.node.internal

import com.codahale.metrics.JmxReporter
import com.google.common.net.HostAndPort
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.serialization.NodeClock
import com.r3corda.node.services.api.MessagingServiceInternal
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingClient
import com.r3corda.node.services.messaging.ArtemisMessagingServer
import com.r3corda.node.servlets.AttachmentDownloadServlet
import com.r3corda.node.servlets.Config
import com.r3corda.node.servlets.DataUploadServlet
import com.r3corda.node.servlets.ResponseFilter
import com.r3corda.node.utilities.AffinityExecutor
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.servlet.ServletContainer
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.channels.FileLock
import java.nio.file.Path
import java.time.Clock
import javax.management.ObjectName
import kotlin.concurrent.thread

class ConfigurationException(message: String) : Exception(message)

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param dir A [Path] to a location on disk where working files can be found or stored.
 * @param p2pAddr The host and port that this server will use. It can't find out its own external hostname, so you
 *                have to specify that yourself.
 * @param configuration This is typically loaded from a .properties file.
 * @param networkMapAddress An external network map service to use. Should only ever be null when creating the first
 * network map service, while bootstrapping a network.
 * @param advertisedServices The services this node advertises. This must be a subset of the services it runs,
 * but nodes are not required to advertise services they run (hence subset).
 * @param clock The clock used within the node and by all protocols etc.
 * @param messagingServerAddr The address of the Artemis broker instance. If not provided the node will run one locally.
 */
class Node(dir: Path, val p2pAddr: HostAndPort, val webServerAddr: HostAndPort,
           configuration: NodeConfiguration, networkMapAddress: NodeInfo?,
           advertisedServices: Set<ServiceType>, clock: Clock = NodeClock(),
           val messagingServerAddr: HostAndPort? = null) : AbstractNode(dir, configuration, networkMapAddress, advertisedServices, clock) {
    companion object {
        /** The port that is used by default if none is specified. As you know, 31337 is the most elite number. */
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
        val serverAddr = messagingServerAddr ?: {
            messageBroker = ArtemisMessagingServer(dir, configuration, p2pAddr, services.networkMapCache)
            p2pAddr
        }()
        if (networkMapService != null) {
            return ArtemisMessagingClient(dir, configuration, serverAddr, services.storageService.myLegalIdentityKey.public, serverThread)
        } else {
            return ArtemisMessagingClient(dir, configuration, serverAddr, null, serverThread)
        }
    }

    override fun startMessagingService() {
        // Start up the embedded MQ server
        messageBroker?.apply {
            configureWithDevSSLCertificate() // TODO: Create proper certificate provisioning process
            runOnStop += Runnable { messageBroker?.stop() }
            start()
            bridgeToNetworkMapService(networkMapService)
        }

        // Start up the MQ client.
        (net as ArtemisMessagingClient).apply {
            configureWithDevSSLCertificate() // TODO: Client might need a separate certificate
            start()
        }
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

        if (configuration is FullNodeConfiguration && configuration.useHTTPS) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            val sslContextFactory = SslContextFactory()
            val keyStorePath = dir.resolve("certificates").resolve("sslkeystore.jks")
            val trustStorePath = dir.resolve("certificates").resolve("truststore.jks")
            sslContextFactory.setKeyStorePath(keyStorePath.toString())
            sslContextFactory.setKeyStorePassword(configuration.keyStorePassword)
            sslContextFactory.setKeyManagerPassword(configuration.keyStorePassword)
            sslContextFactory.setTrustStorePath(trustStorePath.toString())
            sslContextFactory.setTrustStorePassword(configuration.trustStorePassword)
            sslContextFactory.setExcludeProtocols("SSL.*", "TLSv1", "TLSv1.1")
            sslContextFactory.setIncludeProtocols("TLSv1.2")
            sslContextFactory.setExcludeCipherSuites(".*NULL.*", ".*RC4.*", ".*MD5.*", ".*DES.*", ".*DSS.*")
            sslContextFactory.setIncludeCipherSuites(".*AES.*GCM.*")
            val sslConnector = ServerConnector(server, SslConnectionFactory(sslContextFactory, "http/1.1"), HttpConnectionFactory(httpsConfiguration))
            sslConnector.port = webServerAddr.port
            server.connectors = arrayOf<Connector>(sslConnector)
        } else {
            val httpConfiguration = HttpConfiguration()
            httpConfiguration.outputBufferSize = 32768
            val httpConnector = ServerConnector(server, HttpConnectionFactory(httpConfiguration))
            httpConnector.port = webServerAddr.port
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
        }
    }

    override fun start(): Node {
        alreadyRunningNodeCheck()
        super.start()
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

        shutdownThread = thread(start = false) {
            stop()
        }
        Runtime.getRuntime().addShutdownHook(shutdownThread)

        return this
    }

    /** Starts a blocking event loop for message dispatch. */
    fun run() {
        (net as ArtemisMessagingClient).run()
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
            if(shutdownThread != null) {
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
        val pidPath = dir.resolve("process-id")
        val file = pidPath.toFile()
        if (!file.exists()) {
            file.createNewFile()
        }
        file.deleteOnExit()
        val f = RandomAccessFile(file, "rw")
        val l = f.channel.tryLock()
        if (l == null) {
            log.error("It appears there is already a node running with the specified data directory $dir")
            log.error("Shut that other node down and try again. It may have process ID ${file.readText()}")
            System.exit(1)
        }

        nodeFileLock = l
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        f.setLength(0)
        f.write(ourProcessID.toByteArray())
    }
}
