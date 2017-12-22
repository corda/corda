package com.r3.corda.networkmanage.doorman.webservice

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.doorman.NetworkMapConfig
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService.Companion.NETWORK_MAP_PATH
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.SignedNetworkMap
import java.io.InputStream
import java.security.InvalidKeyException
import java.security.SignatureException
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status

@Path(NETWORK_MAP_PATH)
class NodeInfoWebService(private val nodeInfoStorage: NodeInfoStorage,
                         private val networkMapStorage: NetworkMapStorage,
                         private val config: NetworkMapConfig) {
    companion object {
        const val NETWORK_MAP_PATH = "network-map"
    }

    private val networkMapCache: LoadingCache<Boolean, SignedNetworkMap?> = CacheBuilder.newBuilder()
            .expireAfterWrite(config.cacheTimeout, TimeUnit.MILLISECONDS)
            .build(CacheLoader.from { _ -> networkMapStorage.getCurrentNetworkMap() })

    @POST
    @Path("publish")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun registerNode(input: InputStream): Response {
        val registrationData = input.readBytes().deserialize<SignedNodeInfo>()
        return try {
            // Store the NodeInfo
            nodeInfoStorage.putNodeInfo(registrationData)
            ok()
        } catch (e: Exception) {
            // Catch exceptions thrown by signature verification.
            when (e) {
                is IllegalArgumentException, is InvalidKeyException, is SignatureException -> status(Response.Status.UNAUTHORIZED).entity(e.message)
                // Rethrow e if its not one of the expected exception, the server will return http 500 internal error.
                else -> throw e
            }
        }.build()
    }

    @GET
    fun getNetworkMap(): Response = createResponse(networkMapCache.get(true), addCacheTimeout = true)

    @GET
    @Path("node-info/{nodeInfoHash}")
    fun getNodeInfo(@PathParam("nodeInfoHash") nodeInfoHash: String): Response {
        val signedNodeInfo = nodeInfoStorage.getNodeInfo(SecureHash.parse(nodeInfoHash))
        return createResponse(signedNodeInfo)
    }

    @GET
    @Path("network-parameter/{netParamsHash}") // TODO Fix path to be /network-parameters
    fun getNetworkParameters(@PathParam("netParamsHash") netParamsHash: String): Response {
        val signedNetParams = networkMapStorage.getSignedNetworkParameters(SecureHash.parse(netParamsHash))
        return createResponse(signedNetParams)
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        // TODO: Verify this returns IP correctly.
        return ok(request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}").build()
    }

    private fun createResponse(payload: Any?, addCacheTimeout: Boolean = false): Response {
        return if (payload != null) {
            val ok = Response.ok(payload.serialize().bytes)
            if (addCacheTimeout) {
                ok.header("Cache-Control", "max-age=${Duration.ofMillis(config.cacheTimeout).seconds}")
            }
            ok
        } else {
            status(Response.Status.NOT_FOUND)
        }.build()
    }
}
