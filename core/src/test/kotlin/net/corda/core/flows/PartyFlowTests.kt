package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.mixins.WithFinality
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.enclosedCordapp
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.security.PublicKey

class PartyFlowTests : WithFinality {
    override val mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(FINANCE_CONTRACTS_CORDAPP, enclosedCordapp()))

    private val aliceNode = makeNode(ALICE_NAME)

    private val notary = mockNet.defaultNotaryIdentity

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test
    fun `finalise a simple transaction`() {
        val bob = makeNode(BOB_NAME)
        bob.registerInitiatedFlow(InitiateFlowCheckingRespondingFlow::class.java)
        val newKey = bob.services.keyManagementService.freshKey()
        val result = aliceNode.startFlow(InitiateFlowCheckingFlow(Party(bob.info.legalIdentities.first().name, newKey)))
        mockNet.runNetwork()

        Assert.assertThat(result.resultFuture.getOrThrow(), `is`(equalTo(newKey)))
    }


    @InitiatingFlow
    class InitiateFlowCheckingFlow(val party: Party) : FlowLogic<PublicKey>() {

        @Suspendable
        override fun call(): PublicKey {
            val session = initiateFlow(party)
            session.send("HELLO")
            val responseString = session.receive<String>().unwrap { it }
            println(responseString)
            return session.counterparty.owningKey
        }
    }

    @InitiatedBy(InitiateFlowCheckingFlow::class)
    class InitiateFlowCheckingRespondingFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            val hello = otherSession.receive(String::class.java)
            otherSession.send("$hello?")
        }
    }

}
