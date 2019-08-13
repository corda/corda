package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.node.services.config.NotaryConfig
import java.util.*

class LRUCache<K, V>(size: Int) : LinkedHashMap<K, V>(size) {
    var cacheSize = size

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > cacheSize
    }
}

/**
 * A cache that tracks known unspent states, intended for use with any uniqueness provider used by a notary service.
 * When we notarise a transaction, we know that any states created by that transaction have not been spent yet.
 * Thus, we keep a cache of those states that allows us to fast track notarisation requests involving those states, by
 * avoiding unnecessary database access.
 * Not compatible with reference states.
 */
class UnspentStatesCache(metrics: MetricRegistry) {

    private val metricPrefix = UnspentStatesCache::class.simpleName
    private val cache = LRUCache<SecureHash, BitSet>(100_000)

    /** Tracks how many times we have an unspent cache hit and can do things the fast way. */
    private val unspentCacheHit = metrics.counter("$metricPrefix.UnspentCacheHit")
    /** Tracks how many times we have an unspent cache miss and must do things the slow way. */
    private val unspentCacheMiss = metrics.counter("$metricPrefix.UnspentCacheMiss")
    /** Tracks the distribution of the size of the unspent cache. */
    private val unspentCacheCount = metrics.histogram("$metricPrefix.UnspentCacheCount")

    private fun isKnownUnspent(stateRef: StateRef): Boolean {
        val unspentStates = cache[stateRef.txhash] ?: return false
        return !unspentStates[stateRef.index]
    }

    private fun markSpent(stateRef: StateRef) {
        val bs = cache[stateRef.txhash]
        // TODO: When a client notarises a transaction with a large index in the state ref, we allocate a lot of memory.
        bs?.set(stateRef.index)
        unspentCacheCount.update(cache.cacheSize)
    }

    /**
     * Marks states belonging to a specified transaction hash as unspent. Should be called
     * directly after committing a new transaction.
     * @param txHash SecureHash of newly created transaction to mark as unspent
     */
    fun markUnspent(txHash: SecureHash) {
        if (cache.containsKey(txHash)) {
            // Transaction already tracked.
            return
        }
        cache[txHash] = BitSet()
        unspentCacheCount.update(cache.cacheSize)
    }

    /**
     * Returns a Boolean indicating whether all of the states passed in are marked as unspent in the cache
     * @param toCheck A list of StateRefs to examine the cache for
     */
    fun isAllUnspent(toCheck: List<StateRef>): Boolean {
        val result = toCheck.all { isKnownUnspent(it) }
        if (result) {
            unspentCacheHit.inc()
        } else {
            unspentCacheMiss.inc()
        }
        return result
    }

    /**
     * Marks all of the passed in states as spent, in other words evicting them from the cache.
     * If a state is not found in the cache, nothing happens.
     *
     * @param toMark List of states to evict from the cache since we know they are spent
     */
    fun markAllAsSpent(toMark: List<StateRef>) {
        toMark.forEach { markSpent(it) }
    }
}