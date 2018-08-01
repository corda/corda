/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.assertion.assert
import net.corda.testing.internal.matchers.flow.willReturn
import net.corda.core.flows.mixins.WithMockNet
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import org.assertj.core.api.Assertions.assertThat
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
    fun showcase_flows_as_closures() {
        val answer = 10.0
        val message = "Hello Ivan"

        val counterParty = nodes[1].info.singleIdentity()

        val initiatingFlow = @InitiatingFlow object : FlowLogic<Any>() {

            @Suspendable
            override fun call(): Any {
                val session = initiateFlow(counterParty)
                return session.sendAndReceive<Any>(message).unwrap { it }
            }
        }

        nodes[1].registerInitiatedFlow(initiatingFlow::class) { session ->
            object : FlowLogic<Unit>() {
                @Suspendable
                override fun call() {
                    // this is a closure, meaning you can access variables outside its scope e.g., `answer`.
                    val receivedMessage = session.receive<String>().unwrap { it }
                    logger.info("Got message from counterParty: $receivedMessage.")
                    assertThat(receivedMessage).isEqualTo(message)
                    session.send(answer)
                }
            } as FlowLogic<Unit>
        }

        assert.that(
                nodes[0].startFlowAndRunNetwork(initiatingFlow),
                willReturn(answer as Any))
    }

    @Test
    fun `receive all messages in parallel using map style`() {
        val doubleValue = 5.0
        nodes[1].registerAnswer(AlgorithmDefinition::class, doubleValue)
        val stringValue = "Thriller"
        nodes[2].registerAnswer(AlgorithmDefinition::class, stringValue)

        assert.that(
                nodes[0].startFlowAndRunNetwork(ParallelAlgorithmMap(nodes[1].info.singleIdentity(), nodes[2].info.singleIdentity())),
                willReturn(doubleValue * stringValue.length))
    }

    @Test
    fun `receive all messages in parallel using list style`() {
        val value1 = 5.0
        nodes[1].registerAnswer(ParallelAlgorithmList::class, value1)
        val value2 = 6.0
        nodes[2].registerAnswer(ParallelAlgorithmList::class, value2)

        assert.that(
                nodes[0].startFlowAndRunNetwork(ParallelAlgorithmList(nodes[1].info.singleIdentity(), nodes[2].info.singleIdentity())),
                willReturn(listOf(value1, value2)))
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