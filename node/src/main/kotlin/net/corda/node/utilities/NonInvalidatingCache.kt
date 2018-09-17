package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher

class NonInvalidatingCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(metricRegistry: MetricRegistry, name: String, bound: Long, loadFunction: (K) -> V) :
            this(buildCache(metricRegistry, name, bound, loadFunction))

    private companion object {
        private fun <K, V> buildCache(metricRegistry: MetricRegistry, name: String, bound: Long, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumSize(bound)
            return builder.buildNamed(metricRegistry, name, NonInvalidatingCacheLoader(loadFunction))
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}

class NonInvalidatingWeightBasedCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {
    constructor (metricRegistry: MetricRegistry, name: String, maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V) :
            this(buildCache(metricRegistry, name, maxWeight, weigher, loadFunction))

    private companion object {
        private fun <K, V> buildCache(metricRegistry: MetricRegistry, name: String, maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumWeight(maxWeight).weigher(weigher)
            return builder.buildNamed(metricRegistry, name, NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction))
        }
    }
}