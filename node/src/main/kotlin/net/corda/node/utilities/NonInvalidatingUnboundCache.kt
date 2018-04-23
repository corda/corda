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

import co.paralleluniverse.common.util.SameThreadExecutor
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener

class NonInvalidatingUnboundCache<K, V> private constructor(
        val cache: LoadingCache<K, V>
) : LoadingCache<K, V> by cache {

    constructor(loadFunction: (K) -> V, removalListener: RemovalListener<K, V> = RemovalListener { key, value, cause -> },
                keysToPreload: () -> Iterable<K> = { emptyList() }) :
            this(buildCache(loadFunction, removalListener, keysToPreload))

    private companion object {
        private fun <K, V> buildCache(loadFunction: (K) -> V, removalListener: RemovalListener<K, V>,
                                      keysToPreload: () -> Iterable<K>): LoadingCache<K, V> {
            val builder = Caffeine.newBuilder().removalListener(removalListener).executor(SameThreadExecutor.getExecutor())
            return builder.build(NonInvalidatingCacheLoader(loadFunction)).apply {
                getAll(keysToPreload())
            }
        }
    }

    // TODO look into overriding loadAll() if we ever use it
    private class NonInvalidatingCacheLoader<K, V>(val loadFunction: (K) -> V) : CacheLoader<K, V> {
        override fun reload(key: K, oldValue: V): V {
            throw IllegalStateException("Non invalidating cache refreshed")
        }

        override fun load(key: K) = loadFunction(key)
    }
}