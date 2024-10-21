package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher
import net.corda.core.internal.NamedCacheFactory

class NonInvalidatingCache<K : Any, V : Any> private constructor(val cache: LoadingCache<K, V>) : LoadingCache<K, V> by cache {
    constructor(cacheFactory: NamedCacheFactory, name: String, loadFunction: (K) -> V?) : this(buildCache(cacheFactory, name, loadFunction))

    private companion object {
        private fun <K : Any, V : Any> buildCache(cacheFactory: NamedCacheFactory, name: String, loadFunction: (K) -> V?): LoadingCache<K, V> {
            return cacheFactory.buildNamed(name, NonInvalidatingCacheLoader(loadFunction))
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    class NonInvalidatingCacheLoader<K : Any, V : Any>(val loadFunction: (K) -> V?) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}

class NonInvalidatingWeightBasedCache<K : Any, V : Any> private constructor(val cache: LoadingCache<K, V>) : LoadingCache<K, V> by cache {
    constructor(cacheFactory: NamedCacheFactory, name: String, weigher: Weigher<K, V>, loadFunction: (K) -> V?) :
            this(buildCache(cacheFactory, name, weigher, loadFunction))

    private companion object {
        private fun <K : Any, V : Any> buildCache(cacheFactory: NamedCacheFactory,
                                                  name: String,
                                                  weigher: Weigher<K, V>,
                                                  loadFunction: (K) -> V?): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().weigher(weigher)
            return cacheFactory.buildNamed(builder, name, NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction))
        }
    }
}
