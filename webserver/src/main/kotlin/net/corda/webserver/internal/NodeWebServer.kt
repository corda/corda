/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver.internal

import com.google.common.html.HtmlEscapers.htmlEscaper
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.contextLogger
import net.corda.webserver.WebServerConfig
import net.corda.webserver.converters.CordaConverterProvider
import net.corda.webserver.services.WebServerPluginRegistry
import net.corda.webserver.servlets.*
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.servlet.ServletContainer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.Writer
import java.lang.reflect.InvocationTargetException
import java.nio.file.NoSuchFileException
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.MediaType

class NodeWebServer(val config: WebServerConfig) {
    private companion object {
        private val log = contextLogger()
        const val retryDelay = 1000L // Milliseconds
    }

    val address = config.webAddress
    private var renderBasicInfoToConsole = true
    private lateinit var server: Server

    fun start() {
        logAndMaybePrint("Starting as webserver: ${config.webAddress}")
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

        // API, data upload and download to services (attachments, rates oracles etc)
        handlerCollection.addHandler(buildServletContextHandler(localRpc))

        val server = Server()

        val connector = if (config.useHTTPS) {
            val httpsConfiguration = HttpConfiguration()
            httpsConfiguration.outputBufferSize = 32768
            httpsConfiguration.addCustomizer(SecureRequestCustomizer())
            val sslContextFactory = SslContextFactory()
            sslContextFactory.keyStorePath = config.sslKeystore.toString()
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

    private fun retryConnectLocalRpc(): CordaRPCOps {
        while (true) {
            try {
                return connectLocalRpcAsNodeUser()
            } catch (e: ActiveMQNotConnectedException) {
                log.debug("Could not connect to ${config.rpcAddress} due to exception: ", e)
                Thread.sleep(retryDelay)
                // This error will happen if the server has yet to create the keystore
                // Keep the fully qualified package name due to collisions with the Kotlin stdlib
                // exception of the same name
            } catch (e: NoSuchFileException) {
                log.debug("Tried to open a file that doesn't yet exist, retrying", e)
                Thread.sleep(retryDelay)
            } catch (e: Throwable) {
                // E.g. a plugin cannot be instantiated?
                // Note that we do want the exception stacktrace.
                log.error("Cannot start WebServer", e)
                throw e
            }
        }
    }

    private fun connectLocalRpcAsNodeUser(): CordaRPCOps {
        log.info("Connecting to node at ${config.rpcAddress} as ${config.runAs}")
        val client = CordaRPCClient(config.rpcAddress)
        val connection = client.start(config.runAs.username, config.runAs.password)
        return connection.proxy
    }

    /** Fetch WebServerPluginRegistry classes registered in META-INF/services/net.corda.webserver.services.WebServerPluginRegistry files that exist in the classpath */
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
