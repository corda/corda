package net.corda.node.services.network

import com.google.common.util.concurrent.MoreExecutors
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.openHttpConnection
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.utilities.NamedThreadFactory
import net.corda.nodeapi.internal.NetworkMap
import net.corda.nodeapi.internal.NetworkParameters
import net.corda.nodeapi.internal.SignedNetworkMap
import net.corda.nodeapi.internal.crypto.X509Utilities
import okhttp3.CacheControl
import okhttp3.Headers
import rx.Subscription
import java.io.BufferedReader
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkMapClient(compatibilityZoneURL: URL, private val trustedRoot: X509Certificate) {
    private val networkMapUrl = URL("$compatibilityZoneURL/network-map")

    fun publish(signedNodeInfo: SignedData<NodeInfo>) {
        val publishURL = URL("$networkMapUrl/publish")
        val conn = publishURL.openHttpConnection()
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.outputStream.use { it.write(signedNodeInfo.serialize().bytes) }

        // This will throw IOException if the response code is not HTTP 200.
        // This gives a much better exception then reading the error stream.
        conn.inputStream.close()
    }

    fun getNetworkMap(): NetworkMapResponse {
        val conn = networkMapUrl.openHttpConnection()
        val signedNetworkMap = conn.inputStream.use { it.readBytes() }.deserialize<SignedNetworkMap>()
        val networkMap = signedNetworkMap.verified(trustedRoot)
        val timeout = CacheControl.parse(Headers.of(conn.headerFields.filterKeys { it != null }.mapValues { it.value.first() })).maxAgeSeconds().seconds
        return NetworkMapResponse(networkMap, timeout)
    }

    fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo? {
        val conn = URL("$networkMapUrl/node-info/$nodeInfoHash").openHttpConnection()
        return if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            null
        } else {
            val signedNodeInfo = conn.inputStream.use { it.readBytes() }.deserialize<SignedData<NodeInfo>>()
            val nodeInfo = signedNodeInfo.verified()
            // Verify node info is signed by node identity
            // TODO : Validate multiple signatures when NodeInfo supports multiple identities.
            require(nodeInfo.legalIdentities.any { it.owningKey == signedNodeInfo.sig.by }) { "NodeInfo must be signed by the node owning key." }
            nodeInfo
        }
    }

    fun getNetworkParameter(networkParameterHash: SecureHash): NetworkParameters? {
        val conn = URL("$networkMapUrl/network-parameter/$networkParameterHash").openHttpConnection()
        return if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            null
        } else {
            conn.inputStream.use { it.readBytes() }.deserialize()
        }
    }

    fun myPublicHostname(): String {
        val conn = URL("$networkMapUrl/my-hostname").openHttpConnection()
        return conn.inputStream.bufferedReader().use(BufferedReader::readLine)
    }
}

data class NetworkMapResponse(val networkMap: NetworkMap, val cacheMaxAge: Duration)

class NetworkMapUpdater(private val networkMapCache: NetworkMapCacheInternal,
                        private val fileWatcher: NodeInfoWatcher,
                        private val networkMapClient: NetworkMapClient?) : Closeable {
    companion object {
        private val logger = contextLogger()
        private val retryInterval = 1.minutes
    }

    private val executor = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("Network Map Updater Thread", Executors.defaultThreadFactory()))
    private var fileWatcherSubscription: Subscription? = null

    override fun close() {
        fileWatcherSubscription?.unsubscribe()
        MoreExecutors.shutdownAndAwaitTermination(executor, 50, TimeUnit.SECONDS)
    }

    fun updateNodeInfo(newInfo: NodeInfo, signNodeInfo: (NodeInfo) -> SignedData<NodeInfo>) {
        val oldInfo = networkMapCache.getNodeByLegalIdentity(newInfo.legalIdentities.first())
        // Compare node info without timestamp.
        if (newInfo.copy(serial = 0L) == oldInfo?.copy(serial = 0L)) return

        // Only publish and write to disk if there are changes to the node info.
        val signedNodeInfo = signNodeInfo(newInfo)
        networkMapCache.addNode(newInfo)
        fileWatcher.saveToFile(signedNodeInfo)

        if (networkMapClient != null) {
            tryPublishNodeInfoAsync(signedNodeInfo, networkMapClient)
        }
    }

    fun subscribeToNetworkMap() {
        require(fileWatcherSubscription == null) { "Should not call this method twice." }
        // Subscribe to file based networkMap
        fileWatcherSubscription = fileWatcher.nodeInfoUpdates().subscribe(networkMapCache::addNode)

        if (networkMapClient == null) return
        // Subscribe to remote network map if configured.
        val task = object : Runnable {
            override fun run() {
                val nextScheduleDelay = try {
                    val (networkMap, cacheTimeout) = networkMapClient.getNetworkMap()
                    val currentNodeHashes = networkMapCache.allNodeHashes
                    val hashesFromNetworkMap = networkMap.nodeInfoHashes
                    (hashesFromNetworkMap - currentNodeHashes).mapNotNull {
                        // Download new node info from network map
                        try {
                            networkMapClient.getNodeInfo(it)
                        } catch (t: Throwable) {
                            // Failure to retrieve one node info shouldn't stop the whole update, log and return null instead.
                            logger.warn("Error encountered when downloading node info '$it', skipping...", t)
                            null
                        }
                    }.forEach {
                        // Add new node info to the network map cache, these could be new node info or modification of node info for existing nodes.
                        networkMapCache.addNode(it)
                    }
                    // Remove node info from network map.
                    (currentNodeHashes - hashesFromNetworkMap - fileWatcher.processedNodeInfoHashes)
                            .mapNotNull(networkMapCache::getNodeByHash)
                            .forEach(networkMapCache::removeNode)
                    // TODO: Check NetworkParameter.
                    cacheTimeout
                } catch (t: Throwable) {
                    logger.warn("Error encountered while updating network map, will retry in ${retryInterval.seconds} seconds", t)
                    retryInterval
                }
                // Schedule the next update.
                executor.schedule(this, nextScheduleDelay.toMillis(), TimeUnit.MILLISECONDS)
            }
        }
        executor.submit(task) // The check may be expensive, so always run it in the background even the first time.
    }

    private fun tryPublishNodeInfoAsync(signedNodeInfo: SignedData<NodeInfo>, networkMapClient: NetworkMapClient) {
        val task = object : Runnable {
            override fun run() {
                try {
                    networkMapClient.publish(signedNodeInfo)
                } catch (t: Throwable) {
                    logger.warn("Error encountered while publishing node info, will retry in ${retryInterval.seconds} seconds.", t)
                    // TODO: Exponential backoff?
                    executor.schedule(this, retryInterval.toMillis(), TimeUnit.MILLISECONDS)
                }
            }
        }
        executor.submit(task)
    }
}