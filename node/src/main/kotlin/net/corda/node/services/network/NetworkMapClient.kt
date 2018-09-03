package net.corda.node.services.network

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.post
import net.corda.core.internal.responseAs
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.core.utilities.trace
import net.corda.node.utilities.registration.cacheControl
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import java.io.BufferedReader
import java.net.URL
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*

class NetworkMapClient(compatibilityZoneURL: URL) {
    companion object {
        private val logger = contextLogger()
    }

    private val networkMapUrl = URL("$compatibilityZoneURL/network-map")
    private lateinit var trustRoot: X509Certificate

    fun start(trustRoot: X509Certificate) {
        this.trustRoot = trustRoot
    }

    fun publish(signedNodeInfo: SignedNodeInfo) {
        val publishURL = URL("$networkMapUrl/publish")
        logger.trace { "Publishing NodeInfo to $publishURL." }
        publishURL.post(signedNodeInfo.serialize())
        logger.trace { "Published NodeInfo to $publishURL successfully." }
    }

    fun ackNetworkParametersUpdate(signedParametersHash: SignedData<SecureHash>) {
        val ackURL = URL("$networkMapUrl/ack-parameters")
        logger.trace { "Sending network parameters with hash ${signedParametersHash.raw.deserialize()} approval to $ackURL." }
        ackURL.post(signedParametersHash.serialize())
        logger.trace { "Sent network parameters approval to $ackURL successfully." }
    }

    fun getNetworkMap(networkMapKey: UUID? = null): NetworkMapResponse {
        val url = networkMapKey?.let { URL("$networkMapUrl/$networkMapKey") } ?: networkMapUrl
        logger.trace { "Fetching network map update from $url." }
        val connection = url.openHttpConnection()
        val signedNetworkMap = connection.responseAs<SignedNetworkMap>()
        val networkMap = signedNetworkMap.verifiedNetworkMapCert(trustRoot)
        val timeout = connection.cacheControl.maxAgeSeconds().seconds
        logger.trace { "Fetched network map update from $url successfully: $networkMap" }
        return NetworkMapResponse(networkMap, timeout)
    }

    fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo {
        val url = URL("$networkMapUrl/node-info/$nodeInfoHash")
        logger.trace { "Fetching node info: '$nodeInfoHash' from $url." }
        val verifiedNodeInfo = url.openHttpConnection().responseAs<SignedNodeInfo>().verified()
        logger.trace { "Fetched node info: '$nodeInfoHash' successfully. Node Info: $verifiedNodeInfo" }
        return verifiedNodeInfo
    }

    fun getNetworkParameters(networkParameterHash: SecureHash): SignedNetworkParameters {
        val url = URL("$networkMapUrl/network-parameters/$networkParameterHash")
        logger.trace { "Fetching network parameters: '$networkParameterHash' from $url." }
        val networkParameter = url.openHttpConnection().responseAs<SignedNetworkParameters>()
        logger.trace { "Fetched network parameters: '$networkParameterHash' successfully. Network Parameters: $networkParameter" }
        return networkParameter
    }

    fun myPublicHostname(): String {
        val url = URL("$networkMapUrl/my-hostname")
        logger.trace { "Resolving public hostname from '$url'." }
        val hostName = url.openHttpConnection().inputStream.bufferedReader().use(BufferedReader::readLine)
        logger.trace { "My public hostname is $hostName." }
        return hostName
    }
}

data class NetworkMapResponse(val payload: NetworkMap, val cacheMaxAge: Duration)
