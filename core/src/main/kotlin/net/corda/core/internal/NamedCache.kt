package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.profiling.CacheTracing.Companion.wrap

/**
 * Restrict the allowed characters of a cache name - this ensures that each cache has a name, and that
 * the name can be used to create a file name or a metric name.
 */
internal fun checkCacheName(name: String) {
    require(!name.isBlank())
    require(allowedChars.matches(name))
}

private val allowedChars = Regex("^[0-9A-Za-z_.]*\$")

/* buildNamed is the central helper method to build caffeine caches in Corda.
 * This allows to easily add tweaks to all caches built in Corda, and also forces
 * cache users to give their cache a (meaningful) name that can be used e.g. for
 * capturing cache traces etc.
 */

fun <K, V> Caffeine<in K, in V>.buildNamed(name: String): Cache<K, V> {
    checkCacheName(name)
    return wrap(this.build<K, V>(), name)
}

fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loadFunc: (K) -> V): LoadingCache<K, V> {
    checkCacheName(name)
    return wrap(this.build<K, V>(loadFunc), name)
}


fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
    checkCacheName(name)
    return wrap(this.build<K, V>(loader), name)
}
