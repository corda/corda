package net.corda.testing.node.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.MessageRecipients
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.*
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.SenderDeduplicationId
import net.corda.node.utilities.AffinityExecutor
import net.corda.testing.node.InMemoryMessagingNetwork
import java.time.Instant
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.thread

@ThreadSafe
class MockNodeMessagingService(private val configuration: NodeConfiguration,
                               private val executor: AffinityExecutor) : SingletonSerializeAsToken(), MessagingService {
    private companion object {
        private val log = contextLogger()
    }

    private inner class Handler(val topicSession: String, val callback: MessageHandler) : MessageHandlerRegistration

    @Volatile
    private var running = true

    private inner class InnerState {
        val handlers: MutableList<Handler> = ArrayList()
        val pendingRedelivery = LinkedHashSet<InMemoryMessagingNetwork.MessageTransfer>()
    }

    private val state = ThreadBox(InnerState())
    private val processedMessages: MutableSet<DeduplicationId> = Collections.synchronizedSet(HashSet<DeduplicationId>())

    override val ourSenderUUID: String = UUID.randomUUID().toString()

    private var _myAddress: InMemoryMessagingNetwork.PeerHandle? = null
    override val myAddress: InMemoryMessagingNetwork.PeerHandle get() = checkNotNull(_myAddress) { "Not started" }

    private lateinit var network: InMemoryMessagingNetwork
    private var backgroundThread: Thread? = null

    var spy: MessagingServiceSpy? = null

    /**
     * @param manuallyPumped if set to true, then you are expected to call [MockNodeMessagingService.pumpReceive]
     * in order to cause the delivery of a single message, which will occur on the thread of the caller. If set to false
     * then this class will set up a background thread to deliver messages asynchronously, if the handler specifies no
     * executor.
     * @param id the numeric ID to use, e.g. set to whatever ID the node used last time.
     */
    fun start(network: InMemoryMessagingNetwork, manuallyPumped: Boolean, id: Int, notaryService: PartyAndCertificate?) {
        val peerHandle = InMemoryMessagingNetwork.PeerHandle(id, configuration.myLegalName)

        this.network = network
        _myAddress = peerHandle

        val oldNode = network.initPeer(this)
        if (oldNode != null) {
            inheritPendingRedelivery(oldNode)
        }

        if (!manuallyPumped) {
            backgroundThread = thread(isDaemon = true, name = "In-memory message dispatcher") {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        pumpReceiveInternal(true)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }

        network.addNotaryIdentity(this, notaryService)
    }

    override fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients {
        return when (partyInfo) {
            is PartyInfo.SingleNode -> network.getPeer(partyInfo.party.name)
                    ?: throw IllegalArgumentException("No StartedMockNode for party ${partyInfo.party.name}")
            is PartyInfo.DistributedNode -> InMemoryMessagingNetwork.DistributedServiceHandle(partyInfo.party)
        }
    }

    override fun addMessageHandler(topic: String, callback: MessageHandler): MessageHandlerRegistration {
        check(running)
        val (handler, transfers) = state.locked {
            val handler = Handler(topic, callback).apply { handlers.add(this) }
            val pending = ArrayList<InMemoryMessagingNetwork.MessageTransfer>()
            pending.addAll(pendingRedelivery)
            pendingRedelivery.clear()
            Pair(handler, pending)
        }

        unPopMessages(transfers)
        return handler
    }

    /**
     * When a new message handler is added, this implies we have started a new node.  The add handler logic uses this to
     * push back any un-acknowledged messages for this peer onto the head of the queue (rather than the tail) to maintain message
     * delivery order.  We push them back because their consumption was not complete and a restarted node would
     * see them re-delivered if this was Artemis.
     */
    private fun unPopMessages(transfers: Collection<InMemoryMessagingNetwork.MessageTransfer>) {
        val messageQueue = network.getQueueForPeerHandle(myAddress)
        val drained = ArrayList<InMemoryMessagingNetwork.MessageTransfer>().apply { messageQueue.drainTo(this) }
        messageQueue.addAll(transfers)
        messageQueue.addAll(drained)
    }

    private fun inheritPendingRedelivery(other: MockNodeMessagingService) {
        state.locked {
            pendingRedelivery.addAll(other.state.locked { pendingRedelivery })
        }
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        check(running)
        state.locked { check(handlers.remove(registration as Handler)) }
    }

    override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
        check(running)
        val spy = this.spy
        if (spy != null) {
            this.spy = null
            try {
                spy.send(message, target, sequenceKey)
            } finally {
                this.spy = spy
            }
        } else {
            network.msgSend(this, message, target)
        }
    }

    override fun send(addressedMessages: List<MessagingService.AddressedMessage>) {
        for ((message, target, sequenceKey) in addressedMessages) {
            send(message, target, sequenceKey)
        }
    }

    override fun close() {
        backgroundThread?.let {
            it.interrupt()
            it.join()
        }
        running = false
        network.netNodeHasShutdown(myAddress)
    }

    /** Returns the given (topic & session, data) pair as a newly created message object. */
    override fun createMessage(topic: String, data: ByteArray, deduplicationId: SenderDeduplicationId, additionalHeaders: Map<String, String>): Message {
        return InMemoryMessage(topic, OpaqueBytes(data), deduplicationId.deduplicationId, senderUUID = deduplicationId.senderUUID)
    }

    /**
     * Delivers a single message from the internal queue. If there are no messages waiting to be delivered and block
     * is true, waits until one has been provided on a different thread via send. If block is false, the return
     * result indicates whether a message was delivered or not.
     *
     * @return the message that was processed, if any in this round.
     */
    fun pumpReceive(block: Boolean): InMemoryMessagingNetwork.MessageTransfer? {
        check(backgroundThread == null)
        check(running)
        executor.flush()
        try {
            return pumpReceiveInternal(block)
        } finally {
            executor.flush()
        }
    }

    /**
     * Get the next transfer, and matching queue, that is ready to handle. Any pending transfers without handlers
     * are placed into `pendingRedelivery` to try again later.
     *
     * @param block if this should block until a message it can process.
     */
    private fun getNextQueue(q: LinkedBlockingQueue<InMemoryMessagingNetwork.MessageTransfer>, block: Boolean): Pair<InMemoryMessagingNetwork.MessageTransfer, List<Handler>>? {
        var deliverTo: List<Handler>? = null
        // Pop transfers off the queue until we run out (and are not blocking), or find something we can process
        while (deliverTo == null) {
            val transfer = (if (block) q.take() else q.poll()) ?: return null
            deliverTo = state.locked {
                val matchingHandlers = handlers.filter { it.topicSession.isBlank() || transfer.message.topic == it.topicSession }
                if (matchingHandlers.isEmpty()) {
                    // Got no handlers for this message yet. Keep the message around and attempt redelivery after a new
                    // handler has been registered. The purpose of this path is to make unit tests that have multi-threading
                    // reliable, as a sender may attempt to send a message to a receiver that hasn't finished setting
                    // up a handler for yet. Most unit tests don't run threaded, but we want to test true parallelism at
                    // least sometimes.
                    log.warn("Message to ${transfer.message.topic} could not be delivered")
                    pendingRedelivery.add(transfer)
                    null
                } else {
                    matchingHandlers
                }
            }
            if (deliverTo != null) {
                return Pair(transfer, deliverTo)
            }
        }
        return null
    }

    private fun pumpReceiveInternal(block: Boolean): InMemoryMessagingNetwork.MessageTransfer? {
        val q = network.getQueueForPeerHandle(myAddress)
        val (transfer, deliverTo) = getNextQueue(q, block) ?: return null
        if (transfer.message.uniqueMessageId !in processedMessages) {
            executor.execute {
                for (handler in deliverTo) {
                    try {
                        val receivedMessage = transfer.toReceivedMessage()
                        state.locked { pendingRedelivery.add(transfer) }
                        handler.callback(receivedMessage, handler, InMemoryDeduplicationHandler(receivedMessage, transfer))
                    } catch (e: Exception) {
                        log.error("Caught exception in handler for $this/${handler.topicSession}", e)
                    }
                }
                network.onMessageTransfer(transfer)
            }
        } else {
            log.info("Drop duplicate message ${transfer.message.uniqueMessageId}")
        }
        return transfer
    }

    private fun InMemoryMessagingNetwork.MessageTransfer.toReceivedMessage(): ReceivedMessage {
        return InMemoryReceivedMessage(
                message.topic,
                OpaqueBytes(message.data.bytes.copyOf()), // Kryo messes with the buffer so give each client a unique copy
                1,
                message.uniqueMessageId,
                message.debugTimestamp,
                sender.name
        )
    }

    private data class InMemoryReceivedMessage(override val topic: String,
                                               override val data: ByteSequence,
                                               override val platformVersion: Int,
                                               override val uniqueMessageId: DeduplicationId,
                                               override val debugTimestamp: Instant,
                                               override val peer: CordaX500Name,
                                               override val senderUUID: String? = null,
                                               override val senderSeqNo: Long? = null,
                                               /** Note this flag is never set in the in memory network. */
                                               override val isSessionInit: Boolean = false) : ReceivedMessage {

        override val additionalHeaders: Map<String, String> = emptyMap()
    }

    private inner class InMemoryDeduplicationHandler(override val receivedMessage: ReceivedMessage, val transfer: InMemoryMessagingNetwork.MessageTransfer) : DeduplicationHandler, ExternalEvent.ExternalMessageEvent {
        override val externalCause: ExternalEvent
            get() = this
        override val deduplicationHandler: DeduplicationHandler
            get() = this

        override fun afterDatabaseTransaction() {
            this@MockNodeMessagingService.state.locked { pendingRedelivery.remove(transfer) }
        }

        override fun insideDatabaseTransaction() {
            processedMessages += transfer.message.uniqueMessageId
        }
    }

    fun hasPendingDeliveries(): Boolean = state.locked { pendingRedelivery.isNotEmpty() }
}
