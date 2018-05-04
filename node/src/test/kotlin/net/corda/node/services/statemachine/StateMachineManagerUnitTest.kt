package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

private class TrivialLogic<out T>(private val result: T) : FlowLogic<T>() {
    override fun call() = result
}

@InitiatingFlow
private class ReceiveLogic<out T : Any>(private val peer: Party, type: KClass<T>) : FlowLogic<T>() {
    private val type = type.java
    @Suspendable
    override fun call() = initiateFlow(peer).receive(type).unwrap { it }
}

@InitiatingFlow
private class SendAndReceiveLogic<out T : Any>(private val peer: Party, private val ping: Any, type: KClass<T>) : FlowLogic<T>() {
    private val type = type.java
    @Suspendable
    override fun call() = initiateFlow(peer).sendAndReceive(type, ping).unwrap { it }
}

private class ChatMessage(val last: Boolean, val value: Any)
@InitiatingFlow
private class ChatLogic<out T : Any>(private val peer: Party, private val ping: Any) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        val channel = initiateFlow(peer)
        var value = ping
        while (true) {
            val message = channel.sendAndReceive<ChatMessage>(value).unwrap { it }
            value = message.value
            message.last && return uncheckedCast(value)
        }
    }
}

class StateMachineManagerUnitTest : StateMachineManagerHarness() {
    @Test
    fun `trivial flow`() {
        assertEquals(135, TrivialLogic(135).spawn().getOrThrow())
    }

    @Test
    fun `just receive`() {
        Channel().run {
            val future = ReceiveLogic(peer, Number::class).spawn()
            handshake { it == null }
            dataMessage(246)
            assertEquals(246 as Any, future.getOrThrow())
        }
    }

    @Test
    fun `send and receive`() {
        Channel().run {
            val future = SendAndReceiveLogic(peer, "ping", Integer::class).spawn()
            handshake { it == "ping" }
            dataMessage(357)
            assertEquals(357 as Any, future.getOrThrow())
        }
    }

    @Test
    fun chat() {
        Channel().run {
            val future = ChatLogic<Int>(peer, "ping").spawn()
            handshake { it == "ping" }
            dataMessage(ChatMessage(false, 555.0))
            expectData { it == 555.0 }
            dataMessage(ChatMessage(false, "woo"))
            expectData { it == "woo" }
            dataMessage(ChatMessage(true, 666))
            assertEquals(666 as Any, future.getOrThrow())
        }
    }
}
