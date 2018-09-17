package net.corda.node.utilities

import com.codahale.metrics.MetricRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.buildNamed

/**
 * Allow passing metrics to caching implementations.
 */

fun <K, V> Caffeine<in K, in V>.buildNamed(registry: MetricRegistry, metricsPrefix: String): Cache<K, V> = this.buildNamed<K, V>(metricsPrefix)

fun <K, V> Caffeine<in K, in V>.buildNamed(registry: MetricRegistry, metricsPrefix: String, loader: CacheLoader<K, V>): LoadingCache<K, V> = this.buildNamed<K, V>(metricsPrefix, loader)
