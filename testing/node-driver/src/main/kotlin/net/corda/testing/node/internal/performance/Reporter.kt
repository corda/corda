package net.corda.testing.node.internal.performance

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.JmxReporter
import com.codahale.metrics.MetricRegistry
import net.corda.testing.node.internal.ShutdownManager
import java.util.concurrent.TimeUnit
import javax.management.ObjectName
import kotlin.concurrent.thread

fun startReporter(shutdownManager: ShutdownManager, metricRegistry: MetricRegistry = MetricRegistry()): MetricRegistry {
    val jmxReporter = thread {
        JmxReporter.forRegistry(metricRegistry).inDomain("net.corda").createsObjectNamesWith { _, domain, name ->
            // Make the JMX hierarchy a bit better organised.
            val category = name.substringBefore('.')
            val subName = name.substringAfter('.', "")
            if (subName == "")
                ObjectName("$domain:name=$category")
            else
                ObjectName("$domain:type=$category,name=$subName")
        }.build().start()
    }
    shutdownManager.registerShutdown { jmxReporter.interrupt() }
    val consoleReporter = thread {
        ConsoleReporter.forRegistry(metricRegistry).build().start(1, TimeUnit.SECONDS)
    }
    shutdownManager.registerShutdown { consoleReporter.interrupt() }
    return metricRegistry
}
