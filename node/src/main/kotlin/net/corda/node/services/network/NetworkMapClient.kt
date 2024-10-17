package net.corda.node.services.network

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sha256
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.post
import net.corda.core.internal.responseAs
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.node.utilities.registration.cacheControl
import net.corda.node.utilities.registration.cordaServerVersion
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

class NetworkMapClient(compatibilityZoneURL: URL, private val versionInfo: VersionInfo) {
    companion object {
        private val logger = contextLogger()
    }

    private val networkMapUrl = URL("$compatibilityZoneURL/network-map")
    private lateinit var trustRoots: Set<X509Certificate>

    fun start(trustRoots: Set<X509Certificate>) {
        this.trustRoots = trustRoots
    }

    fun publish(signedNodeInfo: SignedNodeInfo) {
        val publishURL = URL("$networkMapUrl/publish")
        logger.trace { "Publishing NodeInfo to $publishURL." }
        publishURL.post(signedNodeInfo.serialize(),
                "Platform-Version" to "${versionInfo.platformVersion}",
                "Client-Version" to versionInfo.releaseVersion)
        logger.trace { "Published NodeInfo to $publishURL successfully." }
    }

    fun ackNetworkParametersUpdate(signedParametersHash: SignedData<SecureHash>) {
        val ackURL = URL("$networkMapUrl/ack-parameters")
        logger.trace { "Sending network parameters with hash ${signedParametersHash.raw.deserialize()} approval to $ackURL." }
        ackURL.post(signedParametersHash.serialize(),
                "Platform-Version" to "${versionInfo.platformVersion}",
                "Client-Version" to versionInfo.releaseVersion)
        logger.trace { "Sent network parameters approval to $ackURL successfully." }
    }

    fun getNetworkMap(networkMapKey: UUID? = null): NetworkMapResponse {
        val url = networkMapKey?.let { URL("$networkMapUrl/$networkMapKey") } ?: networkMapUrl
        logger.trace { "Fetching network map update from $url." }
        val connection = url.openHttpConnection()
        val signedNetworkMap = connection.responseAs<SignedNetworkMap>()
        val networkMap = signedNetworkMap.verifiedNetworkMapCert(trustRoots)
        val timeout = connection.cacheControl.maxAgeSeconds.seconds
        val version = connection.cordaServerVersion
        logger.trace { "Fetched network map update from $url successfully: $networkMap" }
        return NetworkMapResponse(networkMap, timeout, version)
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

    fun getNodeInfos(): List<NodeInfo> {
        val url = URL("$networkMapUrl/node-infos")
        logger.trace { "Fetching node infos from $url." }
        val verifiedNodeInfo = url.openHttpConnection().responseAs<Pair<SignedNetworkMap, List<SignedNodeInfo>>>()
                .also {
                    val verifiedNodeInfoHashes = it.first.verifiedNetworkMapCert(trustRoots).nodeInfoHashes
                    val nodeInfoHashes = it.second.map { signedNodeInfo -> signedNodeInfo.verified().serialize().sha256() }
                    require(
                            verifiedNodeInfoHashes.containsAll(nodeInfoHashes) &&
                                    verifiedNodeInfoHashes.size == nodeInfoHashes.size
                    )
                }
                .second.map { it.verified() }
        logger.trace { "Fetched node infos successfully. Node Infos size: ${verifiedNodeInfo.size}" }
        return verifiedNodeInfo
    }

    fun myPublicHostname(): String {
        val url = URL("$networkMapUrl/my-hostname")
        logger.trace { "Resolving public hostname from '$url'." }
        val hostName = url.openHttpConnection().inputStream.bufferedReader().use(BufferedReader::readLine)
        logger.trace { "My public hostname is $hostName." }
        return hostName
    }
}

data class NetworkMapResponse(val payload: NetworkMap, val cacheMaxAge: Duration, val serverVersion: String)
