/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher

class NonInvalidatingCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(bound: Long, loadFunction: (K) -> V) :
            this(buildCache(bound, loadFunction))

    private companion object {
        private fun <K, V> buildCache(bound: Long, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumSize(bound)
            return builder.build(NonInvalidatingCacheLoader(loadFunction))
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}

class NonInvalidatingWeightBasedCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {
    constructor (maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V) :
            this(buildCache(maxWeight, weigher, loadFunction))

    private companion object {
        private fun <K, V> buildCache(maxWeight: Long, weigher: Weigher<K, V>, loadFunction: (K) -> V): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().maximumWeight(maxWeight).weigher(weigher)
            return builder.build(NonInvalidatingCache.NonInvalidatingCacheLoader(loadFunction))
        }
    }
}