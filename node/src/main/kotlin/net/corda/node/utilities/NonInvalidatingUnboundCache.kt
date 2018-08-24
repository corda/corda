package net.corda.node.utilities

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener
import net.corda.core.internal.NamedLoadingCache
import net.corda.core.internal.buildNamed

class NonInvalidatingUnboundCache<K, V> private constructor(
        val cache: NamedLoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(name: String, loadFunction: (K) -> V, removalListener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(name, loadFunction, removalListener, keysToPreload))

    private companion object {
        private fun <K, V> buildCache(name: String, loadFunction: (K) -> V, removalListener: RemovalListener<K, V>,
                                      keysToPreload: () -> Iterable<K>): NamedLoadingCache<K, V> {
            val builder = Caffeine.newBuilder().removalListener(removalListener).executor(SameThreadExecutor.getExecutor())
            return builder.buildNamed(name, NonInvalidatingCacheLoader(loadFunction)).apply {
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