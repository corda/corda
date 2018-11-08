package net.corda.node.services.config

import net.corda.core.utilities.NetworkHostAndPort
import java.io.File
import java.net.InetAddress
import java.nio.file.Path
import net.corda.nodeapi.internal.config.ExternalBrokerConnectionConfiguration

data class EnterpriseConfiguration(
        val mutualExclusionConfiguration: MutualExclusionConfiguration,
        val externalBrokerConnectionConfiguration: ExternalBrokerConnectionConfiguration = Defaults.externalBrokerConnectionConfiguration,
        val externalBrokerBackupAddresses: List<NetworkHostAndPort> = Defaults.externalBrokerBackupAddresses,
        val useMultiThreadedSMM: Boolean = Defaults.useMultiThreadedSMM,
        val tuning: PerformanceTuning = Defaults.tuning,
        val externalBridge: Boolean? = null,
        val enableCacheTracing: Boolean = Defaults.enableCacheTracing,
        val traceTargetDirectory: Path = Defaults.traceTargetDirectory
) {
    internal object Defaults {
        val externalBrokerConnectionConfiguration: ExternalBrokerConnectionConfiguration = ExternalBrokerConnectionConfiguration.DEFAULT
        val externalBrokerBackupAddresses: List<NetworkHostAndPort> = emptyList()
        val useMultiThreadedSMM: Boolean = true
        val tuning: PerformanceTuning = PerformanceTuning.default
        val enableCacheTracing: Boolean = false
        val traceTargetDirectory: Path = File(".").toPath()
    }
}

data class MutualExclusionConfiguration(val on: Boolean = Defaults.on,
                                        val machineName: String = Defaults.machineName,
                                        val updateInterval: Long,
                                        val waitInterval: Long
) {
    internal object Defaults {
        val machineName = InetAddress.getLocalHost().hostName
        val on: Boolean = false
    }
}

/**
 * @param flowThreadPoolSize Determines the size of the thread pool used by the flow framework to run flows.
 * @param maximumMessagingBatchSize Determines the maximum number of jobs the messaging layer submits asynchronously
 *     before waiting for a flush from the broker.
 * @param rpcThreadPoolSize Determines the number of threads used by the RPC server to serve requests.
 * @param p2pConfirmationWindowSize Determines the number of bytes buffered by the broker before flushing to disk and
 *     acking the triggering send. Setting this to -1 causes session commits to immediately return, potentially
 *     causing blowup in the broker if the rate of sends exceeds the broker's flush rate. Note also that this window
 *     causes send latency to be around [brokerConnectionTtlCheckInterval] if the window isn't saturated.
 * @param brokerConnectionTtlCheckIntervalMs Determines the interval of TTL timeout checks, but most importantly it also
 *     determines the flush period of message acks in case [p2pConfirmationWindowSize] is not saturated in time.
 */
data class PerformanceTuning(
        val flowThreadPoolSize: Int,
        val maximumMessagingBatchSize: Int,
        val rpcThreadPoolSize: Int,
        val p2pConfirmationWindowSize: Int,
        val brokerConnectionTtlCheckIntervalMs: Long
) {
    companion object {
        val default = PerformanceTuning(
                flowThreadPoolSize = 1,
                maximumMessagingBatchSize = 256,
                rpcThreadPoolSize = 4,
                p2pConfirmationWindowSize = 1048576,
                brokerConnectionTtlCheckIntervalMs = 20
        )
    }
}
