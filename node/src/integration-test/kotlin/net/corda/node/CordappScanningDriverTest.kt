package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CordappScanningDriverTest {
    @Test
    fun `sub-classed initiated flow pointing to the same initiating flow as its super-class`() {
        val user = User("u", "p", setOf(startFlow<ReceiveFlow>()))
        // The driver will automatically pick up the annotated flows below
        driver(DriverParameters(notarySpecs = emptyList())) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME)).transpose().getOrThrow()
            val initiatedFlowClass = CordaRPCClient(alice.rpcAddress)
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, bob.nodeInfo.singleIdentity())
                    .returnValue
            assertThat(initiatedFlowClass.getOrThrow()).isEqualTo(SendSubClassFlow::class.java.name)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class ReceiveFlow(private val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String = initiateFlow(otherParty).receive<String>().unwrap { it }
    }

    @InitiatedBy(ReceiveFlow::class)
    open class SendClassFlow(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherPartySession.send(javaClass.name)
    }

    @InitiatedBy(ReceiveFlow::class)
    class SendSubClassFlow(otherPartySession: FlowSession) : SendClassFlow(otherPartySession)
}
