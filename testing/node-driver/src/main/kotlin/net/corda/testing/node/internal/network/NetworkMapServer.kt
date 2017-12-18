package net.corda.testing.node.internal.network

import net.corda.core.crypto.*
import net.corda.core.internal.cert
import net.corda.core.internal.toX509CertHolder
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.network.DigitalSignatureWithCert
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.ROOT_CA
import org.bouncycastle.asn1.x500.X500Name
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.io.InputStream
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok

class NetworkMapServer(cacheTimeout: Duration,
                       hostAndPort: NetworkHostAndPort,
                       root_ca: CertificateAndKeyPair = ROOT_CA, // Default to ROOT_CA for testing.
                       vararg additionalServices: Any) : Closeable {
    companion object {
        val stubNetworkParameter = NetworkParameters(1, emptyList(), 10485760, 40000, Instant.now(), 10)
        private val serializedParameters = stubNetworkParameter.serialize()

        private fun networkMapKeyAndCert(rootCAKeyAndCert: CertificateAndKeyPair): CertificateAndKeyPair {
            val networkMapKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val networkMapCert = X509Utilities.createCertificate(
                    CertificateType.INTERMEDIATE_CA,
                    rootCAKeyAndCert.certificate,
                    rootCAKeyAndCert.keyPair,
                    X500Name("CN=Corda Network Map,L=London"),
                    networkMapKey.public).cert
            return CertificateAndKeyPair(networkMapCert.toX509CertHolder(), networkMapKey)
        }
    }

    private val server: Server
    private val service = InMemoryNetworkMapService(cacheTimeout, networkMapKeyAndCert(root_ca))

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

    fun removeNodeInfo(nodeInfo: NodeInfo) {
        service.removeNodeInfo(nodeInfo)
    }

    override fun close() {
        server.stop()
    }

    @Path("network-map")
    class InMemoryNetworkMapService(private val cacheTimeout: Duration, private val networkMapKeyAndCert: CertificateAndKeyPair) {
        private val nodeInfoMap = mutableMapOf<SecureHash, SignedNodeInfo>()
        private val parametersHash = serializedParameters.hash
        private val signedParameters = SignedData(
                serializedParameters,
                DigitalSignature.WithKey(networkMapKeyAndCert.keyPair.public, Crypto.doSign(networkMapKeyAndCert.keyPair.private, serializedParameters.bytes))
        )

        @POST
        @Path("publish")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        fun publishNodeInfo(input: InputStream): Response {
            val registrationData = input.readBytes().deserialize<SignedNodeInfo>()
            val nodeInfo = registrationData.verified()
            val nodeInfoHash = nodeInfo.serialize().sha256()
            nodeInfoMap.put(nodeInfoHash, registrationData)
            return ok().build()
        }

        @GET
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkMap(): Response {
            val networkMap = NetworkMap(nodeInfoMap.keys.toList(), parametersHash)
            val serializedNetworkMap = networkMap.serialize()
            val signature = Crypto.doSign(networkMapKeyAndCert.keyPair.private, serializedNetworkMap.bytes)
            val signedNetworkMap = SignedNetworkMap(networkMap.serialize(), DigitalSignatureWithCert(networkMapKeyAndCert.certificate.cert, signature))
            return Response.ok(signedNetworkMap.serialize().bytes).header("Cache-Control", "max-age=${cacheTimeout.seconds}").build()
        }

        // Remove nodeInfo for testing.
        fun removeNodeInfo(nodeInfo: NodeInfo) {
            nodeInfoMap.remove(nodeInfo.serialize().hash)
        }

        @GET
        @Path("node-info/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNodeInfo(@PathParam("var") nodeInfoHash: String): Response {
            val signedNodeInfo = nodeInfoMap[SecureHash.parse(nodeInfoHash)]
            return if (signedNodeInfo != null) {
                Response.ok(signedNodeInfo.serialize().bytes)
            } else {
                Response.status(Response.Status.NOT_FOUND)
            }.build()
        }

        @GET
        @Path("network-parameter/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkParameter(@PathParam("var") networkParameterHash: String): Response {
            return Response.ok(signedParameters.serialize().bytes).build()
        }

        @GET
        @Path("my-hostname")
        fun getHostName(): Response {
            return Response.ok("test.host.name").build()
        }
    }
}
