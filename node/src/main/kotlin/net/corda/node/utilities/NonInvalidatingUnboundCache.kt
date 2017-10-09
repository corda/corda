package net.corda.node.utilities

import com.google.common.cache.*
import com.google.common.util.concurrent.ListenableFuture


class NonInvalidatingUnboundCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(concurrencyLevel: Int, loadFunction: (K) -> V, removalListener: RemovalListener<K, V> = RemovalListener {},
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(concurrencyLevel, loadFunction, removalListener, keysToPreload))

    private companion object {
        private fun <K, V> buildCache(concurrencyLevel: Int, loadFunction: (K) -> V, removalListener: RemovalListener<K, V>,
                                      keysToPreload: () -> Iterable<K>): LoadingCache<K, V> {
            val builder = CacheBuilder.newBuilder().concurrencyLevel(concurrencyLevel).removalListener(removalListener)
            return builder.build(NonInvalidatingCacheLoader(loadFunction)).apply {
                getAll(keysToPreload())
            }
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    private class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V>() {
        override fun reload(key: K, oldValue: V): ListenableFuture<V> {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}