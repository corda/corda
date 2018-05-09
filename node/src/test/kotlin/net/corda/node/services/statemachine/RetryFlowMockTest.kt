/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.messaging.MessageRecipients
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.StartedNode
import net.corda.node.services.messaging.Message
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.MessagingServiceSpy
import net.corda.testing.node.internal.newContext
import net.corda.testing.node.internal.setMessagingServiceSpy
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.SQLException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RetryFlowMockTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var internalNodeA: StartedNode<InternalMockNetwork.MockNode>
    private lateinit var internalNodeB: StartedNode<InternalMockNetwork.MockNode>

    @Before
    fun start() {
        mockNet = InternalMockNetwork(threadPerNode = true, cordappPackages = listOf(this.javaClass.`package`.name))
        internalNodeA = mockNet.createNode()
        internalNodeB = mockNet.createNode()
        mockNet.startNodes()
    }

    private fun <T> StartedNode<InternalMockNetwork.MockNode>.startFlow(logic: FlowLogic<T>): CordaFuture<T> = this.services.startFlow(logic, this.services.newContext()).getOrThrow().resultFuture

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Single retry`() {
        assertEquals(Unit, internalNodeA.startFlow(RetryFlow(1)).get())
    }

    @Test
    fun `Retry forever`() {
        Assertions.assertThatThrownBy {
            internalNodeA.startFlow(RetryFlow(Int.MAX_VALUE)).getOrThrow()
        }.isInstanceOf(RetryCausingError::class.java)
    }

    @Test
    fun `Retry does not set senderUUID`() {
        val messagesSent = mutableListOf<Message>()
        val partyB = internalNodeB.info.legalIdentities.first()
        internalNodeA.setMessagingServiceSpy(object : MessagingServiceSpy(internalNodeA.network) {
            override fun send(message: Message, target: MessageRecipients, retryId: Long?, sequenceKey: Any) {
                messagesSent.add(message)
                messagingService.send(message, target, retryId)
            }
        })
        internalNodeA.startFlow(SendAndRetryFlow(1, partyB)).get()
        assertNotNull(messagesSent.first().senderUUID)
        assertNull(messagesSent.last().senderUUID)
    }

    @Test
    fun `Retry duplicate insert`() {
        assertEquals(Unit, internalNodeA.startFlow(RetryInsertFlow(1)).get())
    }

    @Test
    fun `Patient records do not leak in hospital`() {
        assertEquals(Unit, internalNodeA.startFlow(RetryFlow(1)).get())
        assertEquals(0, StaffedFlowHospital.numberOfPatients)
    }
}

class RetryCausingError : SQLException("deadlock")

class RetryFlow(val i: Int) : FlowLogic<Unit>() {
    companion object {
        var count = 0
    }

    @Suspendable
    override fun call() {
        logger.info("Hello")
        if (count++ < i) {
            throw RetryCausingError()
        }
    }
}

@InitiatingFlow
class SendAndRetryFlow(val i: Int, val other: Party) : FlowLogic<Unit>() {
    companion object {
        var count = 0
    }

    @Suspendable
    override fun call() {
        logger.info("Sending...")
        val session = initiateFlow(other)
        session.send("Boo")
        if (count++ < i) {
            throw RetryCausingError()
        }
    }
}

@InitiatedBy(SendAndRetryFlow::class)
class ReceiveFlow2(val other: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val received = other.receive<String>().unwrap { it }
        logger.info("Received... $received")
    }
}

class RetryInsertFlow(val i: Int) : FlowLogic<Unit>() {
    companion object {
        var count = 0
    }

    @Suspendable
    override fun call() {
        logger.info("Hello")
        doInsert()
        // Checkpoint so we roll back to here
        FlowLogic.sleep(Duration.ofSeconds(0))
        if (count++ < i) {
            doInsert()
        }
    }

    private fun doInsert() {
        val tx = DBTransactionStorage.DBTransaction("Foo")
        contextTransaction.session.save(tx)
    }
}