package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher
import net.corda.core.internal.NamedLoadingCache
import net.corda.core.internal.buildNamed

class NonInvalidatingCache<K, V> private constructor(
        val cache: NamedLoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(name: String, bound: Long, loadFunction: (K) -> V) :
            this(buildCache(name, bound, loadFunction))

    private companion object {
        private fun <K, V> buildCache(name: String, bound: Long, loadFunction: (K) -> V): NamedLoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumSize(bound)
            return builder.buildNamed(name, NonInvalidatingCacheLoader(loadFunction))
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
        val cache: NamedLoadingCache<K, V>
) : LoadingCache<K, V> by cache {
    constructor (name: String, maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V) :
            this(buildCache(name, maxWeight, weigher, loadFunction))

    private companion object {
        private fun <K, V> buildCache(name: String, maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V): NamedLoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumWeight(maxWeight).weigher(weigher)
            return builder.buildNamed(name, NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction))
        }
    }
}