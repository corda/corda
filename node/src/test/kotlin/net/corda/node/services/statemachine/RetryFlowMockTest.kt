package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.packageName
import net.corda.core.messaging.MessageRecipients
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.StartedNode
import net.corda.node.services.FinalityHandler
import net.corda.node.services.messaging.Message
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.MessagingServiceSpy
import net.corda.testing.node.internal.newContext
import net.corda.testing.node.internal.setMessagingServiceSpy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.exception.ConstraintViolationException
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.SQLException
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RetryFlowMockTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var nodeA: StartedNode<MockNode>
    private lateinit var nodeB: StartedNode<MockNode>

    @Before
    fun start() {
        mockNet = InternalMockNetwork(threadPerNode = true, cordappsForAllNodes = cordappsForPackages(setOf(this.javaClass.packageName)))
        nodeA = mockNet.createNode()
        nodeB = mockNet.createNode()
        mockNet.startNodes()
        RetryFlow.count = 0
        SendAndRetryFlow.count = 0
        RetryInsertFlow.count = 0
        KeepSendingFlow.count.set(0)
    }

    private fun <T> StartedNode<MockNode>.startFlow(logic: FlowLogic<T>): CordaFuture<T> {
        return this.services.startFlow(logic, this.services.newContext()).flatMap { it.resultFuture }
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `Single retry`() {
        assertEquals(Unit, nodeA.startFlow(RetryFlow(1)).get())
        assertEquals(2, RetryFlow.count)
    }

    @Test
    fun `Retry forever`() {
        assertThatThrownBy {
            nodeA.startFlow(RetryFlow(Int.MAX_VALUE)).getOrThrow()
        }.isInstanceOf(LimitedRetryCausingError::class.java)
        assertEquals(5, RetryFlow.count)
    }

    @Test
    fun `Retry does not set senderUUID`() {
        val messagesSent = Collections.synchronizedList(mutableListOf<Message>())
        val partyB = nodeB.info.legalIdentities.first()
        nodeA.setMessagingServiceSpy(object : MessagingServiceSpy(nodeA.network) {
            override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
                messagesSent.add(message)
                messagingService.send(message, target)
            }
        })
        nodeA.startFlow(SendAndRetryFlow(1, partyB)).get()
        assertNotNull(messagesSent.first().senderUUID)
        assertNull(messagesSent.last().senderUUID)
        assertEquals(2, SendAndRetryFlow.count)
    }

    @Test
    fun `Restart does not set senderUUID`() {
        val messagesSent = Collections.synchronizedList(mutableListOf<Message>())
        val partyB = nodeB.info.legalIdentities.first()
        nodeA.setMessagingServiceSpy(object : MessagingServiceSpy(nodeA.network) {
            override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
                messagesSent.add(message)
                messagingService.send(message, target)
            }
        })
        val count = 10000 // Lots of iterations so the flow keeps going long enough
        nodeA.startFlow(KeepSendingFlow(count, partyB))
        while (messagesSent.size < 1) {
            Thread.sleep(10)
        }
        assertNotNull(messagesSent.first().senderUUID)
        nodeA = mockNet.restartNode(nodeA)
        // This is a bit racy because restarting the node actually starts it, so we need to make sure there's enough iterations we get here with flow still going.
        nodeA.setMessagingServiceSpy(object : MessagingServiceSpy(nodeA.network) {
            override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
                messagesSent.add(message)
                messagingService.send(message, target)
            }
        })
        // Now short circuit the iterations so the flow finishes soon.
        KeepSendingFlow.count.set(count - 2)
        while (nodeA.smm.allStateMachines.size > 0) {
            Thread.sleep(10)
        }
        assertNull(messagesSent.last().senderUUID)
    }

    @Test
    fun `Retry duplicate insert`() {
        assertEquals(Unit, nodeA.startFlow(RetryInsertFlow(1)).get())
        assertEquals(2, RetryInsertFlow.count)
    }

    @Test
    fun `Patient records do not leak in hospital`() {
        assertEquals(Unit, nodeA.startFlow(RetryFlow(1)).get())
        // Need to make sure the state machine has finished.  Otherwise this test is flakey.
        mockNet.waitQuiescent()
        assertThat(nodeA.smm.flowHospital.track().snapshot).isEmpty()
        assertEquals(2, RetryFlow.count)
    }

    @Test
    fun `Patient records do not leak in hospital when using killFlow`() {
        // Make sure we have seen an update from the hospital, and thus the flow went there.
        val records = nodeA.smm.flowHospital.track().updates.toBlocking().toIterable().iterator()
        val flow: FlowStateMachine<Unit> = nodeA.services.startFlow(FinalityHandler(object : FlowSession() {
            override val counterparty: Party
                get() = TODO("not implemented")

            override fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo {
                TODO("not implemented")
            }

            override fun getCounterpartyFlowInfo(): FlowInfo {
                TODO("not implemented")
            }

            override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
                TODO("not implemented")
            }

            override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
                TODO("not implemented")
            }

            override fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
                TODO("not implemented")
            }

            override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
                TODO("not implemented")
            }

            override fun send(payload: Any, maySkipCheckpoint: Boolean) {
                TODO("not implemented")
            }

            override fun send(payload: Any) {
                TODO("not implemented")
            }
        }), nodeA.services.newContext()).get()
        // Should be 2 records, one for admission and one for keep in.
        records.next()
        records.next()
        // Killing it should remove it.
        nodeA.smm.killFlow(flow.id)
        assertThat(nodeA.smm.flowHospital.track().snapshot).isEmpty()
    }
}

class LimitedRetryCausingError : ConstraintViolationException("Test message", SQLException(), "Test constraint")

class RetryCausingError : SQLException("deadlock")

class RetryFlow(private val i: Int) : FlowLogic<Unit>() {
    companion object {
        @Volatile
        var count = 0
    }

    @Suspendable
    override fun call() {
        logger.info("Hello $count")
        if (count++ < i) {
            if (i == Int.MAX_VALUE) {
                throw LimitedRetryCausingError()
            } else {
                throw RetryCausingError()
            }
        }
    }
}

@InitiatingFlow
class SendAndRetryFlow(private val i: Int, private val other: Party) : FlowLogic<Unit>() {
    companion object {
        @Volatile
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

@Suppress("unused")
@InitiatedBy(SendAndRetryFlow::class)
class ReceiveFlow2(private val other: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val received = other.receive<String>().unwrap { it }
        logger.info("Received... $received")
    }
}

@InitiatingFlow
class KeepSendingFlow(private val i: Int, private val other: Party) : FlowLogic<Unit>() {
    companion object {
        val count = AtomicInteger(0)
    }

    @Suspendable
    override fun call() {
        val session = initiateFlow(other)
        session.send(i.toString())
        do {
            logger.info("Sending... $count")
            session.send("Boo")
        } while (count.getAndIncrement() < i)
    }
}

@Suppress("unused")
@InitiatedBy(KeepSendingFlow::class)
class ReceiveFlow3(private val other: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        var count = other.receive<String>().unwrap { it.toInt() }
        while (count-- > 0) {
            val received = other.receive<String>().unwrap { it }
            logger.info("Received... $received $count")
        }
    }
}

class RetryInsertFlow(private val i: Int) : FlowLogic<Unit>() {
    companion object {
        @Volatile
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
