/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.webservice

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.doorman.NetworkMapConfig
import com.r3.corda.networkmanage.doorman.webservice.NetworkMapWebService.Companion.NETWORK_MAP_PATH
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sha256
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.NodeInfoAndSigned
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
class NetworkMapWebService(private val nodeInfoStorage: NodeInfoStorage,
                           private val networkMapStorage: NetworkMapStorage,
                           private val config: NetworkMapConfig) {

    companion object {
        val logger = contextLogger()
        const val NETWORK_MAP_PATH = "network-map"
    }

    private val networkMapCache: LoadingCache<Boolean, CachedData> = Caffeine.newBuilder()
            .expireAfterWrite(config.cacheTimeout, TimeUnit.MILLISECONDS)
            .build {
                networkMapStorage.getActiveNetworkMap()?.let {
                    logger.info("Re-publishing network map")
                    val networkMap = it.networkMap
                    val signedNetworkMap = it.toSignedNetworkMap()
                    CachedData(signedNetworkMap, networkMap.nodeInfoHashes.toSet(), it.networkParameters.networkParameters)
                }
            }

    private val nodeInfoCache: LoadingCache<SecureHash, SignedNodeInfo> = Caffeine.newBuilder()
            // TODO: Define cache retention policy.
            .softValues()
            .build(nodeInfoStorage::getNodeInfo)

    private val currentSignedNetworkMap: SignedNetworkMap? get() = networkMapCache.get(true)?.signedNetworkMap
    private val currentNodeInfoHashes: Set<SecureHash> get() = networkMapCache.get(true)?.nodeInfoHashes ?: emptySet()
    private val currentNetworkParameters: NetworkParameters? get() = networkMapCache.get(true)?.networkParameters

    @POST
    @Path("publish")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun registerNode(input: InputStream): Response {
        val signedNodeInfo = input.readBytes().deserialize<SignedNodeInfo>()
        var nodeInfo: NodeInfo? = null
        return try {
            // Store the NodeInfo
            val nodeInfoAndSigned = NodeInfoAndSigned(signedNodeInfo)
            nodeInfo = nodeInfoAndSigned.nodeInfo
            logger.debug { "Publishing node-info: $nodeInfo" }
            verifyNodeInfo(nodeInfo)
            nodeInfoStorage.putNodeInfo(nodeInfoAndSigned)
            ok()
        } catch (e: Exception) {
            logger.warn("Unable to process node-info: $nodeInfo", e)
            when (e) {
                is NetworkMapNotInitialisedException -> status(Response.Status.SERVICE_UNAVAILABLE).entity(e.message)
                is InvalidPlatformVersionException -> status(Response.Status.BAD_REQUEST).entity(e.message)
                is InvalidKeyException, is SignatureException -> status(Response.Status.UNAUTHORIZED).entity(e.message)
                // Rethrow e if its not one of the expected exception, the server will return http 500 internal error.
                else -> throw e
            }
        }.build()
    }

    @POST
    @Path("ack-parameters")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun ackNetworkParameters(input: InputStream): Response {
        return try {
            val signedParametersHash = input.readBytes().deserialize<SignedData<SecureHash>>()
            val hash = signedParametersHash.verified()
            networkMapStorage.getSignedNetworkParameters(hash) ?: throw IllegalArgumentException("No network parameters with hash $hash")
            logger.debug { "Received ack-parameters with $hash from ${signedParametersHash.sig.by}" }
            nodeInfoStorage.ackNodeInfoParametersUpdate(signedParametersHash.sig.by.encoded.sha256(), hash)
            ok()
        } catch (e: SignatureException) {
            status(Response.Status.FORBIDDEN).entity(e.message)
        }.build()
    }

    @GET
    fun getNetworkMap(): Response = createResponse(currentSignedNetworkMap, addCacheTimeout = true)

    @GET
    @Path("node-info/{nodeInfoHash}")
    fun getNodeInfo(@PathParam("nodeInfoHash") nodeInfoHash: String): Response {
        // Only serve node info if its in the current network map, otherwise return 404.
        logger.trace { "Processing node info request for hash: '$nodeInfoHash'" }
        val signedNodeInfo = if (SecureHash.parse(nodeInfoHash) in currentNodeInfoHashes) {
            nodeInfoCache.get(SecureHash.parse(nodeInfoHash))
        } else {
            logger.trace { "Requested node info is not current, returning null." }
            null
        }
        logger.trace { "Node Info: ${signedNodeInfo?.verified()}" }
        return createResponse(signedNodeInfo)
    }

    @GET
    @Path("network-parameters/{hash}")
    fun getNetworkParameters(@PathParam("hash") hash: String): Response {
        val signedNetParams = networkMapStorage.getSignedNetworkParameters(SecureHash.parse(hash))
        logger.trace { "Precessed network parameter request for hash: '$hash'" }
        logger.trace { "Network parameter : ${signedNetParams?.verified()}" }
        return createResponse(signedNetParams)
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        val ip = request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}"
        logger.trace { "Processed IP request from client, IP: '$ip'" }
        return ok(ip).build()
    }

    private fun verifyNodeInfo(nodeInfo: NodeInfo) {
        val minimumPlatformVersion = currentNetworkParameters?.minimumPlatformVersion
                ?: throw NetworkMapNotInitialisedException("Network parameters have not been initialised")
        if (nodeInfo.platformVersion < minimumPlatformVersion) {
            throw InvalidPlatformVersionException("Minimum platform version is $minimumPlatformVersion")
        }
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

    class NetworkMapNotInitialisedException(message: String?) : Exception(message)
    class InvalidPlatformVersionException(message: String?) : Exception(message)

    private data class CachedData(val signedNetworkMap: SignedNetworkMap,
                                  val nodeInfoHashes: Set<SecureHash>,
                                  val networkParameters: NetworkParameters)
}
