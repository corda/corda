package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.buildNamed
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.NodeConfiguration

/**
 * Allow passing metrics and config to caching implementations.
 */
interface NamedCacheFactory : SerializeAsToken {
    /**
     * Build a new cache factory of the same type that incorporates metrics.
     */
    fun bindWithMetrics(metricRegistry: MetricRegistry): NamedCacheFactory

    /**
     * Build a new cache factory of the same type that incorporates the associated configuration.
     */
    fun bindWithConfig(nodeConfiguration: NodeConfiguration): NamedCacheFactory

    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V>
    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V>
}

class DefaultNamedCacheFactory private constructor(private val metricRegistry: MetricRegistry?, private val nodeConfiguration: NodeConfiguration?) : NamedCacheFactory, SingletonSerializeAsToken() {
    constructor() : this(null, null)

    override fun bindWithMetrics(metricRegistry: MetricRegistry): NamedCacheFactory = DefaultNamedCacheFactory(metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration): NamedCacheFactory = DefaultNamedCacheFactory(this.metricRegistry, nodeConfiguration)

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        return caffeine.maximumSize(1024).buildNamed<K, V>(name)
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkNotNull(metricRegistry)
        checkNotNull(nodeConfiguration)
        val configuredCaffeine = when (name) {
            "DBTransactionStorage_transactions" -> caffeine.maximumWeight(nodeConfiguration!!.transactionCacheSizeBytes)
            "NodeAttachmentService_attachmentContent" -> caffeine.maximumWeight(nodeConfiguration!!.attachmentContentCacheSizeBytes)
            "NodeAttachmentService_attachmentPresence" -> caffeine.maximumSize(nodeConfiguration!!.attachmentCacheBound)
            else -> caffeine.maximumSize(1024)
        }
        return configuredCaffeine.buildNamed<K, V>(name, loader)
    }
}