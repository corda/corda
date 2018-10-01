package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.profiling.CacheTracing
import net.corda.core.internal.profiling.CacheTracing.Companion.wrap
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.KB
import net.corda.node.services.config.MB
import net.corda.node.services.config.NodeConfiguration
import java.util.concurrent.TimeUnit


class EnterpriseNamedCacheFactory private constructor(private val tracingConfig: CacheTracing.CacheTracingConfig, private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : NamedCacheFactory, SingletonSerializeAsToken() {
    constructor(tracingConfig: CacheTracing.CacheTracingConfig) : this(tracingConfig, null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): NamedCacheFactory = EnterpriseNamedCacheFactory(tracingConfig, metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): NamedCacheFactory = EnterpriseNamedCacheFactory(tracingConfig, this.metricRegistry, nodeConfiguration)

    // Scale most caches off the transaction cache size.
    private fun defaultBound(nodeConfiguration: NodeConfiguration): Long = nodeConfiguration.transactionCacheSizeBytes / 8.KB

    // This result in the minimum being 10MB as per OS, but it then grows as per the transaction cache.
    private fun defaultAttachmentCacheBound(nodeConfiguration: NodeConfiguration): Long = nodeConfiguration.transactionCacheSizeBytes + 2.MB

    // This results in a minium of 1024 entries as per OS, but then grows linearly with attachment cache size.
    private fun defaultAttachmentPresenceCacheBound(nodeConfiguration: NodeConfiguration): Long = defaultAttachmentCacheBound(nodeConfiguration) / 10.KB

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        return wrap(caffeine.maximumSize(defaultBound(nodeConfiguration!!)).buildNamed<K, V>(metricRegistry!!, name), tracingConfig, name)
    }

    // TODO: allow a config file override for any named cache.
    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        val configuredCaffeine = when (name) {
            "DBTransactionStorage_transactions" -> caffeine.maximumWeight(nodeConfiguration!!.transactionCacheSizeBytes)
            "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(defaultAttachmentCacheBound(nodeConfiguration!!))
            "NodeAttachmentService_attachmentPresence" -> caffeine.maximumSize(defaultAttachmentPresenceCacheBound(nodeConfiguration!!))
            "P2PMessageDeduplicator_senderUUIDSeqNoHWM" -> caffeine.expireAfterAccess(7, TimeUnit.DAYS)
            else -> caffeine.maximumSize(defaultBound(nodeConfiguration!!))
        }
        return wrap(configuredCaffeine.buildNamed<K, V>(metricRegistry!!, name, loader), tracingConfig, name)
    }
}