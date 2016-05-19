package node.core

import com.codahale.metrics.JmxReporter
import com.google.common.net.HostAndPort
import core.messaging.MessagingService
import core.node.NodeInfo
import core.node.services.ServiceType
import core.utilities.loggerFor
import node.api.APIServer
import node.services.config.NodeConfiguration
import node.services.messaging.ArtemisMessagingService
import node.servlets.AttachmentDownloadServlet
import node.servlets.Config
import node.servlets.DataUploadServlet
import node.servlets.ResponseFilter
import node.utilities.AffinityExecutor
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
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

class ConfigurationException(message: String) : Exception(message)

// TODO: Split this into a regression testing environment

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param dir A [Path] to a location on disk where working files can be found or stored.
 * @param p2pAddr The host and port that this server will use. It can't find out its own external hostname, so you
 *                have to specify that yourself.
 * @param configuration This is typically loaded from a .properties file
 * @param networkMapAddress An external network map service to use. Should only ever be null when creating the first
 * network map service, while bootstrapping a network.
 * @param advertisedServices The services this node advertises. This must be a subset of the services it runs,
 * but nodes are not required to advertise services they run (hence subset).
 * @param clientAPIs A list of JAX-RS annotated classes to register
 * which will be used to register any extra client web interfaces the node requires for demos to use.
 * Listed clientAPI classes are assumed to have to take a single APIServer constructor parameter
 * @param clock The clock used within the node and by all protocols etc
 */
class Node(dir: Path, val p2pAddr: HostAndPort, configuration: NodeConfiguration,
           networkMapAddress: NodeInfo?, advertisedServices: Set<ServiceType>,
           clock: Clock = Clock.systemUTC(),
           val clientAPIs: List<Class<*>> = listOf()) : AbstractNode(dir, configuration, networkMapAddress, advertisedServices, clock) {
    companion object {
        /** The port that is used by default if none is specified. As you know, 31337 is the most elite number. */
        val DEFAULT_PORT = 31337
    }

    override val log = loggerFor<Node>()

    override val serverThread = AffinityExecutor.ServiceAffinityExecutor("Node thread", 1)

    lateinit var webServer: Server

    // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
    // when our process shuts down, but we try in stop() anyway just to be nice.
    private var nodeFileLock: FileLock? = null

    override fun makeMessagingService(): MessagingService = ArtemisMessagingService(dir, p2pAddr, serverThread)

    override fun startMessagingService() {
        // Start up the MQ service.
        (net as ArtemisMessagingService).start()
    }

    private fun initWebServer(): Server {
        // Note that the web server handlers will all run concurrently, and not on the node thread.

        val port = p2pAddr.port + 1   // TODO: Move this into the node config file.
        val server = Server(port)

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
        handlerCollection.addHandler(ServletContextHandler().apply {
            contextPath = "/"
            setAttribute("node", this@Node)
            addServlet(DataUploadServlet::class.java, "/upload/*")
            addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")

            val resourceConfig = ResourceConfig()
            // Add your API provider classes (annotated for JAX-RS) here
            resourceConfig.register(Config(services))
            resourceConfig.register(ResponseFilter())
            resourceConfig.register(api)

            for(customAPIClass in clientAPIs) {
                val customAPI = customAPIClass.getConstructor(APIServer::class.java).newInstance(api)
                resourceConfig.register(customAPI)
            }


            // Give the app a slightly better name in JMX rather than a randomly generated one and enable JMX
            resourceConfig.addProperties(mapOf(ServerProperties.APPLICATION_NAME to "node.api",
                    ServerProperties.MONITORING_STATISTICS_MBEANS_ENABLED to "true"))

            val container = ServletContainer(resourceConfig)
            val jerseyServlet = ServletHolder(container)
            addServlet(jerseyServlet, "/api/*")
            jerseyServlet.initOrder = 0 // Initialise at server start
        })

        server.handler = handlerCollection
        server.start()
        return server
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
        return this
    }

    override fun stop() {
        webServer.stop()
        super.stop()
        nodeFileLock!!.release()
        serverThread.shutdownNow()
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
            println("It appears there is already a node running with the specified data directory $dir")
            println("Shut that other node down and try again. It may have process ID ${file.readText()}")
            System.exit(1)
        }
        nodeFileLock = l
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        f.setLength(0)
        f.write(ourProcessID.toByteArray())
     }
}
