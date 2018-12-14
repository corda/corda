package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.NamedCacheFactory
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps a Caffeine cache and provides thread safe and database transaction aware invalidation.
 *
 * All access should be via [get] and [invalidate].  Data to be mutated should be changed at source (presumed to be a database)
 * followed by a call to [invalidate] the value associated with a key.  During periods of invalidity, the source will always be
 * consulted to resolve transaction visibility issues.  This is why invalidation should be infrequent, otherwise the pessimism
 * of the cache for invalidated values will result in few cache hits.
 */
class InfrequentlyMutatedCache<K : Any, V : Any>(name: String, cacheFactory: NamedCacheFactory) {
    /**
     * Retrieve the value associated with the given key in the cache, or use the function to retrieve the value (and potentially cache it).
     *
     * @param key The key to retrieve.
     * @param valueGetter A function to return the value for the key if the cache does not have it.
     */
    fun get(key: K, valueGetter: (K) -> V): V {
        val wrapper = backingCache.get(key) { key: K ->
            Wrapper.Valid(valueGetter(key))
        }
        return when(wrapper) {
            is Wrapper.Valid -> { wrapper.value }
            else -> { valueGetter(key) }
        }
    }

    /**
     * Inform the cache that the current value for the key may have been updated.  Subsequent calls to [get]
     * will not use the current cached value.  The point at which values start to be cached again will be
     * delayed until any open database transaction for the caller has been closed, to avoid callers to [get]
     * who do not have transaction visibility of the updated value from re-populating the cache with an incorrect value.
     */
    fun invalidate(key: K) {
        backingCache.asMap().compute(key) { key: K, value: Wrapper<V>? ->
            when(value) {
                is Wrapper.Valid -> { invalidate(key, Wrapper.Invalidated()) }
                is Wrapper.Invalidated -> { invalidate(key, value) }
                else -> { null }
            }
        }
    }

    private fun invalidate(key: K, value: Wrapper.Invalidated<V>): Wrapper.Invalidated<V> {
        val tx = contextTransactionOrNull
        value.invalidators.incrementAndGet()
        if (tx != null) {
            // When we close, we can't start using caching again until all simultaneously open transactions are closed.
            tx.onClose { tx.database.onAllOpenTransactionsClosed { decrementInvalidators(key, value) } }
        } else {
            decrementInvalidators(key, value)
        }
        return value
    }

    private fun decrementInvalidators(key: K, value: Wrapper.Invalidated<V>) {
        if(value.invalidators.decrementAndGet() == 0) {
            // Maybe we can replace the invalidated value with nothing, so it gets loaded next time.
            backingCache.asMap().compute(key) { key: K, currentValue: Wrapper<V>? ->
                if(currentValue === value && value.invalidators.get() == 0) {
                    null
                } else currentValue
            }
        }
    }

    private val backingCache = cacheFactory.buildNamed<K, Wrapper<V>>(Caffeine.newBuilder(), name)

    private sealed class Wrapper<V : Any> {
        abstract val value: V?

        class Invalidated<V : Any> : Wrapper<V>() {
            val invalidators = AtomicInteger(0)
            override val value: V? = null
        }

        class Valid<V : Any>(override val value: V) : Wrapper<V>()
    }
}