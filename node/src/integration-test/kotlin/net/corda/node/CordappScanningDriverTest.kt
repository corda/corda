package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.core.utilities.unwrap
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CordappScanningDriverTest {
    @Test
    fun `sub-classed initiated flow pointing to the same initiating flow as its super-class`() {
        val user = User("u", "p", setOf(startFlowPermission<ReceiveFlow>()))
        // The driver will automatically pick up the annotated flows below
        driver {
            val (alice, bob) = listOf(
                    startNode(ALICE.name, rpcUsers = listOf(user)),
                    startNode(BOB.name)).transpose().getOrThrow()
            val initiatedFlowClass = alice.rpcClientToNode()
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, bob.nodeInfo.legalIdentity)
                    .returnValue
            assertThat(initiatedFlowClass.getOrThrow()).isEqualTo(SendSubClassFlow::class.java.name)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class ReceiveFlow(val otherParty: Party) :FlowLogic<String>() {
        @Suspendable
        override fun call(): String = receive<String>(otherParty).unwrap { it }
    }

    @InitiatedBy(ReceiveFlow::class)
    open class SendClassFlow(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, javaClass.name)
    }

    @InitiatedBy(ReceiveFlow::class)
    class SendSubClassFlow(otherParty: Party) : SendClassFlow(otherParty)
}
