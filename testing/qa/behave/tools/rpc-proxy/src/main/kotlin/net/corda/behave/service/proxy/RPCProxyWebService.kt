package net.corda.behave.service.proxy

import net.corda.behave.service.proxy.RPCProxyWebService.Companion.RPC_PROXY_PATH
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
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
class RPCProxyWebService(targetHostAndPort: NetworkHostAndPort) {

    // see "NetworkInterface" port allocation definitions
    private val targetPort = targetHostAndPort.port - 1000

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
        private val log = contextLogger()
        const val DEFAULT_PASSWORD = "S0meS3cretW0rd"
        const val RPC_PROXY_PATH = "rpc"
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        return createResponse("HELLO! My ip is ${request.remoteHost}:${request.remotePort}")
    }

    @GET
    @Path("node-info")
    fun nodeInfo(@Context request: HttpServletRequest): Response {
        log.info("nodeInfo")
        return use {
            it.nodeInfo()
        }
    }

    @GET
    @Path("registered-flows")
    fun registeredFlows(@Context request: HttpServletRequest): Response {
        log.info("registeredFlows")
        return use {
            it.registeredFlows()
        }
    }

    @GET
    @Path("notary-identities")
    fun notaryIdentities(@Context request: HttpServletRequest): Response {
        log.info("networkMapSnapshot")
        return use {
            it.notaryIdentities()
        }
    }

    @GET
    @Path("network-map-snapshot")
    fun networkMapSnapshot(@Context request: HttpServletRequest): Response {
        log.info("networkMapSnapshot")
        return use {
            it.networkMapSnapshot()
        }
    }

    @POST
    @Path("parties-from-name")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun partiesFromName(input: InputStream): Response {
        log.info("partiesFromName")
        val queryName =  input.readBytes().deserialize<String>()
        return use {
            it.partiesFromName(queryName, false)
        }
    }

    @POST
    @Path("vault-query")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun vaultQuery(input: InputStream): Response {
        log.info("vaultQuery")
        val contractStateType =  input.readBytes().deserialize<String>()
        val clazz = Class.forName(contractStateType) as Class<ContractState>
        return use {
            log.info("Calling vaultQuery with: $clazz")
            it.vaultQuery(clazz)
        }
    }

    @POST
    @Path("start-flow")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun startFlow(input: InputStream): Response {
        log.info("startFlow")
        return use { rpcClient ->
            val argsList = input.readBytes().deserialize<List<Any>>()
            for (i in argsList.indices) {
                log.info("$i: ${argsList[i]}")
            }
            val flowClass = Class.forName(argsList[0] as String) as Class<FlowLogic<*>>
            val flowArgs = argsList.drop(1).toTypedArray()
            log.info("Calling flow: $flowClass with arguments: ${flowArgs.asList()}")
            rpcClient.startFlowDynamic(flowClass, *flowArgs).returnValue.getOrThrow()
        }
    }

    private fun <T> use(action: (CordaRPCOps) -> T): Response {
        val targetHost = NetworkHostAndPort("localhost", targetPort)
        val config = object : CordaRPCClientConfiguration {
            override val connectionMaxRetryInterval = 10.seconds
        }
        log.info("Establishing RPC connection to ${targetHost.host} on port ${targetHost.port} ...")
        return try {
            CordaRPCClient(targetHost, config).use("corda", DEFAULT_PASSWORD) {
                log.info("RPC connection to ${targetHost.host}:${targetHost.port} established")
                val client = it.proxy
                val result = action(client)
                log.info("CordaRPCOps result: $result")
                return createResponse(result)
            }
        } catch (e: Exception) {
            log.warn("RPC Proxy request failed: ", e)
            e.printStackTrace()
            status(Response.Status.INTERNAL_SERVER_ERROR).encoding(e.message)
        }.build()
    }

    private fun createResponse(payload: Any?): Response {
        return if (payload != null) {
            Response.ok(payload.serialize().bytes)
        } else {
            status(Response.Status.NOT_FOUND)
        }.build()
    }
}
