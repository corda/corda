package net.corda.nodeapi.internal.protonwrapper.engine

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import net.corda.coretesting.internal.rigorousMock
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import org.apache.qpid.proton.amqp.transport.Begin
import org.apache.qpid.proton.amqp.transport.Open
import org.apache.qpid.proton.engine.impl.TransportImpl
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class EventProcessorTest {
    @Test(timeout=300_000)
    fun `reject unacknowledged message on disconnect`() {
        val executor = DefaultEventLoop()
        val channel = channel(executor)

        val eventProcessor = EventProcessor(channel, false, ALICE_NAME.toString(), BOB_NAME.toString(), "username", "password")
        eventProcessor.processEventsAsync()

        val msg = SendableMessageImpl("test".toByteArray(), "topic", BOB_NAME.toString(), mock(), mapOf())
        eventProcessor.transportWriteMessage(msg)
        eventProcessor.processEventsAsync()

        // Open remote connection and session
        (eventProcessor.connection.transport as TransportImpl).also {
            Open().invoke(it, null, 0)
            Begin().invoke(it, null, 0)
        }
        eventProcessor.processEventsAsync()

        executor.execute { eventProcessor.close() }
        assertEquals(MessageStatus.Rejected, msg.onComplete.get(5, TimeUnit.SECONDS))
    }

    @Test(timeout=300_000)
    fun `reject unacknowledged message on disconnect without remote session being open`() {
        val executor = DefaultEventLoop()
        val channel = channel(executor)

        val eventProcessor = EventProcessor(channel, false, ALICE_NAME.toString(), BOB_NAME.toString(), "username", "password")
        eventProcessor.processEventsAsync()

        val msg = SendableMessageImpl("test".toByteArray(), "topic", BOB_NAME.toString(), mock(), mapOf())
        eventProcessor.transportWriteMessage(msg)
        eventProcessor.processEventsAsync()

        executor.execute { eventProcessor.close() }
        assertEquals(MessageStatus.Rejected, msg.onComplete.get(5, TimeUnit.SECONDS))
    }

    private fun channel(executor: EventLoop) = rigorousMock<Channel>().also {
        doReturn(executor).whenever(it).eventLoop()
        doReturn(mock<ChannelFuture>()).whenever(it).writeAndFlush(any())
        doReturn(true).whenever(it).isActive
        doReturn(mock<ChannelFuture>()).whenever(it).close()
        doReturn(null).whenever(it).localAddress()
        doReturn(null).whenever(it).remoteAddress()
    }
}