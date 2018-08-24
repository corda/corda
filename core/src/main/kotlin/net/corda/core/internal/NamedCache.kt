package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

/* buildNamed is the central helper method to build caffeine caches in Corda.
 * This allows to easily add tweaks to all caches build in Corda, and also forces
 * cache users to give their cache a (meaningful) name that can be used e.g. for
 * capturing cache traces etc.
 *
 * Currently it is not used in this version of CORDA, but there are plans to do so
 */

@Suppress("UNUSED_PARAMETER")
fun <K, V> Caffeine<in K, in V>.buildNamed(name: String): Cache<K, V> {
    return this.build<K, V>()
}

@Suppress("UNUSED_PARAMETER")
fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loadFunc: (K) -> V): LoadingCache<K, V> {
    return this.build<K, V>(loadFunc)
}


@Suppress("UNUSED_PARAMETER")
fun <K, V> Caffeine<in K, in V>.buildNamed(name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
    return this.build<K, V>(loader)
}
