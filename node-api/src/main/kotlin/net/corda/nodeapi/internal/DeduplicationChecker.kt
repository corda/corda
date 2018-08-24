package net.corda.nodeapi.internal

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.buildNamed
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A class allowing the deduplication of a strictly incrementing sequence number.
 */
class DeduplicationChecker(cacheExpiry: Duration) {
    // dedupe identity -> watermark cache
    private val watermarkCache = Caffeine.newBuilder()
            .expireAfterAccess(cacheExpiry.toNanos(), TimeUnit.NANOSECONDS)
            .buildNamed("DeduplicationChecker_watermark", WatermarkCacheLoader)

    private object WatermarkCacheLoader : CacheLoader<Any, AtomicLong> {
        override fun load(key: Any) = AtomicLong(-1)
    }

    /**
     * @param identity the identity that generates the sequence numbers.
     * @param sequenceNumber the sequence number to check.
     * @return true if the message is unique, false if it's a duplicate.
     */
    fun checkDuplicateMessageId(identity: Any, sequenceNumber: Long): Boolean {
        return watermarkCache[identity]!!.getAndUpdate { maxOf(sequenceNumber, it) } >= sequenceNumber
    }
}

