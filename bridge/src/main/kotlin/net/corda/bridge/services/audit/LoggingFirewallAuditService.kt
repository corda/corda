package net.corda.bridge.services.audit

import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.RoutingDirection
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.protonwrapper.messages.ApplicationMessage
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

class LoggingFirewallAuditService(val conf: FirewallConfiguration,
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : FirewallAuditService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()

        private fun <K> Map<K, AtomicLong>.sumValues(): Long {
            return this.values.map { it.get() }.sum()
        }

        private fun <K> Map<K, AtomicLong>.prettyPrint(tabsCount: Int, nf: NumberFormat): String {
            val leftPad = "\t".repeat(tabsCount)
            return entries.joinToString(separator = "\n") { entry -> leftPad + entry.key.toString() + " -> " + nf.format(entry.value.get())}
        }

        private fun <K> Map<K, AtomicLong>.whenNonEmptyPrint(block: Map<K, AtomicLong>.() -> String): String {
            return if(this.isEmpty()) {
                ""
            } else {
                block()
            }
        }
    }

    private val loggingIntervalSec = conf.auditServiceConfiguration.loggingIntervalSec

    private val timedExecutor = Executors.newScheduledThreadPool(1)

    private val executionLock = ReentrantLock()

    private data class DirectionalStats(val successfulConnectionCount : ConcurrentMap<InetSocketAddress, AtomicLong> = ConcurrentHashMap<InetSocketAddress, AtomicLong>(),
                                        val failedConnectionCount : ConcurrentMap<InetSocketAddress, AtomicLong> = ConcurrentHashMap<InetSocketAddress, AtomicLong>(),
                                        val activeConnectionCount : ConcurrentMap<InetSocketAddress, AtomicLong> =  ConcurrentHashMap<InetSocketAddress, AtomicLong>(),
                                        val accepted : ConcurrentMap<String, Pair<AtomicLong, AtomicLong>> = ConcurrentHashMap<String, Pair<AtomicLong, AtomicLong>>(),
                                        val droppedPacketsCount : ConcurrentMap<String, AtomicLong> = ConcurrentHashMap<String, AtomicLong>())

    private data class State(val directionalStatsMap: EnumMap<RoutingDirection, DirectionalStats> = forEveryRoutingDirection()
    ) {
        companion object {
            private fun forEveryRoutingDirection() : EnumMap<RoutingDirection, DirectionalStats> {
                val map = RoutingDirection.values().map { it to DirectionalStats() }.toMap()
                return EnumMap(map)
            }
        }

        fun reset() : State {
            val newStatsMap = forEveryRoutingDirection()
            directionalStatsMap.entries.forEach {
                val currentStats = it.value
                val newStats = DirectionalStats().apply {
                    activeConnectionCount.putAll(currentStats.activeConnectionCount)
                }
                newStatsMap[it.key] = newStats
            }
            return State(newStatsMap)
        }
    }

    private val stateRef = AtomicReference(State())

    override fun start() {
        stateHelper.active = true
        timedExecutor.scheduleAtFixedRate(::logStatsAndReset, loggingIntervalSec, loggingIntervalSec, TimeUnit.SECONDS)
    }

    override fun stop() {
        stateHelper.active = false
        timedExecutor.shutdown()
    }

    private fun logWithSuppression(address: InetSocketAddress, certificateSubject: String?, msg: String, direction: RoutingDirection, defaultLogger: (String) -> Unit) {
        val logStr = "direction: $direction sourceIP: $address certificateSubject: $certificateSubject - $msg"
        if (address.hostString in conf.silencedIPs) {
            log.trace(logStr)
        } else {
            defaultLogger(logStr)
        }
    }

    override fun successfulConnectionEvent(address: InetSocketAddress, certificateSubject: String, msg: String, direction: RoutingDirection) {
        logWithSuppression(address, certificateSubject, msg, direction) { log.warn(it) }
        withDirectionalStatsOf(direction) {
            successfulConnectionCount.getOrPut(address, ::AtomicLong).incrementAndGet()
            activeConnectionCount.getOrPut(address, ::AtomicLong).incrementAndGet()
        }
    }

    override fun failedConnectionEvent(address: InetSocketAddress, certificateSubject: String?, msg: String, direction: RoutingDirection) {
        logWithSuppression(address, certificateSubject, msg, direction) { log.info(it) }
        withDirectionalStatsOf(direction) {
            failedConnectionCount.getOrPut(address, ::AtomicLong).incrementAndGet()
            activeConnectionCount.getOrPut(address, ::AtomicLong).decrementAndGet()
        }
    }

    private fun withDirectionalStatsOf(direction: RoutingDirection, block: DirectionalStats.() -> Unit) {
        val directionalStats = stateRef.get().directionalStatsMap[direction]
        if(directionalStats == null) {
            log.warn("Unknown direction: $direction")
        } else {
            directionalStats.block()
        }
    }

    private fun ApplicationMessage?.address(direction: RoutingDirection) : String {
        val unknownAddress = "Unknown address"
        return when(direction) {
            RoutingDirection.OUTBOUND -> this?.destinationLegalName ?: unknownAddress
            RoutingDirection.INBOUND -> if (this is ReceivedMessage) {
                this.sourceLegalName
            } else {
                unknownAddress
            }
        }
    }

    override fun packetDropEvent(packet: ApplicationMessage?, msg: String, direction: RoutingDirection) {
        log.info("$direction : $msg")

        withDirectionalStatsOf(direction) {
            droppedPacketsCount.getOrPut(packet.address(direction), ::AtomicLong).incrementAndGet()
        }
    }

    override fun packetAcceptedEvent(packet: ApplicationMessage, direction: RoutingDirection) {

        val address = packet.address(direction)
        log.trace { "$direction: Address: $address, uuid: ${packet.applicationProperties["_AMQ_DUPL_ID"]}" }

        withDirectionalStatsOf(direction) {
            val pair = accepted.getOrPut(address) { Pair(AtomicLong(), AtomicLong())}
            pair.first.incrementAndGet()
            pair.second.addAndGet(packet.payload.size.toLong())
        }
    }

    override fun statusChangeEvent(msg: String) {
        log.info(msg)
    }

    private fun logStatsAndReset() {
        if(executionLock.tryLock()) {
            try {
                val statsStr = prepareStatsAndReset()
                log.info(statsStr)
            } catch (ex: Exception) {
                // This is running by the scheduled execution service and must not fail
                log.error("Unexpected exception when logging stats", ex)
            } finally {
                executionLock.unlock()
            }
        } else {
            log.warn("Skipping stats logging as it is already running in a different thread.")
        }
    }

    internal fun prepareStatsAndReset() : String {

        fun Long.toMB(): Long  = this / (1024 * 1024)

        val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
        val loadAverage = operatingSystemMXBean.systemLoadAverage
        val df = DecimalFormat("#.##")
        val loadAverageStr = if(loadAverage < 0) "N/A" else "${df.format(loadAverage * 100)}%"

        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory().toMB()
        val totalMemory = runtime.totalMemory().toMB()
        val maxMemory = runtime.maxMemory().toMB()

        val nf = NumberFormat.getNumberInstance()

        val durationStr = "During last ${Duration.ofSeconds(loggingIntervalSec)} stats were as follows:"

        val runtimeStr = "Load average: $loadAverageStr\n" +
                                 "Memory:\n\tFree: ${nf.format(freeMemory)} MB" +
                                        "\n\tTotal: ${nf.format(totalMemory)} MB" +
                                        "\n\tMax: ${nf.format(maxMemory)} MB"

        val state = stateRef.getAndSet(stateRef.get().reset())

        val dirStatsIn = state.directionalStatsMap[RoutingDirection.INBOUND]!!
        val dirStatsOut = state.directionalStatsMap[RoutingDirection.OUTBOUND]!!

        val inAcceptedPackets = dirStatsIn.accepted.mapValues { it.value.first }
        val outAcceptedPackets = dirStatsOut.accepted.mapValues { it.value.first }

        val trafficTotalsStr =   "Traffic totals:\n" +
                                "\tSuccessful connection count: ${nf.format(dirStatsIn.successfulConnectionCount.sumValues())}(inbound), ${nf.format(dirStatsOut.successfulConnectionCount.sumValues())}(outbound)\n" +
                                "\tFailed connection count: ${nf.format(dirStatsIn.failedConnectionCount.sumValues())}(inbound), ${nf.format(dirStatsOut.failedConnectionCount.sumValues())}(outbound)\n" +
                                "\tActive connection count: ${nf.format(dirStatsIn.activeConnectionCount.sumValues())}(inbound), ${nf.format(dirStatsOut.activeConnectionCount.sumValues())}(outbound)\n" +
                                "\tPackets accepted count: ${nf.format(inAcceptedPackets.sumValues())}(inbound), " +
                                                        "${nf.format(outAcceptedPackets.sumValues())}(outbound)\n" +
                                "\tBytes transmitted: ${nf.format(dirStatsIn.accepted.mapValues { it.value.second }.sumValues())}(inbound), " +
                                                   "${nf.format(dirStatsOut.accepted.mapValues { it.value.second }.sumValues())}(outbound)\n" +
                                "\tPackets dropped count: ${nf.format(dirStatsIn.droppedPacketsCount.sumValues())}(inbound), ${nf.format(dirStatsOut.droppedPacketsCount.sumValues())}(outbound)"

        val breakDownTrafficStr = "Traffic breakdown:\n" +
                dirStatsIn.activeConnectionCount.whenNonEmptyPrint { "\tLive connections in:\n${prettyPrint(2, nf)}\n" } +
                dirStatsOut.activeConnectionCount.whenNonEmptyPrint { "\tLive connections out:\n${prettyPrint(2, nf)}\n" } +
                dirStatsIn.successfulConnectionCount.whenNonEmptyPrint { "\tSuccessful connections in:\n${prettyPrint(2, nf)}\n" } +
                dirStatsOut.successfulConnectionCount.whenNonEmptyPrint { "\tSuccessful connections out:\n${prettyPrint(2, nf)}\n" } +
                dirStatsIn.failedConnectionCount.whenNonEmptyPrint { "\tFailed connections in:\n${prettyPrint(2, nf)}\n" } +
                dirStatsOut.failedConnectionCount.whenNonEmptyPrint { "\tFailed connections out:\n${prettyPrint(2, nf)}\n" } +
                inAcceptedPackets.whenNonEmptyPrint { "\tAccepted packets in:\n${prettyPrint(2, nf)}\n" } +
                outAcceptedPackets.whenNonEmptyPrint { "\tAccepted packets out:\n${prettyPrint(2, nf)}\n" } +
                dirStatsIn.droppedPacketsCount.whenNonEmptyPrint { "\tDropped packets in:\n${prettyPrint(2, nf)}\n" } +
                dirStatsOut.droppedPacketsCount.whenNonEmptyPrint { "\tDropped packets out:\n${prettyPrint(2, nf)}" }

        return durationStr + "\n" + runtimeStr + "\n" + trafficTotalsStr + "\n" + breakDownTrafficStr
    }
}