package net.corda.serialization.internal.model

import java.util.concurrent.ConcurrentHashMap

/**
 * We can't have [ConcurrentHashMap]s in the DJVM, so it must supply its own version of this object which returns
 * plain old [MutableMap]s instead.
 */
object DefaultCacheProvider {
    fun <K, V> createCache(): MutableMap<K, V> = ConcurrentHashMap()
}