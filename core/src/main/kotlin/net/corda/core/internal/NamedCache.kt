package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

fun <K, V> Caffeine<Any, Any>.buildNamed(name: String): Cache<K, V> {
    return this.build<K, V>()
}

fun <K, V> Caffeine<Any, Any>.buildNamed(name: String, loadFunc: (K) -> V): LoadingCache<K, V> {
    return this.build<K, V>(loadFunc)
}


fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
    return this.build<K, V>(loader)
}
