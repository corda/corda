package net.corda.testing.internal

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.buildNamed
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.MB
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.NamedCacheFactory

class TestingNamedCacheFactory private constructor(private val sizeOverride: Long, private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : NamedCacheFactory, SingletonSerializeAsToken() {
    constructor(sizeOverride: Long = 1024) : this(sizeOverride, null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): NamedCacheFactory = TestingNamedCacheFactory(sizeOverride, metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): NamedCacheFactory = TestingNamedCacheFactory(sizeOverride, this.metricRegistry, nodeConfiguration)

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        // Does not check metricRegistry or nodeConfiguration, because for tests we don't care.
        return caffeine.maximumSize(sizeOverride).buildNamed<K, V>(name)
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        // Does not check metricRegistry or nodeConfiguration, because for tests we don't care.
        val configuredCaffeine = when (name) {
            "DBTransactionStorage_transactions" -> caffeine.maximumWeight(1.MB)
            "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(1.MB)
            else -> caffeine.maximumSize(sizeOverride)
        }
        return configuredCaffeine.buildNamed<K, V>(name, loader)
    }
}