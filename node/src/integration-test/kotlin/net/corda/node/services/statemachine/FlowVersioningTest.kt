package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.Futures
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.getOrThrow
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.unwrap
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FlowVersioningTest : NodeBasedTest() {
    @Test
    fun `core flows receive platform version of initiator`() {
        val (alice, bob) = Futures.allAsList(
                startNode(ALICE.name, platformVersion = 2),
                startNode(BOB.name, platformVersion = 3)).getOrThrow()
        bob.installCoreFlow(ClientFlow::class, ::SendBackPlatformVersionFlow)
        val resultFuture = alice.services.startFlow(ClientFlow(bob.info.legalIdentity)).resultFuture
        assertThat(resultFuture.getOrThrow()).isEqualTo(2)
    }

    private open class ClientFlow(val otherParty: Party) : FlowLogic<Any>() {
        @Suspendable
        override fun call(): Any {
            return sendAndReceive<Any>(otherParty, "This is ignored. We only send to kick off the flow on the other side").unwrap { it }
        }
    }

    private open class SendBackPlatformVersionFlow(val otherParty: Party, val otherPartysPlatformVersion: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, otherPartysPlatformVersion)
    }

}