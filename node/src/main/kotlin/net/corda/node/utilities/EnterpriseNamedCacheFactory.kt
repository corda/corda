package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.profiling.CacheTracing
import net.corda.core.internal.profiling.CacheTracing.Companion.wrap
import net.corda.node.services.config.KB
import net.corda.node.services.config.MB
import net.corda.node.services.config.NodeConfiguration
import java.util.concurrent.TimeUnit


class EnterpriseNamedCacheFactory private constructor(private val tracingConfig: CacheTracing.CacheTracingConfig, private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : DefaultNamedCacheFactory(metricRegistry, nodeConfiguration) {
    constructor(tracingConfig: CacheTracing.CacheTracingConfig) : this(tracingConfig, null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): BindableNamedCacheFactory = EnterpriseNamedCacheFactory(tracingConfig, metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): BindableNamedCacheFactory = EnterpriseNamedCacheFactory(tracingConfig, this.metricRegistry, nodeConfiguration)

    // Scale most caches off the transaction cache size.
    private fun defaultBound(nodeConfiguration: NodeConfiguration): Long = nodeConfiguration.transactionCacheSizeBytes / 8.KB

    // This result in the minimum being 10MB as per OS, but it then grows as per the transaction cache.
    private fun defaultAttachmentCacheBound(nodeConfiguration: NodeConfiguration): Long = nodeConfiguration.transactionCacheSizeBytes + 2.MB

    // This results in a minium of 1024 entries as per OS, but then grows linearly with attachment cache size.
    private fun defaultAttachmentPresenceCacheBound(nodeConfiguration: NodeConfiguration): Long = defaultAttachmentCacheBound(nodeConfiguration) / 10.KB

    override val defaultCacheSize = if (nodeConfiguration == null) super.defaultCacheSize else defaultBound(nodeConfiguration)

    override fun <K, V> configuredForNamed(caffeine: Caffeine<K, V>, name: String): Caffeine<K, V> {
        return with(nodeConfiguration!!) {
            when {
                name == "DBTransactionStorage_locks" -> caffeine.maximumSize(defaultCacheSize)
                name == "NodeVaultService_producedStates" -> caffeine.maximumSize(defaultCacheSize)
                name == "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(defaultAttachmentCacheBound(this))
                name == "NodeAttachmentService_attachmentPresence" -> caffeine.maximumSize(defaultAttachmentPresenceCacheBound(this))
                name == "P2PMessageDeduplicator_senderUUIDSeqNoHWM" -> caffeine.expireAfterAccess(7, TimeUnit.DAYS)
                name == "FlowLogicRefFactoryImpl_constructorCache" -> caffeine.maximumSize(defaultCacheSize)
                name == "BasicHSMKeyManagementService_wrapped_keys" -> caffeine.maximumSize(defaultCacheSize)
                else -> super.configuredForNamed(caffeine, name)
            }
        }
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        return wrap(configuredForNamed(caffeine, name).buildNamed<K, V>(metricRegistry!!, name), tracingConfig, name)
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        return wrap(configuredForNamed(caffeine, name).buildNamed<K, V>(metricRegistry!!, name, loader), tracingConfig, name)
    }
}