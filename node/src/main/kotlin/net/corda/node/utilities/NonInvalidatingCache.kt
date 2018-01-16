package net.corda.node.utilities

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.Weigher
import com.google.common.util.concurrent.ListenableFuture


class NonInvalidatingCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(bound: Long, concurrencyLevel: Int, loadFunction: (K) -> V) :
            this(buildCache(bound, concurrencyLevel, loadFunction))

    private companion object {
        private fun <K, V> buildCache(bound: Long, concurrencyLevel: Int, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = CacheBuilder.newBuilder().maximumSize(bound).concurrencyLevel(concurrencyLevel)
            return builder.build(NonInvalidatingCacheLoader(loadFunction))
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V>() {
        override fun reload(key: K, oldValue: V): ListenableFuture<V> {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}

class NonInvalidatingWeightBasedCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {
    constructor (maxWeight: Long, concurrencyLevel: Int, weigher: Weigher<K, V>, loadFunction: (K) -> V) :
            this(buildCache(maxWeight, concurrencyLevel, weigher, loadFunction))


    private companion object {
        private fun <K, V> buildCache(maxWeight: Long, concurrencyLevel: Int, weigher: Weigher<K, V>, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = CacheBuilder.newBuilder().maximumWeight(maxWeight).weigher(weigher).concurrencyLevel(concurrencyLevel)
            return builder.build(NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction))
        }
    }
}