package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

/**
 * Allow extra functionality to be injected to our caches.
 */
interface NamedCacheFactory {
    companion object {
        private val allowedChars = Regex("""^[0-9A-Za-z_.]*$""")
    }

    /**
     * Restrict the allowed characters of a cache name - this ensures that each cache has a name, and that
     * the name can be used to create a file name or a metric name.
     */
    fun checkCacheName(name: String) {
        require(name.isNotBlank()) { "Name must not be empty or only whitespace" }
        require(allowedChars.matches(name)) { "Invalid characters in cache name" }
    }

    fun <K : Any, V : Any> buildNamed(name: String): Cache<K, V> = buildNamed(Caffeine.newBuilder(), name)

    fun <K : Any, V : Any> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V>

    fun <K : Any, V : Any> buildNamed(name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> = buildNamed(Caffeine.newBuilder(), name, loader)

    fun <K : Any, V : Any> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V>
}
