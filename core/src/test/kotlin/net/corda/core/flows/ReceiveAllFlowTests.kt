package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.assertion.assert
import net.corda.core.flows.mixins.WithMockNet
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.testing.node.internal.InternalMockNetwork
import org.junit.AfterClass
import org.junit.Test


class ReceiveMultipleFlowTests : WithMockNet {
    companion object {
        private val classMockNet = InternalMockNetwork()

        @JvmStatic
        @AfterClass
        fun stopNodes() = classMockNet.stopNodes()
    }

    override val mockNet = classMockNet

    private val nodes = (0..2).map { mockNet.createPartyNode() }

    @Test
    fun `receive all messages in parallel using map style`() {
        val doubleValue1 = 5.0
        val doubleValue2 = 10.0
        nodes[1].registerInitiatedFlow(AlgorithmDefinitionMap::class.java, Responder::class.java, false)
        nodes[2].registerInitiatedFlow(AlgorithmDefinitionMap::class.java, Responder::class.java, false)

        assert.that(
                nodes[0].startFlowAndRunNetwork(AlgorithmDefinitionMap(nodes[1].info.singleIdentity() to doubleValue1, nodes[2].info.singleIdentity() to doubleValue2)),
                willReturn(Data(5.0, 10)))
    }

    @Test
    fun `receive all messages in parallel using list style`() {
        val doubleValue1 = 5.0
        val doubleValue2 = 10.0
        nodes[1].registerInitiatedFlow(AlgorithmDefinitionList::class.java, Responder::class.java, false)
        nodes[2].registerInitiatedFlow(AlgorithmDefinitionList::class.java, Responder::class.java, false)

        assert.that(
                nodes[0].startFlowAndRunNetwork(AlgorithmDefinitionList(nodes[1].info.singleIdentity() to doubleValue1, nodes[2].info.singleIdentity() to doubleValue2)),
                willReturn(listOf(IntHolder(10), DoubleHolder(5.0))))
    }


    @InitiatingFlow
    class AlgorithmDefinitionList(val doubleMember: Pair<Party, Double>, val intMember: Pair<Party, Double>) : FlowLogic<List<Any>>() {
        @Suspendable
        protected fun askMembersForData(doubleMember: Pair<Party, Double>, intMember: Pair<Party, Double>): List<Any> {
            val doubleSession = initiateFlow(doubleMember.first)
            val intSession = initiateFlow(intMember.first)
            doubleSession.send(Double::class.simpleName to doubleMember.second)
            intSession.send(Int::class.simpleName to intMember.second)
            val rawData = receiveAll(Any::class.java, intSession, doubleSession).map { it.unwrap { it } }
            return rawData
        }

        @Suspendable
        override fun call(): List<Any> {
            return askMembersForData(doubleMember, intMember)
        }
    }

    @InitiatingFlow
    class AlgorithmDefinitionMap(val doubleMember: Pair<Party, Double>, val intMember: Pair<Party, Double>) : FlowLogic<Data>() {
        @Suspendable
        protected fun askMembersForData(doubleMember: Pair<Party, Double>, intMember: Pair<Party, Double>): Data {
            val doubleSession = initiateFlow(doubleMember.first)
            val intSession = initiateFlow(intMember.first)
            doubleSession.send(Double::class.simpleName to doubleMember.second)
            intSession.send(Int::class.simpleName to intMember.second)
            val rawData = receiveAll(doubleSession to DoubleHolder::class.java, intSession to IntHolder::class.java)
            return Data((rawData[doubleSession]?.unwrap { it } as DoubleHolder).number, (rawData[intSession]?.unwrap { it } as IntHolder).number)
        }

        @Suspendable
        override fun call(): Data {
            return askMembersForData(doubleMember, intMember)
        }
    }

    private class Responder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val (typeToRespondWith, valueToRespondWith) = session.receive<Pair<String, Double>>().unwrap { it }
            when (typeToRespondWith) {
                String::class.simpleName -> {
                    session.send(valueToRespondWith.toString())
                }
                Double::class.simpleName -> {
                    session.send(DoubleHolder(valueToRespondWith))
                }
                Int::class.simpleName -> {
                    session.send(IntHolder(valueToRespondWith.toInt()))
                }
                Float::class.simpleName -> {
                    session.send(FloatHolder(valueToRespondWith.toFloat()))
                }
                else -> {
                    throw IllegalStateException("No matching path for : ${typeToRespondWith}")
                }
            }
        }
    }
}

@CordaSerializable
data class DoubleHolder(val number: Double)

@CordaSerializable
data class FloatHolder(val number: Float)

@CordaSerializable
data class IntHolder(val number: Int)

data class Data(val double: Double, val int: Int)