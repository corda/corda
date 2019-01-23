package net.corda.nodeapi.internal.persistence

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.BindableNamedCacheFactory
import java.lang.IllegalArgumentException

// TODO: Need to resolve circular dependency

class MigrationNamedCacheFactory(private val metricRegistry: MetricRegistry?,
                                 private val nodeConfiguration: NodeConfiguration?) : BindableNamedCacheFactory, SingletonSerializeAsToken() {

    override fun bindWithMetrics(metricRegistry: MetricRegistry) = MigrationNamedCacheFactory(metricRegistry, this.nodeConfiguration)
    override fun bindWithConfig(nodeConfiguration: NodeConfiguration) = MigrationNamedCacheFactory(this.metricRegistry, nodeConfiguration)

    private fun <K, V> configuredForNamed(caffeine: Caffeine<K, V>, name: String): Caffeine<K, V> {
        return when(name) {
            "HibernateConfiguration_sessionFactories" -> caffeine.maximumSize(
                    nodeConfiguration?.database?.mappedSchemaCacheSize ?: DatabaseConfig.Defaults.mappedSchemaCacheSize)
            "DBTransactionStorage_transactions" -> caffeine.maximumWeight(
                    nodeConfiguration?.transactionCacheSizeBytes ?: NodeConfiguration.defaultTransactionCacheSize
            )
            else -> throw IllegalArgumentException("Unexpected cache name $name.")
        }
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        return configuredForNamed(caffeine, name).build()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        return configuredForNamed(caffeine, name).build(loader)
    }
}