package net.corda.core.utilities

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

class NamedCache<K, V>(private val cache: Cache<K, V>, val name: String) : Cache<K, V> by cache

class NamedLoadingCache<K, V>(private val cache: LoadingCache<K, V>, val name: String) : LoadingCache<K, V> by cache

fun <K, V> Caffeine<Any, Any>.buildNamed(name: String): NamedCache<K, V> {
    return NamedCache(this.build<K, V>(), name)
}

fun <K, V> Caffeine<Any, Any>.buildNamed(name: String, loadFunc: (K) -> V): NamedLoadingCache<K, V> {
    return NamedLoadingCache(this.build<K, V>(loadFunc), name)
}


fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loader: CacheLoader<K, V>): NamedLoadingCache<K, V> {
    return NamedLoadingCache(this.build<K, V>(loader), name)
}
