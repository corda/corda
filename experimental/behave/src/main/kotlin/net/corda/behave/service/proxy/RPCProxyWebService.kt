package net.corda.behave.service.proxy

import net.corda.behave.logging.getLogger
import net.corda.behave.node.Node
import net.corda.behave.node.configuration.Configuration
import net.corda.behave.seconds
import net.corda.behave.service.proxy.RPCProxyWebService.Companion.RPC_PROXY_PATH
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.io.InputStream
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.status

@Path(RPC_PROXY_PATH)
class RPCProxyWebService {

    init {
        try {
            effectiveSerializationEnv
        } catch (e: IllegalStateException) {
            try {
                KryoClientSerializationScheme.initialiseSerialization()
            } catch (e: IllegalStateException) {
                // Race e.g. two of these constructed in parallel, ignore.
            }
        }
    }

    companion object {
        private val log = getLogger<Node>()
        const val RPC_PROXY_PATH = "rpc"
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        return createResponse("HELLO! My ip is ${request.remoteHost}:${request.remotePort}")
//        return ok(request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}").build()
    }

    @GET
    @Path("node-info")
    fun nodeInfo(@Context request: HttpServletRequest): Response {
        val targetHost = NetworkHostAndPort("localhost", 12002)
        val config = CordaRPCClientConfiguration(
                connectionMaxRetryInterval = 10.seconds
        )
        log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
        return try {
            CordaRPCClient(targetHost, config).use("corda", Configuration.DEFAULT_PASSWORD) {
                log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                val client = it.proxy
                val nodeInfo = client.nodeInfo()
                println("NodeInfo: $nodeInfo")
                return createResponse(nodeInfo)
            }
        } catch (e: Exception) {
            log.warn("nodeInfo failed: ", e)
            e.printStackTrace()
            status(Response.Status.NOT_FOUND)
        }.build()
    }

    @GET
    @Path("registered-flows")
    fun registeredFlows(@Context request: HttpServletRequest): Response {

        val targetHost = NetworkHostAndPort("localhost", 12002)
        val config = CordaRPCClientConfiguration(
                connectionMaxRetryInterval = 10.seconds
        )
        log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
        return try {
            CordaRPCClient(targetHost, config).use("corda", Configuration.DEFAULT_PASSWORD) {
                log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                val client = it.proxy
                val result = client.registeredFlows()
                println("RegisteredFlows: $result")
                return createResponse(result)
            }
        } catch (e: Exception) {
            log.warn("RPC Proxy request failed: ", e)
            e.printStackTrace()
            status(Response.Status.NOT_FOUND)
        }.build()
    }

    @GET
    @Path("notary-identities")
    fun notaryIdentities(@Context request: HttpServletRequest): Response {

        val targetHost = NetworkHostAndPort("localhost", 12002)
        val config = CordaRPCClientConfiguration(
                connectionMaxRetryInterval = 10.seconds
        )
        log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
        return try {
            CordaRPCClient(targetHost, config).use("corda", Configuration.DEFAULT_PASSWORD) {
                log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                val client = it.proxy
                val nodeInfos = client.networkMapSnapshot()
                println("NMS nodeInfos: $nodeInfos")

                val entityParties = client.partiesFromName("Entity", false)
                println("Parties: $entityParties")

                val result = client.notaryIdentities()
                println("Notary Identities: $result")
                return createResponse(result)
            }
        } catch (e: Exception) {
            log.warn("RPC Proxy request failed: ", e)
            e.printStackTrace()
            status(Response.Status.NOT_FOUND)
        }.build()
    }

    @POST
    @Path("start-flow")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun startFlow(input: InputStream): Response {
        log.info("startFlow")
        try {
            val targetHost = NetworkHostAndPort("localhost", 12002)
            val config = CordaRPCClientConfiguration(connectionMaxRetryInterval = 10.seconds)
            log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
            return try {
                CordaRPCClient(targetHost, config).use("corda", Configuration.DEFAULT_PASSWORD) {
                    log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                    val client = it.proxy
                    val notary = client.notaryIdentities()[0]
                    val amount = POUNDS(1000)
                    val ref = OpaqueBytes.of(1)
                    println("Issuing")
                    client.startFlow(::CashIssueFlow, amount, ref, notary).returnValue.getOrThrow().stx
                    println("Transferring")
                    val x500Name = CordaX500Name("EntityB", "London", "GB")
                    val sendToParty = client.wellKnownPartyFromX500Name(x500Name)
                    val response = client.startFlow(::CashPaymentFlow, amount, sendToParty!!).returnValue.getOrThrow().stx
                    println("Response: $response")
                    return createResponse(response)
                }
            } catch (e: Exception) {
                println("startFlow failed: ${e.message}")
                log.warn("startFlow failed: ", e)
                e.printStackTrace()
                status(Response.Status.NOT_FOUND)
            }.build()
        }
        catch (e: RuntimeException) {
            println("RuntimeException ${e.message}")
            e.printStackTrace()
            return status(Response.Status.NOT_FOUND).build()
        }
    }

    private fun createResponse(payload: Any?): Response {
        return if (payload != null) {
            Response.ok(payload.serialize().bytes)
        } else {
            status(Response.Status.NOT_FOUND)
        }.build()
    }
}
