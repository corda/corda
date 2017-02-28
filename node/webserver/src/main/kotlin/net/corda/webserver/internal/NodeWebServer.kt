package net.corda.webserver.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.utilities.loggerFor
import net.corda.node.printBasicNodeInfo
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.webserver.servlets.AttachmentDownloadServlet
import net.corda.webserver.servlets.DataUploadServlet
import net.corda.webserver.servlets.ObjectMapperConfig
import net.corda.webserver.servlets.ResponseFilter
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
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
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.util.*

class NodeWebServer(val config: FullNodeConfiguration) {
    private companion object {
        val log = loggerFor<NodeWebServer>()
        val retryDelay = 1000L // Milliseconds
    }

    val address = config.webAddress
    private lateinit var server: Server

    fun start() {
        printBasicNodeInfo("Starting as webserver: ${config.webAddress}")
        server = initWebServer(retryConnectLocalRpc())
    }

    fun run() {
        while (server.isRunning) {
            Thread.sleep(100) // TODO: Redesign
        }
    }

    private fun initWebServer(localRpc: CordaRPCOps): Server {
        // Note that the web server handlers will all run concurrently, and not on the node thread.
        val handlerCollection = HandlerCollection()

        // Export JMX monitoring statistics and data over REST/JSON.
        if (config.exportJMXto.split(',').contains("http")) {
            val classpath = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
            val warpath = classpath.firstOrNull { it.contains("jolokia-agent-war-2") && it.endsWith(".war") }
            if (warpath != null) {
                handlerCollection.addHandler(WebAppContext().apply {
                    // Find the jolokia WAR file on the classpath.
                    contextPath = "/monitoring/json"
                    setInitParameter("mimeType", "application/json")
                    war = warpath
                })
            } else {
                log.warn("Unable to locate Jolokia WAR on classpath")
            }
        }

        // API, data upload and download to services (attachments, rates oracles etc)
        handlerCollection.addHandler(buildServletContextHandler(localRpc))

        val server = Server()

        val connector = if (config.useHTTPS) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            val sslContextFactory = SslContextFactory()
            sslContextFactory.keyStorePath = config.keyStoreFile.toString()
            sslContextFactory.setKeyStorePassword(config.keyStorePassword)
            sslContextFactory.setKeyManagerPassword(config.keyStorePassword)
            sslContextFactory.setTrustStorePath(config.trustStoreFile.toString())
            sslContextFactory.setTrustStorePassword(config.trustStorePassword)
            sslContextFactory.setExcludeProtocols("SSL.*", "TLSv1", "TLSv1.1")
            sslContextFactory.setIncludeProtocols("TLSv1.2")
            sslContextFactory.setExcludeCipherSuites(".*NULL.*", ".*RC4.*", ".*MD5.*", ".*DES.*", ".*DSS.*")
            sslContextFactory.setIncludeCipherSuites(".*AES.*GCM.*")
            val sslConnector = ServerConnector(server, SslConnectionFactory(sslContextFactory, "http/1.1"), HttpConnectionFactory(httpsConfiguration))
            sslConnector.port = address.port
            sslConnector
        } else {
            val httpConfiguration = HttpConfiguration()
            httpConfiguration.outputBufferSize = 32768
            val httpConnector = ServerConnector(server, HttpConnectionFactory(httpConfiguration))
            httpConnector.port = address.port
            httpConnector
        }
        server.connectors = arrayOf<Connector>(connector)

        server.handler = handlerCollection
        server.start()
        log.info("Starting webserver on address $address")
        return server
    }

    private fun buildServletContextHandler(localRpc: CordaRPCOps): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            setAttribute("rpc", localRpc)
            addServlet(DataUploadServlet::class.java, "/upload/*")
            addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")

            val resourceConfig = ResourceConfig()
            resourceConfig.register(ObjectMapperConfig(localRpc))
            resourceConfig.register(ResponseFilter())
            resourceConfig.register(APIServerImpl(localRpc))

            val webAPIsOnClasspath = pluginRegistries.flatMap { x -> x.webApis }
            for (webapi in webAPIsOnClasspath) {
                log.info("Add plugin web API from attachment $webapi")
                val customAPI = try {
                    webapi.apply(localRpc)
                } catch (ex: InvocationTargetException) {
                    log.error("Constructor $webapi threw an error: ", ex.targetException)
                    continue
                }
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

    private fun retryConnectLocalRpc(): CordaRPCOps {
        while (true) {
            try {
                return connectLocalRpcAsNodeUser()
            } catch (e: ActiveMQNotConnectedException) {
                log.debug("Could not connect to ${config.messagingAddress} due to exception: ", e)
                Thread.sleep(retryDelay)
                // This error will happen if the server has yet to create the keystore
                // Keep the fully qualified package name due to collisions with the Kotlin stdlib
                // exception of the same name
            } catch (e: java.nio.file.NoSuchFileException) {
                log.debug("Tried to open a file that doesn't yet exist, retrying", e)
                Thread.sleep(retryDelay)
            }
        }
    }

    private fun connectLocalRpcAsNodeUser(): CordaRPCOps {
        log.info("Connecting to node at ${config.messagingAddress} as node user")
        val client = CordaRPCClient(config.messagingAddress, config)
        client.start(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER)
        return client.proxy()
    }

    /** Fetch CordaPluginRegistry classes registered in META-INF/services/net.corda.core.node.CordaPluginRegistry files that exist in the classpath */
    val pluginRegistries: List<CordaPluginRegistry> by lazy {
        ServiceLoader.load(CordaPluginRegistry::class.java).toList()
    }
}
