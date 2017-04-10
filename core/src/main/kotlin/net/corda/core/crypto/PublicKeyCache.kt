package net.corda.core.crypto

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import java.security.*

/**
 * A cache for [PublicKey]s required to reduce memory footprint and avoid creating/referencing new [PublicKey] objects
 * during deserialization. This is very helpful in cases where we usually transact with certain clients and for load testing.
 * Current implementation uses Guava's [CacheBuilder] which is thread-safe, and can be safely accessed
 * by multiple concurrent threads. Values are automatically loaded by the cache, and are stored in the cache
 * until evicted. Currently, a reference-based eviction policy is utilised in which softly-referenced objects (values)
 * will be garbage-collected in a <i>globally</i> least-recently-used manner, in response to memory
 * demand. Use of soft-values will result to comparisons using identity (==) equality instead of equals().
 */
object PublicKeyCache {
    private val cache = CacheBuilder.newBuilder()
            .maximumSize(128) // This is a guessed value.
            .softValues()
            .build(object : CacheLoader<PublicKey, PublicKey>() { // TODO: consider using key-hash as cache.key
                override fun load(key: PublicKey): PublicKey {
                    return key
                }
            })

    /**
     * Returns the value associated with {@code key} in this cache, first loading that value if
     * necessary. No observable state associated with this cache is modified until loading completes.
     */
    fun get(key: PublicKey) = cache.getUnchecked(key)
}
