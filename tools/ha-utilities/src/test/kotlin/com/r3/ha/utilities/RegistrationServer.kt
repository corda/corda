package com.r3.ha.utilities

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.readFully
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * A simple registration web server implementing the "doorman" protocol using [X509Utilities].
 * This server is intended for integration testing only.
 */
class RegistrationServer(hostAndPort: NetworkHostAndPort = NetworkHostAndPort("localhost", 0),
                         vararg additionalServices: Any) : Closeable {

    private val server: Server
    private val service = SimpleDoormanService()

    init {
        server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
            handler = HandlerCollection().apply {
                addHandler(ServletContextHandler().apply {
                    contextPath = "/"
                    val resourceConfig = ResourceConfig().apply {
                        // Add your API provider classes (annotated for JAX-RS) here
                        register(service)
                        additionalServices.forEach { register(it) }
                    }
                    val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 } // Initialise at server start
                    addServlet(jerseyServlet, "/*")
                })
            }
        }
    }

    fun start(): NetworkHostAndPort {
        server.start()
        // Wait until server is up to obtain the host and port.
        while (!server.isStarted) {
            Thread.sleep(500)
        }
        return server.connectors
                .mapNotNull { it as? ServerConnector }
                .first()
                .let { NetworkHostAndPort(it.host, it.localPort) }
    }

    override fun close() {
        server.stop()
    }

    @Path("certificate")
    internal class SimpleDoormanService {
        val csrMap = mutableMapOf<String, JcaPKCS10CertificationRequest>()
        val certificates = mutableMapOf<String, X509Certificate>()

        @POST
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        @Produces(MediaType.TEXT_PLAIN)
        fun submitRequest(input: InputStream): Response {
            val csr = JcaPKCS10CertificationRequest(input.readFully())
            val requestId = SecureHash.randomSHA256().toString()
            csrMap[requestId] = csr
            certificates[requestId] = X509Utilities.createCertificate(CertificateType.NODE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, X500Principal(csr.subject.toString()), csr.publicKey)
            return Response.ok(requestId).build()
        }

        @GET
        @Path("{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun retrieveCert(@PathParam("var") requestId: String): Response {
            val cert = requireNotNull(certificates[requestId])
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zip ->
                val certificates = arrayListOf(cert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate)
                listOf(X509Utilities.CORDA_CLIENT_CA, X509Utilities.CORDA_INTERMEDIATE_CA, X509Utilities.CORDA_ROOT_CA).zip(certificates).forEach {
                    zip.putNextEntry(ZipEntry("${it.first}.cer"))
                    zip.write(it.second.encoded)
                    zip.closeEntry()
                }
            }
            return Response.ok(baos.toByteArray())
                    .type("application/zip")
                    .header("Content-Disposition", "attachment; filename=\"certificates.zip\"").build()
        }
    }
}