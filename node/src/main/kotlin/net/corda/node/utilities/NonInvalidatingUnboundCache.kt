package net.corda.node.utilities

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener

class NonInvalidatingUnboundCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(loadFunction: (K) -> V, removalListener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(loadFunction, removalListener, keysToPreload))

    private companion object {
        private fun <K, V> buildCache(loadFunction: (K) -> V, removalListener: RemovalListener<K, V>,
                                      keysToPreload: () -> Iterable<K>): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().removalListener(removalListener).executor(SameThreadExecutor.getExecutor())
            return builder.build(NonInvalidatingCacheLoader(loadFunction)).apply {
                getAll(keysToPreload())
            }
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    private class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}