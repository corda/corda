package net.corda.testing.node

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.ThreadBox
import net.corda.core.crypto.X509Utilities
import net.corda.core.getOrThrow
import net.corda.core.messaging.*
import net.corda.core.node.ServiceEntry
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.trace
import net.corda.node.services.api.MessagingServiceBuilder
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.JDBCHashSet
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.InMemoryMessagingNetwork.InMemoryMessaging
import org.apache.activemq.artemis.utils.ReusableLatch
import org.bouncycastle.asn1.x500.X500Name
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.schedule
import kotlin.concurrent.thread

/**
 * An in-memory network allows you to manufacture [InMemoryMessaging]s for a set of participants. Each
 * [InMemoryMessaging] maintains a queue of messages it has received, and a background thread that dispatches
 * messages one by one to registered handlers. Alternatively, a messaging system may be manually pumped, in which
 * case no thread is created and a caller is expected to force delivery one at a time (this is useful for unit
 * testing).
 *
 * @param servicePeerAllocationStrategy defines the strategy to be used when determining which peer to send to in case
 *     a service is addressed.
 */
@ThreadSafe
class InMemoryMessagingNetwork(
        val sendManuallyPumped: Boolean,
        val servicePeerAllocationStrategy: ServicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
        private val messagesInFlight: ReusableLatch = ReusableLatch()
) : SingletonSerializeAsToken() {
    companion object {
        val MESSAGES_LOG_NAME = "messages"
        private val log = LoggerFactory.getLogger(MESSAGES_LOG_NAME)
    }

    private var counter = 0   // -1 means stopped.
    private val handleEndpointMap = HashMap<PeerHandle, InMemoryMessaging>()

    data class MessageTransfer(val sender: PeerHandle, val message: Message, val recipients: MessageRecipients) {
        override fun toString() = "${message.topicSession} from '$sender' to '$recipients'"
    }

    // All sent messages are kept here until pumpSend is called, or manuallyPumped is set to false
    // The corresponding sentMessages stream reflects when a message was pumpSend'd
    private val messageSendQueue = LinkedBlockingQueue<MessageTransfer>()
    private val _sentMessages = PublishSubject.create<MessageTransfer>()
    @Suppress("unused") // Used by the visualiser tool.
    /** A stream of (sender, message, recipients) triples */
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
    private val serviceToPeersMapping = HashMap<ServiceHandle, LinkedHashSet<PeerHandle>>()

    @Suppress("unused") // Used by the visualiser tool.
    /** A stream of (sender, message, recipients) triples */
    val receivedMessages: Observable<MessageTransfer>
        get() = _receivedMessages

    val endpoints: List<InMemoryMessaging> @Synchronized get() = handleEndpointMap.values.toList()

    /**
     * Creates a node and returns the new object that identifies its location on the network to senders, and the
     * [InMemoryMessaging] that the recipient/in-memory node uses to receive messages and send messages itself.
     *
     * If [manuallyPumped] is set to true, then you are expected to call the [InMemoryMessaging.pump] method on the [InMemoryMessaging]
     * in order to cause the delivery of a single message, which will occur on the thread of the caller. If set to false
     * then this class will set up a background thread to deliver messages asynchronously, if the handler specifies no
     * executor.
     *
     * @param persistenceTx a lambda to wrap message handling in a transaction if necessary. Defaults to a no-op.
     */
    @Synchronized
    fun createNode(manuallyPumped: Boolean,
                   executor: AffinityExecutor,
                   advertisedServices: List<ServiceEntry>,
                   database: Database): Pair<PeerHandle, MessagingServiceBuilder<InMemoryMessaging>> {
        check(counter >= 0) { "In memory network stopped: please recreate." }
        val builder = createNodeWithID(manuallyPumped, counter, executor, advertisedServices, database = database) as Builder
        counter++
        val id = builder.id
        return Pair(id, builder)
    }

    /**
     * Creates a node at the given address: useful if you want to recreate a node to simulate a restart.
     *
     * @param manuallyPumped see [createNode].
     * @param id the numeric ID to use, e.g. set to whatever ID the node used last time.
     * @param description text string that identifies this node for message logging (if is enabled) or null to autogenerate.
     * @param persistenceTx a lambda to wrap message handling in a transaction if necessary.
     */
    fun createNodeWithID(
            manuallyPumped: Boolean,
            id: Int,
            executor: AffinityExecutor,
            advertisedServices: List<ServiceEntry>,
            description: String? = null,
            database: Database)
            : MessagingServiceBuilder<InMemoryMessaging> {
        return Builder(manuallyPumped, PeerHandle(id, description ?: "In memory node $id"), advertisedServices.map(::ServiceHandle), executor, database = database)
    }

    interface LatencyCalculator {
        fun between(sender: SingleMessageRecipient, receiver: SingleMessageRecipient): Duration
    }

    /** This can be set to an object which can inject artificial latency between sender/recipient pairs. */
    @Volatile var latencyCalculator: LatencyCalculator? = null
    private val timer = Timer()

    @Synchronized
    private fun msgSend(from: InMemoryMessaging, message: Message, recipients: MessageRecipients) {
        messagesInFlight.countUp()
        messageSendQueue += MessageTransfer(from.myAddress, message, recipients)
    }

    @Synchronized
    private fun netNodeHasShutdown(peerHandle: PeerHandle) {
        handleEndpointMap.remove(peerHandle)
    }

    @Synchronized
    private fun getQueueForPeerHandle(recipients: PeerHandle) = messageReceiveQueues.getOrPut(recipients) { LinkedBlockingQueue() }

    @Synchronized
    private fun getQueuesForServiceHandle(recipients: ServiceHandle): List<LinkedBlockingQueue<MessageTransfer>> {
        return serviceToPeersMapping[recipients]!!.map {
            messageReceiveQueues.getOrPut(it) { LinkedBlockingQueue() }
        }
    }


    val everyoneOnline: AllPossibleRecipients = object : AllPossibleRecipients {}

    fun stop() {
        val nodes = synchronized(this) {
            counter = -1
            handleEndpointMap.values.toList()
        }

        for (node in nodes)
            node.stop()

        handleEndpointMap.clear()
        messageReceiveQueues.clear()
    }

    inner class Builder(
            val manuallyPumped: Boolean,
            val id: PeerHandle,
            val serviceHandles: List<ServiceHandle>,
            val executor: AffinityExecutor,
            val database: Database) : MessagingServiceBuilder<InMemoryMessaging> {
        override fun start(): ListenableFuture<InMemoryMessaging> {
            synchronized(this@InMemoryMessagingNetwork) {
                val node = InMemoryMessaging(manuallyPumped, id, executor, database)
                handleEndpointMap[id] = node
                serviceHandles.forEach {
                    serviceToPeersMapping.getOrPut(it) { LinkedHashSet<PeerHandle>() }.add(id)
                    Unit
                }
                return Futures.immediateFuture(node)
            }
        }
    }

    data class PeerHandle(val id: Int, val description: String) : SingleMessageRecipient {
        override fun toString() = description
        override fun equals(other: Any?) = other is PeerHandle && other.id == id
        override fun hashCode() = id.hashCode()
    }

    data class ServiceHandle(val service: ServiceEntry) : MessageRecipientGroup {
        override fun toString() = "Service($service)"
    }

    /**
     * Mock service loadbalancing
     */
    sealed class ServicePeerAllocationStrategy {
        abstract fun <A> pickNext(service: ServiceHandle, pickFrom: List<A>): A
        class Random(val random: SplittableRandom = SplittableRandom()) : ServicePeerAllocationStrategy() {
            override fun <A> pickNext(service: ServiceHandle, pickFrom: List<A>): A {
                return pickFrom[random.nextInt(pickFrom.size)]
            }
        }
        class RoundRobin : ServicePeerAllocationStrategy() {
            val previousPicks = HashMap<ServiceHandle, Int>()
            override fun <A> pickNext(service: ServiceHandle, pickFrom: List<A>): A {
                val nextIndex = previousPicks.compute(service) { _key, previous ->
                    (previous?.plus(1) ?: 0) % pickFrom.size
                }
                return pickFrom[nextIndex]
            }
        }
    }

    // If block is set to true this function will only return once a message has been pushed onto the recipients' queues
    fun pumpSend(block: Boolean): MessageTransfer? {
        val transfer = (if (block) messageSendQueue.take() else messageSendQueue.poll()) ?: return null

        log.trace { transfer.toString() }
        val calc = latencyCalculator
        if (calc != null && transfer.recipients is SingleMessageRecipient) {
            val messageSent = SettableFuture.create<Unit>()
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

    fun pumpSendInternal(transfer: MessageTransfer) {
        when (transfer.recipients) {
            is PeerHandle -> getQueueForPeerHandle(transfer.recipients).add(transfer)
            is ServiceHandle -> {
                val queues = getQueuesForServiceHandle(transfer.recipients)
                val queue = servicePeerAllocationStrategy.pickNext(transfer.recipients, queues)
                queue.add(transfer)
            }
            is AllPossibleRecipients -> {
                // This means all possible recipients _that the network knows about at the time_, not literally everyone
                // who joins into the indefinite future.
                for (handle in handleEndpointMap.keys)
                    getQueueForPeerHandle(handle).add(transfer)
            }
            else -> throw IllegalArgumentException("Unknown type of recipient handle")
        }
        _sentMessages.onNext(transfer)
    }

    /**
     * An [InMemoryMessaging] provides a [MessagingService] that isn't backed by any kind of network or disk storage
     * system, but just uses regular queues on the heap instead. It is intended for unit testing and developer convenience
     * when all entities on 'the network' are being simulated in-process.
     *
     * An instance can be obtained by creating a builder and then using the start method.
     */
    @ThreadSafe
    inner class InMemoryMessaging(private val manuallyPumped: Boolean,
                                  private val peerHandle: PeerHandle,
                                  private val executor: AffinityExecutor,
                                  private val database: Database) : SingletonSerializeAsToken(), MessagingServiceInternal {
        inner class Handler(val topicSession: TopicSession,
                            val callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

        @Volatile
        private var running = true

        private inner class InnerState {
            val handlers: MutableList<Handler> = ArrayList()
            val pendingRedelivery = JDBCHashSet<MessageTransfer>("pending_messages", loadOnInit = true)
        }

        private val state = ThreadBox(InnerState())
        private val processedMessages: MutableSet<UUID> = Collections.synchronizedSet(HashSet<UUID>())

        override val myAddress: PeerHandle get() = peerHandle

        private val backgroundThread = if (manuallyPumped) null else
            thread(isDaemon = true, name = "In-memory message dispatcher") {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        pumpReceiveInternal(true)
                    } catch(e: InterruptedException) {
                        break
                    }
                }
            }

        override fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients {
            return when (partyInfo) {
                is PartyInfo.Node -> partyInfo.node.address
                is PartyInfo.Service -> ServiceHandle(partyInfo.service)
            }
        }

        override fun addMessageHandler(topic: String, sessionID: Long, callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration
                = addMessageHandler(TopicSession(topic, sessionID), callback)

        override fun addMessageHandler(topicSession: TopicSession, callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
            check(running)
            val (handler, transfers) = state.locked {
                val handler = Handler(topicSession, callback).apply { handlers.add(this) }
                val pending = ArrayList<MessageTransfer>()
                databaseTransaction(database) {
                    pending.addAll(pendingRedelivery)
                    pendingRedelivery.clear()
                }
                Pair(handler, pending)
            }

            transfers.forEach { pumpSendInternal(it) }
            return handler
        }

        override fun removeMessageHandler(registration: MessageHandlerRegistration) {
            check(running)
            state.locked { check(handlers.remove(registration as Handler)) }
        }

        override fun send(message: Message, target: MessageRecipients) {
            check(running)
            msgSend(this, message, target)
            if (!sendManuallyPumped) {
                pumpSend(false)
            }
        }

        override fun stop() {
            if (backgroundThread != null) {
                backgroundThread.interrupt()
                backgroundThread.join()
            }
            running = false
            netNodeHasShutdown(peerHandle)
        }

        /** Returns the given (topic & session, data) pair as a newly created message object. */
        override fun createMessage(topicSession: TopicSession, data: ByteArray, uuid: UUID): Message {
            return object : Message {
                override val topicSession: TopicSession get() = topicSession
                override val data: ByteArray get() = data
                override val debugTimestamp: Instant = Instant.now()
                override val uniqueMessageId: UUID = uuid
                override fun toString() = "$topicSession#${String(data)}"
            }
        }

        /**
         * Delivers a single message from the internal queue. If there are no messages waiting to be delivered and block
         * is true, waits until one has been provided on a different thread via send. If block is false, the return
         * result indicates whether a message was delivered or not.
         *
         * @return the message that was processed, if any in this round.
         */
        fun pumpReceive(block: Boolean): MessageTransfer? {
            check(manuallyPumped)
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
        private fun getNextQueue(q: LinkedBlockingQueue<MessageTransfer>, block: Boolean): Pair<MessageTransfer, List<Handler>>? {
            var deliverTo: List<Handler>? = null
            // Pop transfers off the queue until we run out (and are not blocking), or find something we can process
            while (deliverTo == null) {
                val transfer = (if (block) q.take() else q.poll()) ?: return null
                deliverTo = state.locked {
                    val matchingHandlers = handlers.filter { it.topicSession.isBlank() || transfer.message.topicSession == it.topicSession }
                    if (matchingHandlers.isEmpty()) {
                        // Got no handlers for this message yet. Keep the message around and attempt redelivery after a new
                        // handler has been registered. The purpose of this path is to make unit tests that have multi-threading
                        // reliable, as a sender may attempt to send a message to a receiver that hasn't finished setting
                        // up a handler for yet. Most unit tests don't run threaded, but we want to test true parallelism at
                        // least sometimes.
                        log.warn("Message to ${transfer.message.topicSession} could not be delivered")
                        databaseTransaction(database) {
                            pendingRedelivery.add(transfer)
                        }
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

        private fun pumpReceiveInternal(block: Boolean): MessageTransfer? {
            val q = getQueueForPeerHandle(peerHandle)
            val next = getNextQueue(q, block) ?: return null
            val (transfer, deliverTo) = next

            if (transfer.message.uniqueMessageId !in processedMessages) {
                executor.execute {
                    databaseTransaction(database) {
                        for (handler in deliverTo) {
                            try {
                                handler.callback(transfer.toReceivedMessage(), handler)
                            } catch(e: Exception) {
                                log.error("Caught exception in handler for $this/${handler.topicSession}", e)
                            }
                        }
                        _receivedMessages.onNext(transfer)
                        processedMessages += transfer.message.uniqueMessageId
                        messagesInFlight.countDown()
                    }
                }
            } else {
                log.info("Drop duplicate message ${transfer.message.uniqueMessageId}")
            }
            return transfer
        }

        private fun MessageTransfer.toReceivedMessage() = object : ReceivedMessage {
            override val topicSession: TopicSession get() = message.topicSession
            override val data: ByteArray get() = message.data.copyOf() // Kryo messes with the buffer so give each client a unique copy
            override val peer: X500Name get() = X509Utilities.getDevX509Name(sender.description)
            override val debugTimestamp: Instant get() = message.debugTimestamp
            override val uniqueMessageId: UUID get() = message.uniqueMessageId
        }
    }
}
