/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node

import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.crypto.CompositeKey
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.AllPossibleRecipients
import net.corda.core.messaging.MessageRecipientGroup
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.trace
import net.corda.node.services.messaging.Message
import net.corda.testing.node.internal.MockNodeMessagingService
import org.apache.activemq.artemis.utils.ReusableLatch
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.schedule

/**
 * An in-memory network allows you to manufacture [MockNodeMessagingService]s for a set of participants. Each
 * [MockNodeMessagingService] maintains a queue of messages it has received, and a background thread that dispatches
 * messages one by one to registered handlers. Alternatively, a messaging system may be manually pumped, in which
 * case no thread is created and a caller is expected to force delivery one at a time (this is useful for unit
 * testing).
 *
 * @param servicePeerAllocationStrategy defines the strategy to be used when determining which peer to send to in case
 *     a service is addressed.
 */
@ThreadSafe
class InMemoryMessagingNetwork private constructor(
        private val sendManuallyPumped: Boolean,
        private val servicePeerAllocationStrategy: ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        private val messagesInFlight: ReusableLatch = ReusableLatch()
) : SingletonSerializeAsToken() {
    companion object {
        private const val MESSAGES_LOG_NAME = "messages"
        private val log = LoggerFactory.getLogger(MESSAGES_LOG_NAME)

        @CordaInternal
        internal fun create(sendManuallyPumped: Boolean,
                            servicePeerAllocationStrategy: ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                            messagesInFlight: ReusableLatch = ReusableLatch()): InMemoryMessagingNetwork {
            return InMemoryMessagingNetwork(sendManuallyPumped, servicePeerAllocationStrategy, messagesInFlight)
        }
    }

    private var counter = 0   // -1 means stopped.
    private val handleEndpointMap = HashMap<PeerHandle, MockNodeMessagingService>()

    /** A class which represents a message being transferred from sender to recipients, within the [InMemoryMessageNetwork]. **/
    @CordaSerializable
    class MessageTransfer private constructor(val sender: PeerHandle, internal val message: Message, val recipients: MessageRecipients) {
        companion object {
            internal fun createMessageTransfer(sender: PeerHandle, message: Message, recipients: MessageRecipients): MessageTransfer {
                return MessageTransfer(sender, message, recipients)
            }
        }
        /** Data contained in this message transfer **/
        val messageData: ByteSequence get() = message.data
        override fun toString() = "${message.topic} from '$sender' to '$recipients'"
    }

    // All sent messages are kept here until pumpSend is called, or manuallyPumped is set to false
    // The corresponding sentMessages stream reflects when a message was pumpSend'd
    private val messageSendQueue = LinkedBlockingQueue<MessageTransfer>()
    private val _sentMessages = PublishSubject.create<MessageTransfer>()
    @Suppress("unused") // Used by the visualiser tool.
    /** A stream of (sender, message, recipients) triples containing messages once they have been sent by [pumpSend]. */
    val sentMessages: Observable<MessageTransfer>
        get() = _sentMessages

    // All messages are kept here until the messages are pumped off the queue by a caller to the node class.
    // Queues are created on-demand when a message is sent to an address: the receiving node doesn't have to have
    // been created yet. If the node identified by the given handle has gone away/been shut down then messages
    // stack up here waiting for it to come back. The intent of this is to simulate a reliable messaging network.
    // The corresponding stream reflects when a message was pumpReceive'd
    private val messageReceiveQueues = HashMap<PeerHandle, LinkedBlockingQueue<MessageTransfer>>()
    private val _receivedMessages = PublishSubject.create<MessageTransfer>()

    // Holds the mapping from services to peers advertising the service.
    private val serviceToPeersMapping = HashMap<DistributedServiceHandle, LinkedHashSet<PeerHandle>>()
    // Holds the mapping from node's X.500 name to PeerHandle.
    private val peersMapping = HashMap<CordaX500Name, PeerHandle>()

    @Suppress("unused") // Used by the visualiser tool.
    /** A stream of (sender, message, recipients) triples containing messages once they have been received. */
    val receivedMessages: Observable<MessageTransfer>
        get() = _receivedMessages
    internal val endpoints: List<MockNodeMessagingService>
        @CordaInternal
        @Synchronized
        get() = handleEndpointMap.values.toList()
    /** Get a [List] of all the [MockMessagingService] endpoints **/
    val endpointsExternal: List<MockMessagingService>
        @Synchronized
        get() = handleEndpointMap.values.map { MockMessagingService.createMockMessagingService(it) }.toList()

    @CordaInternal
    internal fun getPeer(name: CordaX500Name): PeerHandle? = peersMapping[name]

    @CordaInternal
    internal fun initPeer(messagingService: MockNodeMessagingService): MockNodeMessagingService? {
        peersMapping[messagingService.myAddress.name] = messagingService.myAddress // Assume that the same name - the same entity in MockNetwork.
        return synchronized(this) {
            handleEndpointMap.put(messagingService.myAddress, messagingService)
        }
    }

    @CordaInternal
    internal fun onMessageTransfer(transfer: MessageTransfer) {
        _receivedMessages.onNext(transfer)
        messagesInFlight.countDown()
    }

    @CordaInternal
    internal fun addNotaryIdentity(node: MockNodeMessagingService, notaryService: PartyAndCertificate?) {
        val peerHandle = node.myAddress
        notaryService?.let { if (it.owningKey !is CompositeKey) peersMapping[it.name] = peerHandle }
        val serviceHandles = notaryService?.let { listOf(DistributedServiceHandle(it.party)) }
                ?: emptyList() //TODO only notary can be distributed?
        synchronized(this) {
            serviceHandles.forEach {
                serviceToPeersMapping.getOrPut(it) { LinkedHashSet() }.add(peerHandle)
            }
        }
    }

    /** Implement this interface in order to inject artificial latency between sender/recipient pairs. */
    interface LatencyCalculator {
        fun between(sender: SingleMessageRecipient, receiver: SingleMessageRecipient): Duration
    }

    /** This can be set to an object which can inject artificial latency between sender/recipient pairs. */
    @Volatile
    private var latencyCalculator: LatencyCalculator? = null
    private val timer = Timer()

    @CordaInternal
    internal fun msgSend(from: MockNodeMessagingService, message: Message, recipients: MessageRecipients) {
        synchronized(this) {
            messagesInFlight.countUp()
            messageSendQueue += MessageTransfer.createMessageTransfer(from.myAddress, message, recipients)
        }
        if (!sendManuallyPumped) {
            pumpSend(false)
        }
    }

    @CordaInternal
    @Synchronized
    internal fun netNodeHasShutdown(peerHandle: PeerHandle) {
        val endpoint = handleEndpointMap[peerHandle]
        if (endpoint?.hasPendingDeliveries() != true) {
            handleEndpointMap.remove(peerHandle)
        }
    }

    @CordaInternal
    @Synchronized
    internal fun getQueueForPeerHandle(recipients: PeerHandle): LinkedBlockingQueue<MessageTransfer> {
        return messageReceiveQueues.getOrPut(recipients) { LinkedBlockingQueue() }
    }

    @Synchronized
    private fun getQueuesForServiceHandle(recipients: DistributedServiceHandle): List<LinkedBlockingQueue<MessageTransfer>> {
        return serviceToPeersMapping[recipients]!!.map {
            messageReceiveQueues.getOrPut(it) { LinkedBlockingQueue() }
        }
    }

    /**
     * Stop all nodes within the network and clear any buffered messages
     */
    fun stop() {
        val nodes = synchronized(this) {
            counter = -1
            handleEndpointMap.values.toList()
        }

        nodes.forEach { it.close() }

        handleEndpointMap.clear()
        messageReceiveQueues.clear()
        timer.cancel()
    }

    /**
     * A class which represents information about an entity on the [InMemoryMessagingNetwork].
     *
     * @property id An integer giving the node an ID on the [InMemoryMessagingNetwork].
     * @property name The node's [CordaX500Name].
     */
    @CordaSerializable
    data class PeerHandle(val id: Int, val name: CordaX500Name) : SingleMessageRecipient {
        override fun toString() = name.toString()
        override fun equals(other: Any?) = other is PeerHandle && other.id == id
        override fun hashCode() = id.hashCode()
    }

    /**
     * A class which represents information about nodes offering the same distributed service on the [InMemoryMessagingNetwork].
     *
     * @property party The [Party] offering the service.
     */
    @CordaSerializable
    data class DistributedServiceHandle(val party: Party) : MessageRecipientGroup {
        override fun toString() = "Service($party)"
    }

    /**
     * How traffic is allocated in the case where multiple nodes share a single identity, which happens for notaries
     * in a cluster. You don't normally ever need to change this: it is mostly useful for testing notary implementations.
     */
    @DoNotImplement
    sealed class ServicePeerAllocationStrategy {
        abstract fun <A> pickNext(service: DistributedServiceHandle, pickFrom: List<A>): A
        class Random(val random: SplittableRandom = SplittableRandom()) : ServicePeerAllocationStrategy() {
            override fun <A> pickNext(service: DistributedServiceHandle, pickFrom: List<A>): A {
                return pickFrom[random.nextInt(pickFrom.size)]
            }
        }

        class RoundRobin : ServicePeerAllocationStrategy() {
            private val previousPicks = HashMap<DistributedServiceHandle, Int>()
            override fun <A> pickNext(service: DistributedServiceHandle, pickFrom: List<A>): A {
                val nextIndex = previousPicks.compute(service) { _, previous ->
                    (previous?.plus(1) ?: 0) % pickFrom.size
                }!!
                return pickFrom[nextIndex]
            }
        }
    }

    /**
     * Send the next queued message to the requested recipient(s) within the network
     *
     * @param block If set to true this function will only return once a message has been pushed onto the recipients'
     * queues. This is only relevant if a [latencyCalculator] is being used to simulate latency in the network.
     */
    fun pumpSend(block: Boolean): MessageTransfer? {
        val transfer = (if (block) messageSendQueue.take() else messageSendQueue.poll()) ?: return null

        log.trace { transfer.toString() }
        val calc = latencyCalculator
        if (calc != null && transfer.recipients is SingleMessageRecipient) {
            val messageSent = openFuture<Unit>()
            // Inject some artificial latency.
            timer.schedule(calc.between(transfer.sender, transfer.recipients).toMillis()) {
                pumpSendInternal(transfer)
                messageSent.set(Unit)
            }
            if (block) {
                messageSent.getOrThrow()
            }
        } else {
            pumpSendInternal(transfer)
        }

        return transfer
    }

    private fun pumpSendInternal(transfer: MessageTransfer) {
        when (transfer.recipients) {
            is PeerHandle -> getQueueForPeerHandle(transfer.recipients).add(transfer)
            is DistributedServiceHandle -> {
                val queues = getQueuesForServiceHandle(transfer.recipients)
                val queue = servicePeerAllocationStrategy.pickNext(transfer.recipients, queues)
                queue.add(transfer)
            }
            is AllPossibleRecipients -> {
                // This means all possible recipients _that the network knows about at the time_, not literally everyone
                // who joins into the indefinite future.
                synchronized(this) {
                    for (handle in handleEndpointMap.keys) {
                        getQueueForPeerHandle(handle).add(transfer)
                    }
                }
            }
            else -> throw IllegalArgumentException("Unknown type of recipient handle")
        }
        _sentMessages.onNext(transfer)
    }

    /**
     * A class that provides an abstraction over the nodes' messaging service that also contains the ability to
     * receive messages from the queue for testing purposes.
     */
    class MockMessagingService private constructor(private val messagingService: MockNodeMessagingService) {
        companion object {
            @CordaInternal
            internal fun createMockMessagingService(messagingService: MockNodeMessagingService): MockMessagingService {
                return MockMessagingService(messagingService)
            }
        }
        /**
         * Delivers a single message from the internal queue. If there are no messages waiting to be delivered and block
         * is true, waits until one has been provided on a different thread via send. If block is false, the return
         * result indicates whether a message was delivered or not.
         *
         * @return the message that was processed, if any in this round.
         */
        fun pumpReceive(block: Boolean): InMemoryMessagingNetwork.MessageTransfer? = messagingService.pumpReceive(block)
    }
}
