package com.r3.corda.networkmanage.doorman.webservice

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.doorman.NetworkMapConfig
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService.Companion.NETWORK_MAP_PATH
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNetworkMap
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
            .build(CacheLoader.from { _ ->
                networkMapStorage.getCurrentNetworkMap()
            })

    @POST
    @Path("publish")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun registerNode(input: InputStream): Response {
        val registrationData = input.readBytes().deserialize<SignedData<NodeInfo>>()
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
    fun getNetworkMap(): Response {
        val currentNetworkMap = networkMapCache.get(true)
        return if (currentNetworkMap != null) {
            Response.ok(currentNetworkMap.serialize().bytes).header("Cache-Control", "max-age=${Duration.ofMillis(config.cacheTimeout).seconds}")
        } else {
            status(Response.Status.NOT_FOUND)
        }.build()
    }

    @GET
    @Path("node-info/{nodeInfoHash}")
    fun getNodeInfo(@PathParam("nodeInfoHash") nodeInfoHash: String): Response {
        val nodeInfo = nodeInfoStorage.getNodeInfo(SecureHash.parse(nodeInfoHash))
        return if (nodeInfo != null) {
            ok(nodeInfo.serialize().bytes)
        } else {
            status(Response.Status.NOT_FOUND)
        }.build()
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        // TODO: Verify this returns IP correctly.
        return ok(request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}").build()
    }
}
