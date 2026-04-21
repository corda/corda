package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher
import net.corda.core.internal.NamedCacheFactory

class NonInvalidatingCache<K : Any, V : Any> private constructor(val cache: LoadingCache<K, V>) : LoadingCache<K, V> by cache {
    constructor(cacheFactory: NamedCacheFactory, name: String, loadFunction: (K) -> V) : this(buildCache(cacheFactory, name, loadFunction))

    private companion object {
        private fun <K : Any, V : Any> buildCache(cacheFactory: NamedCacheFactory, name: String, loadFunction: (K) -> V): LoadingCache<K, V> {
            @Suppress("UNCHECKED_CAST")
            return cacheFactory.buildNamed(name, NonInvalidatingCacheLoader(loadFunction)) as LoadingCache<K, V>
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    @Suppress("UNCHECKED_CAST")
    class NonInvalidatingCacheLoader<K : Any, V : Any>(val loadFunction: (K) -> V) : CacheLoader<K, V?> {
        @Suppress("UNCHECKED_CAST", "OVERRIDE_BY_INLINE")
        override fun reload(key: K, oldValue: V?): V? {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K): V? = loadFunction(key) as V?
    }
}

class NonInvalidatingWeightBasedCache<K : Any, V : Any> private constructor(val cache: LoadingCache<K, V>) : LoadingCache<K, V> by cache {
    @Suppress("TYPE_ARGUMENT_NOT_WITHIN_BOUNDS")
    constructor(cacheFactory: NamedCacheFactory, name: String, weigher: Weigher<K, V>, loadFunction: (K) -> V) :
            this(buildCache(cacheFactory, name, weigher, loadFunction))

    private companion object {
        @Suppress("TYPE_ARGUMENT_NOT_WITHIN_BOUNDS", "UNCHECKED_CAST")
        private fun <K : Any, V : Any> buildCache(cacheFactory: NamedCacheFactory,
                                                  name: String,
                                                  weigher: Weigher<K, V>,
                                                  loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().weigher(weigher as Weigher<K, Any>)
            return cacheFactory.buildNamed(builder, name, NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction)) as LoadingCache<K, V>
        }
    }
}
