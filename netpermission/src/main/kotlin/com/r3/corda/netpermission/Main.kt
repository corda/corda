package com.r3.corda.netpermission

import com.google.common.net.HostAndPort
import com.r3.corda.netpermission.internal.CertificateSigningService
import com.r3.corda.netpermission.internal.persistence.DBCertificateRequestStorage
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.core.crypto.X509Utilities
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.getProperties
import net.corda.node.utilities.configureDatabase
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
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
import kotlin.concurrent.thread
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
    val basedir: ArgumentAcceptingOptionSpec<String>? = parser.accepts("basedir", "Overriding configuration file path.")
            .withRequiredArg()
}

fun main(args: Array<String>) {
    val log = CertificateSigningServer.log
    log.info("Starting certificate signing server.")
    try {
        ParamsSpec.parser.parse(*args)
    } catch (ex: Exception) {
        log.error("Unable to parse args", ex)
        ParamsSpec.parser.printHelpOn(System.out)
        exitProcess(1)
    }.run {
        val basedir = Paths.get(valueOf(ParamsSpec.basedir) ?: ".")
        val config = ConfigHelper.loadConfig(basedir)

        val keystore = X509Utilities.loadKeyStore(Paths.get(config.getString("keystorePath")).normalize(), config.getString("keyStorePassword"))
        val intermediateCACertAndKey = X509Utilities.loadCertificateAndKey(keystore, config.getString("caKeyPassword"), X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY)
        val rootCA = keystore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY).last()

        // Create DB connection.
        val (datasource, database) = configureDatabase(config.getProperties("dataSourceProperties"))

        val storage = DBCertificateRequestStorage(database)
        val service = CertificateSigningService(intermediateCACertAndKey, rootCA, storage)

        // Background thread approving all request periodically.
        var stopSigner = false
        val certSinger = if (config.getBoolean("approveAll")) {
            thread {
                while (!stopSigner) {
                    Thread.sleep(1000)
                    for (id in storage.pendingRequestIds()) {
                        storage.saveCertificate(id, {
                            JcaPKCS10CertificationRequest(it.request).run {
                                X509Utilities.createServerCert(subject, publicKey, intermediateCACertAndKey,
                                        if (it.ipAddr == it.hostName) listOf() else listOf(it.hostName), listOf(it.ipAddr))
                            }
                        })
                        log.debug { "Approved $id" }
                    }
                }
                log.debug { "Certificate Signer thread stopped." }
            }
        } else {
            null
        }

        CertificateSigningServer(HostAndPort.fromParts(config.getString("host"), config.getInt("port")), service).use {
            Runtime.getRuntime().addShutdownHook(thread(false) {
                stopSigner = true
                certSinger?.join()
                it.close()
                datasource.close()
            })
            it.server.join()
        }
    }
}