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
    }

    private val loggingIntervalSec = conf.auditServiceConfiguration.loggingIntervalSec

    private val timedExecutor = Executors.newScheduledThreadPool(1)

    private val executionLock = ReentrantLock()

    private data class DirectionalStats(val successfulConnectionCount : ConcurrentMap<InetSocketAddress, AtomicLong> = ConcurrentHashMap<InetSocketAddress, AtomicLong>(),
                                        val failedConnectionCount : ConcurrentMap<InetSocketAddress, AtomicLong> = ConcurrentHashMap<InetSocketAddress, AtomicLong>(),
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

    override fun successfulConnectionEvent(address: InetSocketAddress, certificateSubject: String, msg: String, direction: RoutingDirection) {
        log.info(msg)
        withDirectionalStatsOf(direction) {
            successfulConnectionCount.getOrPut(address, ::AtomicLong).incrementAndGet()
        }
    }

    override fun failedConnectionEvent(address: InetSocketAddress, certificateSubject: String?, msg: String, direction: RoutingDirection) {
        log.warn(msg)
        withDirectionalStatsOf(direction) {
            failedConnectionCount.getOrPut(address, ::AtomicLong).incrementAndGet()
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
            RoutingDirection.OUTGOING -> this?.destinationLegalName ?: unknownAddress
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
        val loadAverageStr = if(loadAverage < 0) "N/A"  else "${loadAverage * 100}%"

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

        val state = stateRef.getAndSet(State())

        val dirStatsIn = state.directionalStatsMap[RoutingDirection.INBOUND]!!
        val dirStatsOut = state.directionalStatsMap[RoutingDirection.OUTGOING]!!

        val inAcceptedPackets = dirStatsIn.accepted.mapValues { it.value.first }
        val outAcceptedPackets = dirStatsOut.accepted.mapValues { it.value.first }

        val trafficTotalsStr =   "Traffic totals:\n" +
                                "\tSuccessful connection count: ${nf.format(dirStatsIn.successfulConnectionCount.sumValues())}(inbound), ${nf.format(dirStatsOut.successfulConnectionCount.sumValues())}(outgoing)\n" +
                                "\tFailed connection count: ${nf.format(dirStatsIn.failedConnectionCount.sumValues())}(inbound), ${nf.format(dirStatsOut.failedConnectionCount.sumValues())}(outgoing)\n" +
                                "\tPackets accepted count: ${nf.format(inAcceptedPackets.sumValues())}(inbound), " +
                                                        "${nf.format(outAcceptedPackets.sumValues())}(outgoing)\n" +
                                "\tBytes transmitted: ${nf.format(dirStatsIn.accepted.mapValues { it.value.second }.sumValues())}(inbound), " +
                                                   "${nf.format(dirStatsOut.accepted.mapValues { it.value.second }.sumValues())}(outgoing)\n" +
                                "\tPackets dropped count: ${nf.format(dirStatsIn.droppedPacketsCount.sumValues())}(inbound), ${nf.format(dirStatsOut.droppedPacketsCount.sumValues())}(outgoing)"

        val breakDownTrafficStr =   "Traffic breakdown:\n" +
                                           "\tSuccessful connections in:\n${dirStatsIn.successfulConnectionCount.prettyPrint(2, nf)}\n" +
                                           "\tSuccessful connections out:\n${dirStatsOut.successfulConnectionCount.prettyPrint(2, nf)}\n" +
                                           "\tFailed connections in:\n${dirStatsIn.failedConnectionCount.prettyPrint(2, nf)}\n" +
                                           "\tFailed connections out:\n${dirStatsOut.failedConnectionCount.prettyPrint(2, nf)}\n" +
                                           "\tAccepted packets in:\n${inAcceptedPackets.prettyPrint(2, nf)}\n" +
                                           "\tAccepted packets out:\n${outAcceptedPackets.prettyPrint(2, nf)}\n" +
                                           "\tDropped packets in:\n${dirStatsIn.droppedPacketsCount.prettyPrint(2, nf)}\n" +
                                           "\tDropped packets out:\n${dirStatsOut.droppedPacketsCount.prettyPrint(2, nf)}"

        return durationStr + "\n" + runtimeStr + "\n" + trafficTotalsStr + "\n" + breakDownTrafficStr
    }
}