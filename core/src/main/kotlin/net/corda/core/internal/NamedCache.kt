package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache


/**
 * Restrict the allowed characters of a cache name - this ensures that each cache has a name, and that
 * the name can be used to create a file name or a metric name.
 */
fun checkCacheName(name: String) {
    require(!name.isBlank())
    require(allowedChars.matches(name))
}

val allowedChars = Regex("^[0-9A-Za-z_.]*\$")


/* buildNamed is the central helper method to build caffeine caches in Corda.
 * This allows to easily add tweaks to all caches build in Corda, and also forces
 * cache users to give their cache a (meaningful) name that can be used e.g. for
 * capturing cache traces etc.
 *
 * Currently it is not used in this version of CORDA, but there are plans to do so
 */

fun <K, V> Caffeine<in K, in V>.buildNamed(name: String): Cache<K, V> {
    checkCacheName(name)
    return this.build<K, V>()
}

fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loadFunc: (K) -> V): LoadingCache<K, V> {
    checkCacheName(name)
    return this.build<K, V>(loadFunc)
}


fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
    checkCacheName(name)
    return this.build<K, V>(loader)
}
