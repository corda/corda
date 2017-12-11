package com.r3.corda.networkmanage.doorman

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.net.InetSocketAddress

/**
 *  NetworkManagementWebServer runs on Jetty server and provides service via http.
 */
class NetworkManagementWebServer(hostAndPort: NetworkHostAndPort, private vararg val webServices: Any) : Closeable {
    companion object {
        val logger = loggerFor<NetworkManagementServer>()
    }

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val hostAndPort: NetworkHostAndPort
        get() = server.connectors.mapNotNull { it as? ServerConnector }
                .map { NetworkHostAndPort(it.host, it.localPort) }
                .first()

    override fun close() {
        logger.info("Shutting down network management web services...")
        server.stop()
        server.join()
    }

    fun start() {
        logger.info("Starting network management web services...")
        server.start()
        logger.info("Network management web services started on $hostAndPort with ${webServices.map { it.javaClass.simpleName }}")
        println("Network management web services started on $hostAndPort with ${webServices.map { it.javaClass.simpleName }}")
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                webServices.forEach { register(it) }
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }// Initialise at server start
            addServlet(jerseyServlet, "/*")
        }
    }
}