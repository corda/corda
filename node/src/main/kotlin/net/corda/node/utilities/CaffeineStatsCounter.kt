package net.corda.node.utilities

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.github.benmanes.caffeine.cache.stats.StatsCounter
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Helpers to export statistics to JMX and finally build the cache.
 */
fun <K, V> Caffeine<in K, in V>.buildNamed(registry: MetricRegistry, metricsPrefix: String): Cache<K, V> = this.addMetrics(registry, metricsPrefix).build<K, V>().addExtraMetrics(registry, metricsPrefix)

fun <K, V> Caffeine<in K, in V>.buildNamed(registry: MetricRegistry, metricsPrefix: String, loader: CacheLoader<K, V>): LoadingCache<K, V> = this.addMetrics(registry, metricsPrefix).build<K, V>(loader).addExtraMetrics(registry, metricsPrefix)

private fun <K, V> Caffeine<in K, in V>.addMetrics(registry: MetricRegistry, metricsPrefix: String): Caffeine<in K, in V> = this.recordStats(CaffeineStatsCounter.supplier(registry, "Caches/$metricsPrefix"))
private fun <K, V, C : Cache<K, V>> C.addExtraMetrics(registry: MetricRegistry, metricsPrefix: String): C = this.apply {
    registry.gauge("Caches/$metricsPrefix.size") {
        object : Gauge<Long> {
            override fun getValue(): Long {
                return this@apply.estimatedSize()
            }
        }
    }
    this@apply.policy().eviction().ifPresent {
        if (it.isWeighted) {
            registry.gauge("Caches/$metricsPrefix.maximum-weight") {
                object : Gauge<Long> {
                    override fun getValue(): Long {
                        return it.maximum
                    }
                }
            }
            registry.gauge("Caches/$metricsPrefix.weight") {
                object : Gauge<Long> {
                    override fun getValue(): Long {
                        return it.weightedSize().asLong
                    }
                }
            }
            registry.gauge("Caches/$metricsPrefix.weightPercent") {
                object : Gauge<Long> {
                    override fun getValue(): Long {
                        return minOf((it.weightedSize().asLong * 100L) / it.maximum, 100L)
                    }
                }
            }
        } else {
            registry.gauge("Caches/$metricsPrefix.maximum-size") {
                object : Gauge<Long> {
                    override fun getValue(): Long {
                        return it.maximum
                    }
                }
            }
            registry.gauge("Caches/$metricsPrefix.sizePercent") {
                object : Gauge<Long> {
                    override fun getValue(): Long {
                        return minOf((this@apply.estimatedSize() * 100L) / it.maximum, 100L)
                    }
                }
            }
        }
    }
}


/**
 * A {@link StatsCounter} backed by Dropwizard Metrics.
 */
class CaffeineStatsCounter : StatsCounter {
    private val hitCount: Counter
    private val missCount: Counter
    private val loadSuccessCount: Counter
    private val loadFailureCount: Counter
    private val totalLoadTime: Timer
    private val evictionCount: Counter
    private val evictionWeight: Counter

    companion object {
        /**
         * Creates a supplier of instances for use by a single cache.
         *
         * @param registry the registry of metric instances
         * @param metricsPrefix the prefix name for the metrics
         */
        fun supplier(registry: MetricRegistry, metricsPrefix: String): Supplier<StatsCounter> {
            return Supplier<StatsCounter> { CaffeineStatsCounter(registry, metricsPrefix) }
        }
    }

    private constructor(registry: MetricRegistry, metricsPrefix: String) {
        hitCount = registry.counter("$metricsPrefix.hits")
        missCount = registry.counter("$metricsPrefix.misses")
        totalLoadTime = registry.timer("$metricsPrefix.loads")
        loadSuccessCount = registry.counter("$metricsPrefix.loads-success")
        loadFailureCount = registry.counter("$metricsPrefix.loads-failure")
        evictionCount = registry.counter("$metricsPrefix.evictions")
        evictionWeight = registry.counter("$metricsPrefix.evictions-weight")
    }

    override fun recordHits(count: Int) {
        hitCount.inc(count.toLong())
    }

    override fun recordMisses(count: Int) {
        missCount.inc(count.toLong())
    }

    override fun recordLoadSuccess(loadTime: Long) {
        loadSuccessCount.inc()
        totalLoadTime.update(loadTime, TimeUnit.NANOSECONDS)
    }

    override fun recordLoadFailure(loadTime: Long) {
        loadFailureCount.inc()
        totalLoadTime.update(loadTime, TimeUnit.NANOSECONDS)
    }

    override fun recordEviction() {
        // This method is scheduled for removal in version 3.0 in favor of recordEviction(weight)
        recordEviction(1)
    }

    override fun recordEviction(weight: Int) {
        evictionCount.inc()
        evictionWeight.inc(weight.toLong())
    }

    override fun snapshot(): CacheStats {
        return CacheStats(
                hitCount.getCount(),
                missCount.getCount(),
                loadSuccessCount.getCount(),
                loadFailureCount.getCount(),
                totalLoadTime.getCount(),
                evictionCount.getCount(),
                evictionWeight.getCount())
    }

    override fun toString(): String {
        return snapshot().toString()
    }
}