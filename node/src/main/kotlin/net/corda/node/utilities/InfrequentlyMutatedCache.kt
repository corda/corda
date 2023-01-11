package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.VisibleForTesting
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps a Caffeine cache and provides thread safe and database transaction aware invalidation.
 *
 * All access should be via [get], [getIfPresent] or [invalidate].  Data to be mutated should be changed at source
 * (presumed to be a database) followed by a call to [invalidate] the value associated with a key.
 * During periods of invalidity, the source will always be consulted to resolve transaction visibility issues.
 * This is why invalidation should be infrequent, otherwise the pessimism of the cache for invalidated values will result in few cache hits.
 */
class InfrequentlyMutatedCache<K : Any, V : Any>(name: String, cacheFactory: NamedCacheFactory) {
    /**
     * Retrieve the value associated with the given key in the cache, or use the function to retrieve the value (and potentially cache it).
     *
     * @param key The key to retrieve.
     * @param valueGetter A function to return the value for the key if the cache does not have it.
     */
    fun get(key: K, valueGetter: (K) -> V): V {
        val wrapper = backingCache.get(key) { k: K ->
            currentlyInvalid[k] ?: Wrapper.Valid(valueGetter(k))
        }
        return when(wrapper) {
            is Wrapper.Valid -> { wrapper.value }
            else -> { valueGetter(key) }
        }
    }

    /**
     * Retrieve the value associated with the given key in the cache, or null if not cached.
     *
     * @param key The key to retrieve.
     */
    fun getIfPresent(key: K): V? {
        val wrapper = backingCache.get(key) {
            null
        }
        return when (wrapper) {
            is Wrapper.Valid -> {
                wrapper.value
            }
            else -> null
        }
    }

    /**
     * Inform the cache that the current value for the key may have been updated.  Subsequent calls to [get]
     * will not use the current cached value.  The point at which values start to be cached again will be
     * delayed until any open database transaction for the caller has been closed, to avoid callers to [get]
     * who do not have transaction visibility of the updated value from re-populating the cache with an incorrect value.
     */
    fun invalidate(key: K) {
        backingCache.asMap().compute(key) { k: K, value: Wrapper<V>? ->
            when(value) {
                is Wrapper.Invalidated -> {
                    invalidate(k, value)
                }
                else -> {
                    invalidate(k, currentlyInvalid[k] ?: Wrapper.Invalidated())
                }
            }
        }
    }

    @VisibleForTesting
    internal fun flushCache() {
        backingCache.invalidateAll()
    }

    private fun invalidate(key: K, value: Wrapper.Invalidated<V>): Wrapper.Invalidated<V>? {
        val tx = contextTransactionOrNull
        value.invalidators.incrementAndGet()
        currentlyInvalid[key] = value
        if (tx != null) {
            // When we close, we can't start using caching again until all simultaneously open transactions are closed.
            tx.onClose { tx.database.onAllOpenTransactionsClosed { decrementInvalidators(key, value) } }
        } else {
            if (value.invalidators.decrementAndGet() == 0) {
                currentlyInvalid.remove(key)
                return null
            }
        }
        return value
    }

    private fun decrementInvalidators(key: K, value: Wrapper.Invalidated<V>) {
        if(value.invalidators.decrementAndGet() == 0) {
            // Maybe we can replace the invalidated value with nothing, so it gets loaded next time.
            backingCache.asMap().compute(key) { computeKey: K, currentValue: Wrapper<V>? ->
                if(currentValue === value && value.invalidators.get() == 0) {
                    currentlyInvalid.remove(computeKey)
                    null
                } else currentValue
            }
        }
    }

    private val backingCache = cacheFactory.buildNamed<K, Wrapper<V>>(Caffeine.newBuilder(), name)

    // This protects against the cache purging something that is marked as invalid and thus we "forget" it shouldn't be cached.
    private val currentlyInvalid = ConcurrentHashMap<K, Wrapper.Invalidated<V>>()

    private sealed class Wrapper<V : Any> {
        class Invalidated<V : Any> : Wrapper<V>() {
            val invalidators = AtomicInteger(0)
        }

        class Valid<V : Any>(val value: V) : Wrapper<V>()
    }
}