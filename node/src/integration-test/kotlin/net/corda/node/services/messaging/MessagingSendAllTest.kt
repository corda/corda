package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.Destination
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

class MessagingSendAllTest {

    @Test(timeout=300_000)
    fun `flow can exchange messages with multiple sessions to the same party in parallel`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME)
            ).transpose().getOrThrow()

            val bobIdentity = bob.nodeInfo.singleIdentity()
            val messages = listOf(
                    bobIdentity to "hey bob 1",
                    bobIdentity to "hey bob 2"
            )

            alice.rpc.startFlow(::SenderFlow, messages).returnValue.getOrThrow()
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SenderFlow(private val parties: List<Pair<Destination, String>>): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            val messagesPerSession = parties.toList().map { (party, messageType) ->
                val session = initiateFlow(party)
                Pair(session, messageType)
            }.toMap()

            sendAllMap(messagesPerSession)
            val messages = receiveAll(String::class.java, messagesPerSession.keys.toList())

            messages.map { it.unwrap { payload -> assertEquals("pong", payload) } }

            return "ok"
        }
    }

    @InitiatedBy(SenderFlow::class)
    class RecipientFlow(private val otherPartySession: FlowSession): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            otherPartySession.receive<String>().unwrap { it }
            otherPartySession.send("pong")

            return "ok"
        }
    }

}