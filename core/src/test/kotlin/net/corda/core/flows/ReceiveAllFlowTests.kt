package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class ReceiveMultipleFlowTests {
    lateinit private var mockNet: MockNetwork
    lateinit private var coordinator: StartedNode<MockNetwork.MockNode>
    lateinit private var clusterMember1: StartedNode<MockNetwork.MockNode>
    lateinit private var clusterMember2: StartedNode<MockNetwork.MockNode>
    lateinit private var notary: StartedNode<MockNetwork.MockNode>
    lateinit private var services: MockServices

    @Before
    fun setup() {
        setCordappPackages("net.corda.testing.contracts")
        services = MockServices()
        mockNet = MockNetwork()
        notary = mockNet.createNotaryNode()
        coordinator = mockNet.createPartyNode(notary.network.myAddress)
        clusterMember1 = mockNet.createPartyNode(notary.network.myAddress)
        clusterMember2 = mockNet.createPartyNode(notary.network.myAddress)
        mockNet.runNetwork()
        coordinator.internals.ensureRegistered()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `receive all messages in parallel using map style`() {
        val doubleValue = 5.0
        clusterMember1.registerInitiatedFlow(AlgorithmDefinition::class, { session -> Answer(session, doubleValue) })
        val stringValue = "Thriller"
        clusterMember2.registerInitiatedFlow(AlgorithmDefinition::class, { session -> Answer(session, stringValue) })

        val flow = coordinator.services.startFlow(ParallelAlgorithmMap(clusterMember1.info.chooseIdentity(), clusterMember2.info.chooseIdentity()))
        mockNet.runNetwork()

        val result = flow.resultFuture.getOrThrow()

        assertThat(result).isEqualTo(doubleValue * stringValue.length)
    }

    @Test
    fun `receive all messages in parallel using list style`() {
        val value1 = 5.0
        clusterMember1.registerInitiatedFlow(ParallelAlgorithmList::class, { session -> Answer(session, value1) })
        val value2 = 6.0
        clusterMember2.registerInitiatedFlow(ParallelAlgorithmList::class, { session -> Answer(session, value2) })

        val flow = coordinator.services.startFlow(ParallelAlgorithmList(clusterMember1.info.chooseIdentity(), clusterMember2.info.chooseIdentity()))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow()

        assertThat(result).isEqualTo(value1 * value2)
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
    class ParallelAlgorithmList(private val member1: Party, private val member2: Party) : FlowLogic<Double>() {
        @Suspendable
        override fun call(): Double {
            val session1 = initiateFlow(member1)
            val session2 = initiateFlow(member2)
            val data = receiveAll<Double>(session1, session2)
            return computeAnswer(data)
        }

        private fun computeAnswer(data: List<UntrustworthyData<Double>>): Double {
            return data.map { element -> element.unwrap { it } }.fold(1.0) { a, b -> a * b }
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