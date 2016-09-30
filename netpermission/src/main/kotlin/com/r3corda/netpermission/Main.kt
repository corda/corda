package com.r3corda.netpermission

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.core.utilities.LogHelper
import com.r3corda.core.utilities.loggerFor
import com.r3corda.netpermission.internal.CertificateSigningService
import com.r3corda.netpermission.internal.persistence.InMemoryCertificationRequestStorage
import joptsimple.OptionParser
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 *  CertificateSigningServer runs on Jetty server and provide certificate signing service via http.
 *  The server will require keystorePath, keystore password and key password via command line input.
 *  The Intermediate CA certificate,Intermediate CA private key and Root CA Certificate should use alias name specified in [X509Utilities]
 */
class CertificateSigningServer(val webServerAddr: HostAndPort, val certSigningService: CertificateSigningService) : Closeable {
    companion object {
        val log = loggerFor<CertificateSigningServer>()
        fun Server.hostAndPort(): HostAndPort {
            val connector = server.connectors.first() as ServerConnector
            return HostAndPort.fromParts(connector.host, connector.localPort)
        }
    }

    val server: Server = initWebServer()

    override fun close() {
        log.info("Shutting down CertificateSigningService...")
        server.stop()
    }

    private fun initWebServer(): Server {
        return Server(InetSocketAddress(webServerAddr.hostText, webServerAddr.port)).apply {
            log.info("Starting CertificateSigningService...")
            handler = HandlerCollection().apply {
                addHandler(buildServletContextHandler())
            }
            start()
            log.info("CertificateSigningService started on ${server.hostAndPort()}")
        }
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                register(certSigningService)
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply {
                initOrder = 0  // Initialise at server start
            }
            addServlet(jerseyServlet, "/api/*")
        }
    }
}

object ParamsSpec {
    val parser = OptionParser()
    val host = parser.accepts("host", "The hostname permissioning server will be running on.")
            .withRequiredArg().defaultsTo("localhost")
    val port = parser.accepts("port", "The port number permissioning server will be running on.")
            .withRequiredArg().ofType(Int::class.java).defaultsTo(0)
    val keystorePath = parser.accepts("keystore", "The path to the keyStore containing and root certificate, intermediate CA certificate and private key.")
            .withRequiredArg().required()
    val storePassword = parser.accepts("storePassword", "Keystore's password.")
            .withRequiredArg().required()
    val caKeyPassword = parser.accepts("caKeyPassword", "Intermediate CA private key password.")
            .withRequiredArg().required()
}

fun main(args: Array<String>) {
    LogHelper.setLevel(CertificateSigningServer::class)
    val log = CertificateSigningServer.log
    log.info("Starting certificate signing server.")
    try {
        ParamsSpec.parser.parse(*args)
    } catch (ex: Exception) {
        log.error("Unable to parse args", ex)
        ParamsSpec.parser.printHelpOn(System.out)
        exitProcess(1)
    }.run {
        // Load keystore from input path, default to Dev keystore from jar resource if path not defined.
        val storePassword = valueOf(ParamsSpec.storePassword)
        val keyPassword = valueOf(ParamsSpec.caKeyPassword)
        val keystore = X509Utilities.loadKeyStore(Paths.get(valueOf(ParamsSpec.keystorePath)).normalize(), storePassword)
        val intermediateCACertAndKey = X509Utilities.loadCertificateAndKey(keystore, keyPassword, X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY)
        val rootCA = keystore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY).last()

        // TODO: Create a proper request storage using database or other storage technology.
        val service = CertificateSigningService(intermediateCACertAndKey, rootCA, InMemoryCertificationRequestStorage())

        CertificateSigningServer(HostAndPort.fromParts(valueOf(ParamsSpec.host), valueOf(ParamsSpec.port)), service).use {
            it.server.join()
        }
    }
}