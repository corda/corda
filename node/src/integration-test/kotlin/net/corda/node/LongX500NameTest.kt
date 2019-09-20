package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
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

class LongX500NameTest {

    companion object {
        val LONG_X500_NAME = CordaX500Name(
                commonName = "AB123456789012345678901234567890123456789012345678901234567890",
                organisationUnit = "AB123456789012345678901234567890123456789012345678901234567890",
                organisation = "Bob Plc",
                locality = "AB123456789012345678901234567890123456789012345678901234567890",
                state = "AB123456789012345678901234567890123456789012345678901234567890",
                country = "IT")
    }

    @Test
    fun `corda supports nodes with a long x500 name`() {
        val user = User("u", "p", setOf(startFlow<ReceiveFlow>()))
        // The driver will automatically pick up the annotated flows below
        driver(DriverParameters(notarySpecs = emptyList())) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = LONG_X500_NAME, rpcUsers = listOf(user))).transpose().getOrThrow()
            val initiatedFlowClassAlice = CordaRPCClient(alice.rpcAddress)
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, bob.nodeInfo.singleIdentity())
                    .returnValue
            assertThat(initiatedFlowClassAlice.getOrThrow()).isEqualTo("Success")

            val initiatedFlowClassBob = CordaRPCClient(bob.rpcAddress)
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, alice.nodeInfo.singleIdentity())
                    .returnValue
            assertThat(initiatedFlowClassBob.getOrThrow()).isEqualTo("Success")
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
        override fun call() = otherPartySession.send("Success")
    }
}
