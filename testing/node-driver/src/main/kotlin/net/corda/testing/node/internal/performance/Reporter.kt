package net.corda.testing.node.internal.performance

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import net.corda.testing.node.internal.ShutdownManager
import java.util.concurrent.TimeUnit
import javax.management.ObjectName
import kotlin.concurrent.thread

fun startReporter(shutdownManager: ShutdownManager, metricRegistry: MetricRegistry = MetricRegistry()): MetricRegistry {
    val jmxReporter = thread {
        JmxReporter.forRegistry(metricRegistry).inDomain("net.corda").createsObjectNamesWith { _, domain, name ->
            // Make the JMX hierarchy a bit better organised.
            val category = name.substringBefore('.').substringBeforeLast('/')
            val component = name.substringBefore('.').substringAfterLast('/', "")
            val subName = name.substringAfter('.', "")
            if (subName == "")
                ObjectName("$domain:name=$category${if (component.isNotEmpty()) ",component=$component," else ""}")
            else
                ObjectName("$domain:type=$category,${if (component.isNotEmpty()) "component=$component," else ""}name=$subName")

        }.build().start()
    }
    shutdownManager.registerShutdown { jmxReporter.interrupt() }
    val consoleReporter = thread {
        ConsoleReporter.forRegistry(metricRegistry).build().start(1, TimeUnit.SECONDS)
    }
    shutdownManager.registerShutdown { consoleReporter.interrupt() }
    return metricRegistry
}
