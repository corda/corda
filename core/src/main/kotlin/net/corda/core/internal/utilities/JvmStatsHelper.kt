package net.corda.core.internal.utilities

import java.lang.StringBuilder
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage

/**
 * Helper method that will prepare JVM stats as a string.
 */
class JvmStatsHelper(dumpGcStats: Boolean = true, dumpMemoryStats: Boolean = true, dumpOsStats: Boolean = true) {
    companion object {
        private fun formatMemoryStats(memoryUsage: MemoryUsage?): String {
            return if (memoryUsage == null)
                "null"
            else
                "init: ${memoryUsage.init} committed: ${memoryUsage.committed} max: ${memoryUsage.max} used: ${memoryUsage.used}"
        }
    }

    private val memoryBean = if (dumpMemoryStats) ManagementFactory.getMemoryMXBean() else null
    private val memoryPoolBeans = if (dumpMemoryStats) ManagementFactory.getMemoryPoolMXBeans() else null
    private val gcBeans = if (dumpGcStats) ManagementFactory.getGarbageCollectorMXBeans() else null
    private val osBean = if (dumpOsStats) ManagementFactory.getOperatingSystemMXBean() else null

    private fun StringBuilder.tryAppend(label: String, block: () -> String) {
        this.append("$label: ")
        try {
            this.append(block())
        } catch (e: Exception) {
            this.append("Failed: ${e.message}")
        }
    }

    val stats: String
        get() {
            val message = StringBuilder()
            memoryBean?.apply {
                message.tryAppend("Memory Stats") {
                    "$objectPendingFinalizationCount objects pending finalization\n" +
                            "    Heap Memory: ${formatMemoryStats(heapMemoryUsage)}\n" +
                            "    Non-Heap Memory: ${formatMemoryStats(nonHeapMemoryUsage)}\n"
                }
            }
            memoryPoolBeans?.apply {
                sortedBy { it.name }.forEach {
                    message.tryAppend("MemoryPool ${it.name}") {
                        "${if (it.isValid) "valid" else "invalid"}\n" +
                                "    usage ${formatMemoryStats(it.usage)}\n" +
                                "    collection usage ${formatMemoryStats(it.collectionUsage)}\n" +
                                "    peak usage ${formatMemoryStats(it.peakUsage)}\n"
                    }
                }
            }
            gcBeans?.apply {
                forEach {
                    message.tryAppend("GC") { "${it.name}\n    collectionCount: ${it.collectionCount} collectionTime: ${it.collectionTime}\n" }
                }
            }
            osBean?.apply {
                message.tryAppend("OS stats") { "System load average: $systemLoadAverage Available processors: $availableProcessors" }
            }
            return message.toString()
        }
}