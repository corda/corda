package net.corda.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SessionDataPersistenceTest {

    private val user = User("u", "p", setOf(Permissions.all()))

    @Test(timeout=300_000)
    fun `session data are persisted successfully and with the appropriate sequence numbers`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = setOf(enclosedCordapp()))) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME)).transpose().getOrThrow()

            val numberOfMessages = 3
            alice.rpc.startFlow(::InitiatorFlow, bob.nodeInfo.legalIdentities.first(), numberOfMessages).returnValue.get()

            // session data are not maintained for the initiator side.
            val aliceLastSeqNumbers = alice.rpc.startFlow(::GetSeqNumbersFlow).returnValue.get()
            assertThat(aliceLastSeqNumbers).isEmpty()

            // only one flow here, so sender sequence number is expected to start from zero and increment by one for each message.
            val bobLastSeqNumbers = bob.rpc.startFlow(::GetSeqNumbersFlow).returnValue.get()
            assertThat(bobLastSeqNumbers).hasSize(1)
            assertThat(bobLastSeqNumbers).first().isEqualTo(Pair(0, numberOfMessages - 1))
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InitiatorFlow(private val otherParty: Party, private val numberOfMessages: Int) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(otherParty)
            session.send(numberOfMessages)

            (2 .. numberOfMessages).forEach {
                session.send("message $it")
            }

            session.receive<String>()
        }
    }

    @InitiatedBy(InitiatorFlow::class)
    open class ResponderFlow(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val numberOfMessages = otherPartySession.receive<Int>().unwrap { it }

            (2 .. numberOfMessages).forEach {
                otherPartySession.receive<String>()
            }

            otherPartySession.send("Got them all")
        }
    }

    @StartableByRPC
    class GetSeqNumbersFlow: FlowLogic<MutableList<Pair<Int, Int>>>() {
        @Suspendable
        override fun call(): MutableList<Pair<Int, Int>> {
            return getSeqNumbers()
        }

        private fun getSeqNumbers(): MutableList<Pair<Int, Int>> {
            val sequenceNumbers = mutableListOf<Pair<Int, Int>>()
            serviceHub.jdbcSession().createStatement().use { stmt ->
                stmt.execute("SELECT init_sequence_number, last_sequence_number FROM node_session_data")
                while (stmt.resultSet.next()) {
                    val firstSeqNo = stmt.resultSet.getInt(1)
                    val lastSeqNo = stmt.resultSet.getInt(2)
                    sequenceNumbers += Pair(firstSeqNo, lastSeqNo)
                }
            }

            return sequenceNumbers
        }
    }

}