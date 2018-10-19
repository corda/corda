package net.corda.client.rpc.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.NamedCacheFactory

class ClientCacheFactory : NamedCacheFactory {
    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkCacheName(name)
        return caffeine.build<K, V>()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkCacheName(name)
        return caffeine.build<K, V>(loader)
    }
}