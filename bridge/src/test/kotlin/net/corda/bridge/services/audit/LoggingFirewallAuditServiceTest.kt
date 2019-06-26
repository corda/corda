package net.corda.bridge.services.audit

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.bridge.services.api.AuditServiceConfiguration
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.RoutingDirection
import net.corda.nodeapi.internal.protonwrapper.messages.ApplicationMessage
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class LoggingFirewallAuditServiceTest {

    private val instance : LoggingFirewallAuditService

    init {
        val auditServiceConfiguration = mock<AuditServiceConfiguration>()
        val firewallConfiguration = mock<FirewallConfiguration>()
        whenever(firewallConfiguration.auditServiceConfiguration).then { auditServiceConfiguration }
        whenever(auditServiceConfiguration.loggingIntervalSec).then { 50L }
        instance = LoggingFirewallAuditService(firewallConfiguration)
    }

    @After
    fun tearDown() {
        instance.stop()
    }

    @Test
    fun testActiveConnectionsTracking() {

        fun triggerActivity() {
            val t1 = thread {
                (1..20).forEach { instance.successfulConnectionEvent(it.toAddress(), "connectionTrackingTest", "connectionSuccess", it.toDirection()) }
                (1..10).forEach { instance.terminatedConnectionEvent(it.toAddress(), "connectionTrackingTest", "connectionClose", it.toDirection()) }
            }

            val t2 = thread {
                (1..10).forEach { instance.successfulConnectionEvent(it.toAddress(), "connectionTrackingTest", "connectionSuccess", it.toDirection()) }
                (1..5).forEach { instance.terminatedConnectionEvent(it.toAddress(), "connectionTrackingTest", "connectionClose", it.toDirection()) }
            }

            t1.join()
            t2.join()
        }

        triggerActivity()

        with(instance.prepareStatsAndReset()) {
            assertThat(this, containsSubstring("Successful connection count: 9(inbound), 21(outbound)"))
            assertThat(this, containsSubstring("Failed connection count: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Active connection count: 5(inbound), 10(outbound)"))
            assertThat(this, containsSubstring("Packets accepted count: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Bytes transmitted: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Packets dropped count: 0(inbound), 0(outbound)"))
        }

        triggerActivity()

        // Verify that after another round of connection shuffling, the temporary stats are properly reset while active connections are only updated correctly
        with(instance.prepareStatsAndReset()) {
            assertThat(this, containsSubstring("Successful connection count: 9(inbound), 21(outbound)"))
            assertThat(this, containsSubstring("Failed connection count: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Active connection count: 10(inbound), 20(outbound)"))
            assertThat(this, containsSubstring("Packets accepted count: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Bytes transmitted: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Packets dropped count: 0(inbound), 0(outbound)"))
        }
    }

    @Test
    fun testStatsOutput() {

        val failedConnCount = 7
        (1..failedConnCount).forEach { instance.terminatedConnectionEvent(it.toAddress(), null, "test", it.toDirection()) }
        val succConnCount = 40
        (1..succConnCount).forEach { instance.successfulConnectionEvent(it.toAddress(), "test2", "test", it.toDirection()) }

        val packetDropCount = 20
        // Multi-threaded operation to make sure the state data structure is sound in that regard
        (1..packetDropCount).toList().parallelStream().forEach { val direction = it.toDirection()
            instance.packetDropEvent(it.createMessage(direction), "test3", direction) }

        val packetAcceptedCount = 9000
        // Multi-threaded operation to make sure the state data structure is sound in that regard
        (1..packetAcceptedCount).toList().parallelStream().forEach {
            val direction = it.toDirection()
            instance.packetAcceptedEvent(it.createMessage(direction), direction)
        }

       with(instance.prepareStatsAndReset()) {
           assertThat(this, containsSubstring("Successful connection count: 13(inbound), 27(outbound)"))
           assertThat(this, containsSubstring("Failed connection count: 2(inbound), 5(outbound)"))
           assertThat(this, containsSubstring("Active connection count: 13(inbound), 27(outbound)"))
           assertThat(this, containsSubstring("Packets accepted count: 3,000(inbound), 6,000(outbound)"))
           assertThat(this, containsSubstring("Bytes transmitted: 60,000(inbound), 120,000(outbound)"))
           assertThat(this, containsSubstring("Packets dropped count: 6(inbound), 14(outbound)"))
           assertThat(this, containsSubstring("Failed connections:"))
           assertThat(this, containsSubstring("Server4:10001 -> in: 1 out: 0"))
       }

        // Ensure reset stats
        with(instance.prepareStatsAndReset()) {
            assertThat(this, containsSubstring("Successful connection count: 0"))
            assertThat(this, containsSubstring("Active connection count: 13(inbound), 27(outbound)"))
            assertThat(this, containsSubstring("Packets dropped count: 0(inbound), 0(outbound)"))
            assertThat(this, containsSubstring("Failed connections:").not())
            assertThat(this, containsSubstring("Accepted packets:").not())
        }
    }

    private fun Int.createMessage(direction : RoutingDirection) : ApplicationMessage {
        val byteArray = ByteArray(20)
        val partyName = "Party" + ((this % 4) + 1)
        return when(direction) {
            RoutingDirection.INBOUND -> {
                val msg = mock<ReceivedMessage>()
                whenever(msg.payload).then { byteArray }
                whenever(msg.sourceLegalName).then { partyName }
                msg
            }
            RoutingDirection.OUTBOUND -> {
                val msg = mock<SendableMessage>()
                whenever(msg.payload).then { byteArray }
                whenever(msg.destinationLegalName).then { partyName }
                msg
            }
        }
    }

    private fun Int.toAddress(): InetSocketAddress {
        val hostname = "Server" + ((this % 5) + 1)
        return InetSocketAddress(hostname, 10001)
    }

    private fun Int.toDirection() : RoutingDirection {
        return if (this % 3 == 0) {
            RoutingDirection.INBOUND
        } else {
            RoutingDirection.OUTBOUND
        }
    }
}

/*
During last PT50S stats were as follows:
Load average: 455.96%
Memory:
	Free: 236 MB
	Total: 379 MB
	Max: 3,641 MB
Traffic totals:
	Successful connection count: 13(inbound), 27(outbound)
	Failed connection count: 2(inbound), 5(outbound)
	Active connection count: 11(inbound), 22(outbound)
	Packets accepted count: 3,000(inbound), 6,000(outbound)
	Bytes transmitted: 60,000(inbound), 120,000(outbound)
	Packets dropped count: 6(inbound), 14(outbound)
Traffic breakdown:
	Live connections:
		Server5:10001 -> in: 3 out: 4
		Server4:10001 -> in: 2 out: 5
		Server3:10001 -> in: 2 out: 4
		Server2:10001 -> in: 2 out: 4
		Server1:10001 -> in: 2 out: 5
	Successful connections:
		Server5:10001 -> in: 3 out: 5
		Server4:10001 -> in: 3 out: 5
		Server3:10001 -> in: 2 out: 6
		Server2:10001 -> in: 3 out: 5
		Server1:10001 -> in: 2 out: 6
	Failed connections:
		Server4:10001 -> in: 1 out: 0
		Server2:10001 -> in: 1 out: 1
		Server5:10001 -> in: 0 out: 1
		Server3:10001 -> in: 0 out: 2
		Server1:10001 -> in: 0 out: 1
	Accepted packets:
		Party1 -> in: 750 out: 1,500
		Party2 -> in: 750 out: 1,500
		Party3 -> in: 750 out: 1,500
		Party4 -> in: 750 out: 1,500
	Dropped packets:
		Party1 -> in: 1 out: 4
		Party2 -> in: 1 out: 4
		Party3 -> in: 2 out: 3
		Party4 -> in: 2 out: 3
 */