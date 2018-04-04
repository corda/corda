/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.network

import com.google.common.util.concurrent.MoreExecutors
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.copyTo
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.readObject
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.utilities.NamedThreadFactory
import net.corda.nodeapi.exceptions.OutdatedNetworkParameterHashException
import net.corda.nodeapi.internal.network.*
import rx.Subscription
import rx.subjects.PublishSubject
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class NetworkMapUpdater(private val networkMapCache: NetworkMapCacheInternal,
                        private val fileWatcher: NodeInfoWatcher,
                        private val networkMapClient: NetworkMapClient?,
                        private val currentParametersHash: SecureHash,
                        private val baseDirectory: Path
) : AutoCloseable {
    companion object {
        private val logger = contextLogger()
        private val defaultRetryInterval = 1.minutes
    }

    private val parametersUpdatesTrack: PublishSubject<ParametersUpdateInfo> = PublishSubject.create<ParametersUpdateInfo>()
    private val executor = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("Network Map Updater Thread", Executors.defaultThreadFactory()))
    private var newNetworkParameters: Pair<ParametersUpdate, SignedNetworkParameters>? = null
    private var fileWatcherSubscription: Subscription? = null

    override fun close() {
        fileWatcherSubscription?.unsubscribe()
        MoreExecutors.shutdownAndAwaitTermination(executor, 50, TimeUnit.SECONDS)
    }

    fun trackParametersUpdate(): DataFeed<ParametersUpdateInfo?, ParametersUpdateInfo> {
        val currentUpdateInfo = newNetworkParameters?.let {
            ParametersUpdateInfo(it.first.newParametersHash, it.second.verified(), it.first.description, it.first.updateDeadline)
        }
        return DataFeed(currentUpdateInfo, parametersUpdatesTrack)
    }

    fun subscribeToNetworkMap() {
        require(fileWatcherSubscription == null) { "Should not call this method twice." }
        // Subscribe to file based networkMap
        fileWatcherSubscription = fileWatcher.nodeInfoUpdates().subscribe(networkMapCache::addNode)

        if (networkMapClient == null) return

        // Subscribe to remote network map if configured.
        executor.submit(object : Runnable {
            override fun run() {
                val nextScheduleDelay = try {
                    updateNetworkMapCache(networkMapClient)
                } catch (t: Throwable) {
                    logger.warn("Error encountered while updating network map, will retry in $defaultRetryInterval", t)
                    defaultRetryInterval
                }
                // Schedule the next update.
                executor.schedule(this, nextScheduleDelay.toMillis(), TimeUnit.MILLISECONDS)
            }
        }) // The check may be expensive, so always run it in the background even the first time.
    }

    private fun updateNetworkMapCache(networkMapClient: NetworkMapClient): Duration {
        val (networkMap, cacheTimeout) = networkMapClient.getNetworkMap()
        networkMap.parametersUpdate?.let { handleUpdateNetworkParameters(networkMapClient, it) }

        if (currentParametersHash != networkMap.networkParameterHash) {
            exitOnParametersMismatch(networkMap)
        }

        val currentNodeHashes = networkMapCache.allNodeHashes
        val hashesFromNetworkMap = networkMap.nodeInfoHashes
        (hashesFromNetworkMap - currentNodeHashes).mapNotNull {
            // Download new node info from network map
            try {
                networkMapClient.getNodeInfo(it)
            } catch (e: Exception) {
                // Failure to retrieve one node info shouldn't stop the whole update, log and return null instead.
                logger.warn("Error encountered when downloading node info '$it', skipping...", e)
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

        return cacheTimeout
    }

    private fun exitOnParametersMismatch(networkMap: NetworkMap) {
        val updatesFile = baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME
        val acceptedHash = if (updatesFile.exists()) updatesFile.readObject<SignedNetworkParameters>().raw.hash else null
        val exitCode = if (acceptedHash == networkMap.networkParameterHash) {
            logger.info("Flag day occurred. Network map switched to the new network parameters: " +
                    "${networkMap.networkParameterHash}. Node will shutdown now and needs to be started again.")
            0
        } else {
            // TODO This needs special handling (node omitted update process or didn't accept new parameters)
            logger.error("Node is using parameters with hash: $currentParametersHash but network map is " +
                    "advertising: ${networkMap.networkParameterHash}.\n" +
                    "Node will shutdown now. Please update node to use correct network parameters file.")
            1
        }
        exitProcess(exitCode)
    }

    private fun handleUpdateNetworkParameters(networkMapClient: NetworkMapClient, update: ParametersUpdate) {
        if (update.newParametersHash == newNetworkParameters?.first?.newParametersHash) {
            // This update was handled already.
            return
        }
        val newSignedNetParams = networkMapClient.getNetworkParameters(update.newParametersHash)
        val newNetParams = newSignedNetParams.verifiedNetworkMapCert(networkMapClient.trustedRoot)
        logger.info("Downloaded new network parameters: $newNetParams from the update: $update")
        newNetworkParameters = Pair(update, newSignedNetParams)
        val updateInfo = ParametersUpdateInfo(
                update.newParametersHash,
                newNetParams,
                update.description,
                update.updateDeadline)
        parametersUpdatesTrack.onNext(updateInfo)
    }

    fun acceptNewNetworkParameters(parametersHash: SecureHash, sign: (SecureHash) -> SignedData<SecureHash>) {
        networkMapClient ?: throw IllegalStateException("Network parameters updates are not supported without compatibility zone configured")
        // TODO This scenario will happen if node was restarted and didn't download parameters yet, but we accepted them.
        // Add persisting of newest parameters from update.
        val (update, signedNewNetParams) = requireNotNull(newNetworkParameters) { "Couldn't find parameters update for the hash: $parametersHash" }
        // We should check that we sign the right data structure hash.
        val newNetParams = signedNewNetParams.verifiedNetworkMapCert(networkMapClient.trustedRoot)
        val newParametersHash = newNetParams.serialize().hash
        if (parametersHash == newParametersHash) {
            // The latest parameters have priority.
            signedNewNetParams.serialize()
                    .open()
                    .copyTo(baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME, StandardCopyOption.REPLACE_EXISTING)
            networkMapClient.ackNetworkParametersUpdate(sign(parametersHash))
            logger.info("Accepted network parameter update $update: $newNetParams")
        } else {
            throw OutdatedNetworkParameterHashException(parametersHash, newParametersHash)
        }
    }
}
