package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.chooseIdentity
import net.corda.testing.node.network
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReceiveMultipleFlowTests {
    @Test
    fun `receive all messages in parallel using map style`() {
        network(3) { nodes, _ ->
            val doubleValue = 5.0
            nodes[1].registerAnswer(AlgorithmDefinition::class, doubleValue)
            val stringValue = "Thriller"
            nodes[2].registerAnswer(AlgorithmDefinition::class, stringValue)

            val flow = nodes[0].services.startFlow(ParallelAlgorithmMap(nodes[1].info.chooseIdentity(), nodes[2].info.chooseIdentity()))
            runNetwork()

            val result = flow.resultFuture.getOrThrow()

            assertThat(result).isEqualTo(doubleValue * stringValue.length)
        }
    }

    @Test
    fun `receive all messages in parallel using list style`() {
        network(3) { nodes, _ ->
            val value1 = 5.0
            nodes[1].registerAnswer(ParallelAlgorithmList::class, value1)
            val value2 = 6.0
            nodes[2].registerAnswer(ParallelAlgorithmList::class, value2)

            val flow = nodes[0].services.startFlow(ParallelAlgorithmList(nodes[1].info.chooseIdentity(), nodes[2].info.chooseIdentity()))
            runNetwork()
            val data = flow.resultFuture.getOrThrow()

            assertThat(data[0]).isEqualTo(value1)
            assertThat(data[1]).isEqualTo(value2)
            assertThat(data.fold(1.0) { a, b -> a * b }).isEqualTo(value1 * value2)
        }
    }

    class ParallelAlgorithmMap(doubleMember: Party, stringMember: Party) : AlgorithmDefinition(doubleMember, stringMember) {
        @Suspendable
        override fun askMembersForData(doubleMember: Party, stringMember: Party): Data {
            val doubleSession = initiateFlow(doubleMember)
            val stringSession = initiateFlow(stringMember)
            val rawData = receiveAll(Double::class from doubleSession, String::class from stringSession)
            return Data(rawData from doubleSession, rawData from stringSession)
        }
    }

    @InitiatingFlow
    class ParallelAlgorithmList(private val member1: Party, private val member2: Party) : FlowLogic<List<Double>>() {
        @Suspendable
        override fun call(): List<Double> {
            val session1 = initiateFlow(member1)
            val session2 = initiateFlow(member2)
            val data = receiveAll<Double>(session1, session2)
            return computeAnswer(data)
        }

        private fun computeAnswer(data: List<UntrustworthyData<Double>>): List<Double> {
            return data.map { element -> element.unwrap { it } }
        }
    }

    @InitiatingFlow
    abstract class AlgorithmDefinition(private val doubleMember: Party, private val stringMember: Party) : FlowLogic<Double>() {
        protected data class Data(val double: Double, val string: String)

        @Suspendable
        protected abstract fun askMembersForData(doubleMember: Party, stringMember: Party): Data

        @Suspendable
        override fun call(): Double {
            val (double, string) = askMembersForData(doubleMember, stringMember)
            return double * string.length
        }
    }
}