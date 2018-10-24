package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

/**
 * Allow extra functionality to be injected to our caches.
 */
interface NamedCacheFactory {
    /**
     * Restrict the allowed characters of a cache name - this ensures that each cache has a name, and that
     * the name can be used to create a file name or a metric name.
     */
    fun checkCacheName(name: String) {
        require(!name.isBlank())
        require(allowedChars.matches(name))
    }

    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V>

    fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V>
}

private val allowedChars = Regex("^[0-9A-Za-z_.]*\$")
