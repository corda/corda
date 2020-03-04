package net.corda.webserver.internal

import com.google.common.html.HtmlEscapers.htmlEscaper
import io.netty.channel.unix.Errors
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.GracefulReconnect
import net.corda.client.rpc.RPCException
import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.contextLogger
import net.corda.webserver.WebServerConfig
import net.corda.webserver.converters.CordaConverterProvider
import net.corda.webserver.services.WebServerPluginRegistry
import net.corda.webserver.servlets.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.servlet.ServletContainer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.Writer
import java.lang.reflect.InvocationTargetException
import java.net.BindException
import java.util.*
import javax.servlet.http.HttpServletRequest

class NodeWebServer(val config: WebServerConfig) {
    private companion object {
        private val log = contextLogger()
        private const val NODE_CONNECT_RETRY_COUNT = 30
        private const val NODE_CONNECT_WAIT_BETWEEN_RETRYS = 2000L
    }

    val address = config.webAddress
    private var renderBasicInfoToConsole = true
    private lateinit var server: Server

    fun start() {
        logAndMaybePrint("Starting as webserver: ${config.webAddress}")
        server = initWebServer(reconnectingCordaRPCOps())
    }

    fun run() {
        try {
            while (server.isRunning) {
                Thread.sleep(100) // TODO: Redesign
            }
        } finally {
            rpc.close()
        }
    }

    private fun initWebServer(localRpc: CordaRPCOps): Server {
        // Note that the web server handlers will all run concurrently, and not on the node thread.
        val handlerCollection = HandlerCollection()

        // API, data upload and download to services (attachments, rates oracles etc)
        handlerCollection.addHandler(buildServletContextHandler(localRpc))

        val server = Server()

        val connector = if (config.useHTTPS) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            @Suppress("DEPRECATION")
            val sslContextFactory = SslContextFactory()
            sslContextFactory.keyStorePath = config.keyStorePath
            sslContextFactory.setKeyStorePassword(config.keyStorePassword)
            sslContextFactory.setKeyManagerPassword(config.keyStorePassword)
            sslContextFactory.setTrustStorePath(config.trustStorePath)
            sslContextFactory.setTrustStorePassword(config.trustStorePassword)
            sslContextFactory.setExcludeProtocols("SSL.*", "TLSv1", "TLSv1.1")
            sslContextFactory.setIncludeProtocols("TLSv1.2")
            sslContextFactory.setExcludeCipherSuites(".*NULL.*", ".*RC4.*", ".*MD5.*", ".*DES.*", ".*DSS.*")
            sslContextFactory.setIncludeCipherSuites(".*AES.*GCM.*")
            val sslConnector = ServerConnector(
                    server,
                    SslConnectionFactory(sslContextFactory, "http/1.1"),
                    HttpConnectionFactory(httpsConfiguration)
            )
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
        try {
            server.start()
        } catch (e: IOException) {
            if (e is BindException || e is Errors.NativeIoException && e.message?.contains("Address already in use") == true) {
                throw AddressBindingException(address)
            } else {
                throw e
            }
        }
        log.info("Starting webserver on address $address")
        return server
    }

    private fun buildServletContextHandler(localRpc: CordaRPCOps): ServletContextHandler {
        val safeLegalName = htmlEscaper().escape(config.myLegalName)
        return ServletContextHandler().apply {
            contextPath = "/"
            errorHandler = object : ErrorHandler() {
                @Throws(IOException::class)
                override fun writeErrorPageHead(request: HttpServletRequest, writer: Writer, code: Int, message: String) {
                    writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\"/>\n")
                    writer.write("<title>Corda $safeLegalName : Error $code</title>\n")
                }

                @Throws(IOException::class)
                override fun writeErrorPageMessage(request: HttpServletRequest, writer: Writer, code: Int, message: String, uri: String) {
                    writer.write("<h1>Corda $safeLegalName</h1>\n")
                    super.writeErrorPageMessage(request, writer, code, message, uri)
                }
            }
            setAttribute("rpc", localRpc)
            addServlet(DataUploadServlet::class.java, "/upload/*")
            addServlet(AttachmentDownloadServlet::class.java, "/attachments/*")

            val rpcObjectMapper = pluginRegistries.fold(JacksonSupport.createDefaultMapper(localRpc)) { om, plugin ->
                plugin.customizeJSONSerialization(om)
                om
            }

            val resourceConfig = ResourceConfig()
                    .register(ObjectMapperConfig(rpcObjectMapper))
                    .register(ResponseFilter())
                    .register(CordaConverterProvider)
                    .register(APIServerImpl(localRpc))

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

            val filteredPlugins = pluginRegistries.filterNot {
                it.javaClass.name.startsWith("net.corda.node.") ||
                        it.javaClass.name.startsWith("net.corda.core.") ||
                        it.javaClass.name.startsWith("net.corda.nodeapi.")
            }

            val infoServlet = ServletHolder(CorDappInfoServlet(filteredPlugins, localRpc))
            addServlet(infoServlet, "")

            val container = ServletContainer(resourceConfig)
            val jerseyServlet = ServletHolder(container)
            addServlet(jerseyServlet, "/api/*")
            jerseyServlet.initOrder = 0 // Initialise at server start
        }
    }

    private lateinit var rpc: CordaRPCConnection
    private fun reconnectingCordaRPCOps(): CordaRPCOps {
        var retryCount = NODE_CONNECT_RETRY_COUNT
        while (true) {
            try {
                rpc = CordaRPCClient(config.rpcAddress, null, javaClass.classLoader)
                        .start(
                                config.runAs.username,
                                config.runAs.password,
                                GracefulReconnect()
                        )
                return rpc.proxy
            }
            catch (ex: RPCException) {
                if (retryCount-- == 0) {
                    throw ex
                }
                else {
                    Thread.sleep(NODE_CONNECT_WAIT_BETWEEN_RETRYS)
                }
            }
        }
    }

    /**
     *  Fetch WebServerPluginRegistry classes registered in META-INF/services/net.corda.webserver.services.WebServerPluginRegistry
     *  files that exist in the classpath
     */
    val pluginRegistries: List<WebServerPluginRegistry> by lazy {
        ServiceLoader.load(WebServerPluginRegistry::class.java).toList()
    }

    /** Used for useful info that we always want to show, even when not logging to the console */
    fun logAndMaybePrint(description: String, info: String? = null) {
        val msg = if (info == null) description else "${description.padEnd(40)}: $info"
        val loggerName = if (renderBasicInfoToConsole) "BasicInfo" else "Main"
        LoggerFactory.getLogger(loggerName).info(msg)
    }
}
