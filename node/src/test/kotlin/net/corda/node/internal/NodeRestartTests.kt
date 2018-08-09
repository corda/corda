package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class NodeRestartTests {
    private val mockNet = InternalMockNetwork(threadPerNode = true, autoVisibleNodes = false, notarySpecs = emptyList())

    @After
    fun cleanUp() {
        mockNet.close()
    }

    @Test
    fun `restart with no network map cache update`() {
        val alice = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        val bob = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME))
        bob.registerInitiatedFlow(Responder::class.java)
        alice.services.networkMapCache.addNode(bob.info)
        bob.services.networkMapCache.addNode(alice.info)
        val alice2 = mockNet.restartNode(alice)
        val result = alice2.services.startFlow(Initiator(bob.info.singleIdentity())).resultFuture.getOrThrow()
        assertThat(result).isEqualTo(123)
    }

    @InitiatingFlow
    private class Initiator(private val otherSide: Party) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int = initiateFlow(otherSide).receive<Int>().unwrap { it }
    }

    @InitiatedBy(Initiator::class)
    private class Responder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherSide.send(123)
    }
}
