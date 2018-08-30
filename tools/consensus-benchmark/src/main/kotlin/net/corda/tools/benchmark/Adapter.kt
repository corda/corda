package net.corda.tools.benchmark

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.graphite.PickledGraphite
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class Adapter {
    /**
     * Submits a transaction to the consensus cluster, returning a future that completes once the transaction
     * has been processed.
     */
    abstract fun submitTransaction(tx: ByteArray): Future<Unit>

    protected val metrics: MetricRegistry
    abstract val metricPrefix: String

    init {
        metrics = createMetricsRegistry()
    }

    private fun createMetricsRegistry(): MetricRegistry {
        val hostName = InetAddress.getLocalHost().hostName.replace(".", "_")
        val pickledGraphite = PickledGraphite(
                InetSocketAddress("performance-metrics.northeurope.cloudapp.azure.com", 2004)
        )
        val metrics = MetricRegistry()
        GraphiteReporter.forRegistry(metrics)
                .prefixedWith("corda.$hostName")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(pickledGraphite)
                .start(10, TimeUnit.SECONDS)
        return metrics
    }
}
