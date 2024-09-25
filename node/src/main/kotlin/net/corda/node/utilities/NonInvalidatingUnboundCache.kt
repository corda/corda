package net.corda.node.utilities

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener
import net.corda.core.internal.NamedCacheFactory

class NonInvalidatingUnboundCache<K : Any, V : Any> private constructor(val cache: LoadingCache<K, V>) : LoadingCache<K, V> by cache {
    constructor(name: String,
                cacheFactory: NamedCacheFactory,
                loadFunction: (K) -> V?,
                removalListener: RemovalListener<K, V> = RemovalListener { _, _, _ -> },
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(name, cacheFactory, loadFunction, removalListener, keysToPreload))

    private companion object {
        private fun <K : Any, V : Any> buildCache(name: String,
                                                  cacheFactory: NamedCacheFactory,
                                                  loadFunction: (K) -> V?,
                                                  removalListener: RemovalListener<K, V>,
                                                  keysToPreload: () -> Iterable<K>): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().removalListener(removalListener).executor(SameThreadExecutor.getExecutor())
            return cacheFactory.buildNamed(builder, name, NonInvalidatingCacheLoader(loadFunction)).apply {
                getAll(keysToPreload())
            }
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    private class NonInvalidatingCacheLoader<K : Any, V : Any>(val loadFunction: (K) -> V?) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}
