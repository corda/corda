package net.corda.testing.node.internal.network

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.readObject
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
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
import java.security.PublicKey
import java.security.SignatureException
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status

class NetworkMapServer(private val pollInterval: Duration,
                       hostAndPort: NetworkHostAndPort,
                       private val networkMapCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa(),
                       private val myHostNameValue: String = "test.host.name",
                       vararg additionalServices: Any) : Closeable {
    companion object {
        private val stubNetworkParameters = NetworkParameters(1, emptyList(), 10485760, Int.MAX_VALUE, Instant.now(), 10, emptyMap())
    }

    private val server: Server
    var networkParameters: NetworkParameters = stubNetworkParameters
    private val service = InMemoryNetworkMapService()
    private var parametersUpdate: ParametersUpdate? = null
    private var nextNetworkParameters: NetworkParameters? = null

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

    fun scheduleParametersUpdate(nextParameters: NetworkParameters, description: String, updateDeadline: Instant) {
        nextNetworkParameters = nextParameters
        parametersUpdate = ParametersUpdate(nextParameters.serialize().hash, description, updateDeadline)
    }

    fun advertiseNewParameters() {
        networkParameters = checkNotNull(nextNetworkParameters) { "Schedule parameters update first" }
        nextNetworkParameters = null
        parametersUpdate = null
    }

    fun latestParametersAccepted(publicKey: PublicKey): SecureHash? {
        return service.latestAcceptedParametersMap[publicKey]
    }

    fun addNodeToPrivateNetwork(signedNodeInfo: SignedNodeInfo, uuid: UUID) {
        service.addToPrivateMap(signedNodeInfo, uuid)
    }

    override fun close() {
        server.stop()
    }

    @Path("network-map")
    inner class InMemoryNetworkMapService {
        // Private maps
        private val uuidInfosMap = mutableMapOf<UUID, MutableSet<SecureHash>>()
        // Nodes not visible in global map
        private val privateNodes = mutableMapOf<SecureHash, SignedNodeInfo>()
        // Global network map
        private val globalMap = mutableMapOf<SecureHash, SignedNodeInfo>()
        val latestAcceptedParametersMap = mutableMapOf<PublicKey, SecureHash>()
        private val signedNetParams by lazy { networkMapCertAndKeyPair.sign(networkParameters) }

        @POST
        @Path("publish")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        fun publishNodeInfo(input: InputStream): Response {
            return try {
                val signedNodeInfo = input.readObject<SignedNodeInfo>()
                signedNodeInfo.verified()
                globalMap[signedNodeInfo.raw.hash] = signedNodeInfo
                ok()
            } catch (e: Exception) {
                when (e) {
                    is SignatureException -> status(Response.Status.FORBIDDEN).entity(e.message)
                    else -> status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.message)
                }
            }.build()
        }

        @POST
        @Path("ack-parameters")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        fun ackNetworkParameters(input: InputStream): Response {
            val signedParametersHash = input.readObject<SignedData<SecureHash>>()
            val hash = signedParametersHash.verified()
            latestAcceptedParametersMap[signedParametersHash.sig.by] = hash
            return ok().build()
        }

        @GET
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getGlobalNetworkMap(): Response {
            val nodeInfoHashes = globalMap.keys.toList()
            return networkMapResponse(nodeInfoHashes)
        }

        @GET
        @Path("private/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getPrivateNetworkMap(@PathParam("var") extraUUID: String): Response {
            val uuid = UUID.fromString(extraUUID)
            val nodeInfoHashes = uuidInfosMap[uuid] ?: return Response.status(Response.Status.NOT_FOUND).build()
            return networkMapResponse(nodeInfoHashes.toList())
        }

        private fun networkMapResponse(nodeInfoHashes: List<SecureHash>): Response {
            val networkMap = NetworkMap(nodeInfoHashes, signedNetParams.raw.hash, parametersUpdate)
            val signedNetworkMap = networkMapCertAndKeyPair.sign(networkMap)
            return Response.ok(signedNetworkMap.serialize().bytes).header("Cache-Control", "max-age=${pollInterval.seconds}").build()
        }

        // Remove nodeInfo for testing.
        fun removeNodeInfo(nodeInfo: NodeInfo) {
            globalMap.remove(nodeInfo.serialize().hash)
        }

        // All nodes that are added through publish go to main network map, this is work-around for testing private maps
        fun addToPrivateMap(signedNodeInfo: SignedNodeInfo, uuid: UUID) {
            val hash = signedNodeInfo.raw.hash
            privateNodes[hash] = signedNodeInfo
            uuidInfosMap.computeIfAbsent(uuid, { mutableSetOf() }).add(hash)
        }

        @GET
        @Path("node-info/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNodeInfo(@PathParam("var") nodeInfoHash: String): Response {
            val hash = SecureHash.parse(nodeInfoHash)
            val signedNodeInfo = globalMap[hash] ?: privateNodes[hash]
            return if (signedNodeInfo != null) {
                Response.ok(signedNodeInfo.serialize().bytes)
            } else {
                Response.status(Response.Status.NOT_FOUND)
            }.build()
        }

        @GET
        @Path("network-parameters/{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun getNetworkParameter(@PathParam("var") hash: String): Response {
            val requestedHash = SecureHash.parse(hash)
            val requestedParameters = if (requestedHash == signedNetParams.raw.hash) {
                signedNetParams
            } else if (requestedHash == nextNetworkParameters?.serialize()?.hash) {
                nextNetworkParameters?.let { networkMapCertAndKeyPair.sign(it) }
            } else {
                null
            }
            requireNotNull(requestedParameters)
            return Response.ok(requestedParameters!!.serialize().bytes).build()
        }

        @GET
        @Path("my-hostname")
        fun getHostName(): Response = Response.ok(myHostNameValue).build()
    }
}