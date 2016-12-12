package net.corda.node.webserver

import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.Node
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.config.NodeSSLConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.servlets.AttachmentDownloadServlet
import net.corda.node.servlets.DataUploadServlet
import net.corda.node.servlets.ResponseFilter
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
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.nio.file.Path
import java.util.*
import javax.servlet.DispatcherType

class WebServer(val nodeInfo: NodeInfo, val configuration: Config) {
    private companion object {
        val log = loggerFor<WebServer>()
    }

    private val address = HostAndPort.fromString(configuration.getString("webAddress"))
    private val sslConfig = object : NodeSSLConfiguration {
        override val keyStorePassword: String
            get() = throw UnsupportedOperationException()
        override val trustStorePassword: String
            get() = throw UnsupportedOperationException()
        override val certificatesPath: Path
            get() = throw UnsupportedOperationException()

    }

    fun start() {
        initWebServer(connectLocalRpcAsNodeUser())
    }

    private fun initWebServer(localRpc: CordaRPCOps): Server {
        // Note that the web server handlers will all run concurrently, and not on the node thread.
        val handlerCollection = HandlerCollection()

        // Export JMX monitoring statistics and data over REST/JSON.
        if (configuration.getString("exportJMXto").split(',').contains("http")) {
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

        val connector = if (configuration.getBoolean("useHTTPS")) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            val sslContextFactory = SslContextFactory()
            sslContextFactory.keyStorePath = sslConfig.keyStorePath.toString()
            sslContextFactory.setKeyStorePassword(sslConfig.keyStorePassword)
            sslContextFactory.setKeyManagerPassword(sslConfig.keyStorePassword)
            sslContextFactory.setTrustStorePath(sslConfig.trustStorePath.toString())
            sslContextFactory.setTrustStorePassword(sslConfig.trustStorePassword)
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
        //runOnStop += Runnable { server.stop() }
        server.start()
        log.info("Embedded web server is listening on", "http://${InetAddress.getLocalHost().hostAddress}:${connector.port}/")
        return server
    }

    private fun buildServletContextHandler(localRpc: CordaRPCOps): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            //setAttribute("node", this@Node)
            addServlet(DataUploadServlet::class.java, "/upload/*")
            addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")

            val resourceConfig = ResourceConfig()
            // Add your API provider classes (annotated for JAX-RS) here
            // TODO: Remove this at cleanup time
            //resourceConfig.register(Config(services))
            resourceConfig.register(ResponseFilter())
            // TODO: Move the API out of node and to here.
            //resourceConfig.register(api)

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

            // Wrap all API calls in a database transaction.
            // TODO: Remove this when cleaning up
            //val filterHolder = FilterHolder(Node.DatabaseTransactionFilter(database))
            //addFilter(filterHolder, "/api/*", EnumSet.of(DispatcherType.REQUEST))
            //addFilter(filterHolder, "/upload/*", EnumSet.of(DispatcherType.REQUEST))
        }
    }

    private fun connectLocalRpcAsNodeUser(): CordaRPCOps {
        val client = CordaRPCClient(HostAndPort.fromString(nodeInfo.address.toString()), sslConfig)
        client.start(ArtemisMessagingComponent.NODE_USER, ArtemisMessagingComponent.NODE_USER)
        return client.proxy()
    }

    /** Fetch CordaPluginRegistry classes registered in META-INF/services/net.corda.core.node.CordaPluginRegistry files that exist in the classpath */
    val pluginRegistries: List<CordaPluginRegistry> by lazy {
        ServiceLoader.load(CordaPluginRegistry::class.java).toList()
    }
}