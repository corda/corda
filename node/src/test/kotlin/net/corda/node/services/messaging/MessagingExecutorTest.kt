package net.corda.node.services.messaging

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.utilities.OpaqueBytes
import net.corda.node.VersionInfo
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.testing.node.internal.InMemoryMessage
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import kotlin.concurrent.thread

class MessagingExecutorTest {

    @Rule
    @JvmField
    val exit: ExpectedSystemExit = ExpectedSystemExit.none()

    private lateinit var messagingExecutor: MessagingExecutor

    @After
    fun after() {
        messagingExecutor.close()
    }

    @Test
    fun `System exit node if messaging is closed`() {
        exit.expectSystemExitWithStatus(1)

        val session = mock<ClientSession>()
        whenever(session.createMessage(any())).thenReturn(mock())

        val producer = mock<ClientProducer>()
        whenever(producer.send(any(), any(), any())).thenThrow(ActiveMQObjectClosedException())

        val resolver = mock<AddressToArtemisQueueResolver>()
        whenever(resolver.resolveTargetToArtemisQueue(any())).thenReturn("address")

        val metricRegistry = mock<MetricRegistry>()
        val sendLatencyMetric = mock<Timer>()
        whenever(metricRegistry.timer(any())).thenReturn(sendLatencyMetric)
        whenever(sendLatencyMetric.time()).thenReturn(mock())
        whenever(metricRegistry.histogram(any())).thenReturn(mock())

        messagingExecutor = MessagingExecutor(session, producer, VersionInfo.UNKNOWN, resolver, metricRegistry, "ourSenderUUID", 10, "legalName")
        messagingExecutor.start()

        thread {
            messagingExecutor.send(InMemoryMessage("topic", OpaqueBytes(ByteArray(10)), DeduplicationId("1")), mock())
        }.join()
    }

}