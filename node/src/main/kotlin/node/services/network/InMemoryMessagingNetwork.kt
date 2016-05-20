package node.services.network

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import core.ThreadBox
import core.crypto.sha256
import core.messaging.*
import core.utilities.loggerFor
import core.utilities.trace
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor
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
 */
@ThreadSafe
class InMemoryMessagingNetwork {
    companion object {
        val MESSAGES_LOG_NAME = "messages"
        private val log = LoggerFactory.getLogger(MESSAGES_LOG_NAME)
    }

    private var counter = 0   // -1 means stopped.
    private val handleEndpointMap = HashMap<Handle, InMemoryMessaging>()

    data class MessageTransfer(val sender: InMemoryMessaging, val message: Message, val recipients: MessageRecipients) {
        override fun toString() = "${message.topic} from '${sender.myAddress}' to '$recipients'"
    }

    // All messages are kept here until the messages are pumped off the queue by a caller to the node class.
    // Queues are created on-demand when a message is sent to an address: the receiving node doesn't have to have
    // been created yet. If the node identified by the given handle has gone away/been shut down then messages
    // stack up here waiting for it to come back. The intent of this is to simulate a reliable messaging network.
    private val messageQueues = HashMap<Handle, LinkedBlockingQueue<MessageTransfer>>()

    val endpoints: List<InMemoryMessaging> @Synchronized get() = handleEndpointMap.values.toList()

    /**
     * Creates a node and returns the new object that identifies its location on the network to senders, and the
     * [InMemoryMessaging] that the recipient/in-memory node uses to receive messages and send messages itself.
     *
     * If [manuallyPumped] is set to true, then you are expected to call the [InMemoryMessaging.pump] method on the [InMemoryMessaging]
     * in order to cause the delivery of a single message, which will occur on the thread of the caller. If set to false
     * then this class will set up a background thread to deliver messages asynchronously, if the handler specifies no
     * executor.
     */
    @Synchronized
    fun createNode(manuallyPumped: Boolean): Pair<Handle, MessagingServiceBuilder<InMemoryMessaging>> {
        check(counter >= 0) { "In memory network stopped: please recreate." }
        val builder = createNodeWithID(manuallyPumped, counter) as Builder
        counter++
        val id = builder.id
        return Pair(id, builder)
    }

    /**
     * Creates a node at the given address: useful if you want to recreate a node to simulate a restart.
     *
     * @param manuallyPumped see [createNode]
     * @param id the numeric ID to use, e.g. set to whatever ID the node used last time.
     * @param description text string that identifies this node for message logging (if is enabled) or null to autogenerate
     */
    fun createNodeWithID(manuallyPumped: Boolean, id: Int, description: String? = null): MessagingServiceBuilder<InMemoryMessaging> {
        return Builder(manuallyPumped, Handle(id, description ?: "In memory node $id"))
    }

    private val _allMessages = PublishSubject.create<MessageTransfer>()
    /** A stream of (sender, message, recipients) triples */
    val allMessages: Observable<MessageTransfer> = _allMessages

    interface LatencyCalculator {
        fun between(sender: SingleMessageRecipient, receiver: SingleMessageRecipient): Duration
    }

    /** This can be set to an object which can inject artificial latency between sender/recipient pairs. */
    @Volatile var latencyCalculator: LatencyCalculator? = null
    private val timer = Timer()

    @Synchronized
    private fun msgSend(from: InMemoryMessaging, message: Message, recipients: MessageRecipients) {
        val transfer = MessageTransfer(from, message, recipients)
        log.trace { transfer.toString() }
        val calc = latencyCalculator
        if (calc != null && recipients is SingleMessageRecipient) {
            // Inject some artificial latency.
            timer.schedule(calc.between(from.myAddress, recipients).toMillis()) {
                msgSendInternal(transfer)
            }
        } else {
            msgSendInternal(transfer)
        }
        _allMessages.onNext(MessageTransfer(from, message, recipients))
    }

    private fun msgSendInternal(transfer: MessageTransfer) {
        when (transfer.recipients) {
            is Handle -> getQueueForHandle(transfer.recipients).add(transfer)

            is AllPossibleRecipients -> {
                // This means all possible recipients _that the network knows about at the time_, not literally everyone
                // who joins into the indefinite future.
                for (handle in handleEndpointMap.keys)
                    getQueueForHandle(handle).add(transfer)
            }
            else -> throw IllegalArgumentException("Unknown type of recipient handle")
        }
    }

    @Synchronized
    private fun netNodeHasShutdown(handle: Handle) {
        handleEndpointMap.remove(handle)
    }

    @Synchronized
    private fun getQueueForHandle(recipients: Handle) = messageQueues.getOrPut(recipients) { LinkedBlockingQueue() }

    val everyoneOnline: AllPossibleRecipients = object : AllPossibleRecipients {}

    fun stop() {
        val nodes = synchronized(this) {
            counter = -1
            handleEndpointMap.values.toList()
        }

        for (node in nodes)
            node.stop()

        handleEndpointMap.clear()
        messageQueues.clear()
    }

    inner class Builder(val manuallyPumped: Boolean, val id: Handle) : MessagingServiceBuilder<InMemoryMessaging> {
        override fun start(): ListenableFuture<InMemoryMessaging> {
            synchronized(this@InMemoryMessagingNetwork) {
                val node = InMemoryMessaging(manuallyPumped, id)
                handleEndpointMap[id] = node
                return Futures.immediateFuture(node)
            }
        }
    }

    class Handle(val id: Int, val description: String) : SingleMessageRecipient {
        override fun toString() = description
        override fun equals(other: Any?) = other is Handle && other.id == id
        override fun hashCode() = id.hashCode()
    }

    /**
     * An [InMemoryMessaging] provides a [MessagingService] that isn't backed by any kind of network or disk storage
     * system, but just uses regular queues on the heap instead. It is intended for unit testing and developer convenience
     * when all entities on 'the network' are being simulated in-process.
     *
     * An instance can be obtained by creating a builder and then using the start method.
     */
    @ThreadSafe
    inner class InMemoryMessaging(private val manuallyPumped: Boolean, private val handle: Handle) : MessagingService {
        inner class Handler(val executor: Executor?, val topic: String,
                            val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

        @Volatile
        protected var running = true

        protected inner class InnerState {
            val handlers: MutableList<Handler> = ArrayList()
            val pendingRedelivery = LinkedList<MessageTransfer>()
        }

        protected val state = ThreadBox(InnerState())

        override val myAddress: SingleMessageRecipient = handle

        protected val backgroundThread = if (manuallyPumped) null else
            thread(isDaemon = true, name = "In-memory message dispatcher") {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        pumpInternal(true)
                    } catch(e: InterruptedException) {
                        break
                    }
                }
            }

        override fun addMessageHandler(topic: String, executor: Executor?, callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
            check(running)
            val (handler, items) = state.locked {
                val handler = Handler(executor, topic, callback).apply { handlers.add(this) }
                val items = ArrayList(pendingRedelivery)
                pendingRedelivery.clear()
                Pair(handler, items)
            }
            for (it in items)
                msgSend(this, it.message, handle)
            return handler
        }

        override fun removeMessageHandler(registration: MessageHandlerRegistration) {
            check(running)
            state.locked { check(handlers.remove(registration as Handler)) }
        }

        override fun send(message: Message, target: MessageRecipients) {
            check(running)
            msgSend(this, message, target)
        }

        override fun stop() {
            if (backgroundThread != null) {
                backgroundThread.interrupt()
                backgroundThread.join()
            }
            running = false
            netNodeHasShutdown(handle)
        }

        /** Returns the given (topic, data) pair as a newly created message object.*/
        override fun createMessage(topic: String, data: ByteArray): Message {
            return object : Message {
                override val topic: String get() = topic
                override val data: ByteArray get() = data
                override val debugTimestamp: Instant = Instant.now()
                override fun serialise(): ByteArray = this.serialise()
                override val debugMessageID: String get() = serialise().sha256().prefixChars()

                override fun toString() = topic + "#" + String(data)
            }
        }

        /**
         * Delivers a single message from the internal queue. If there are no messages waiting to be delivered and block
         * is true, waits until one has been provided on a different thread via send. If block is false, the return
         * result indicates whether a message was delivered or not.
         *
         * @return the message that was processed, if any in this round.
         */
        fun pump(block: Boolean): MessageTransfer? {
            check(manuallyPumped)
            check(running)
            return pumpInternal(block)
        }

        private fun pumpInternal(block: Boolean): MessageTransfer? {
            val q = getQueueForHandle(handle)
            val transfer = (if (block) q.take() else q.poll()) ?: return null

            val deliverTo = state.locked {
                val h = handlers.filter { if (it.topic.isBlank()) true else transfer.message.topic == it.topic }

                if (h.isEmpty()) {
                    // Got no handlers for this message yet. Keep the message around and attempt redelivery after a new
                    // handler has been registered. The purpose of this path is to make unit tests that have multi-threading
                    // reliable, as a sender may attempt to send a message to a receiver that hasn't finished setting
                    // up a handler for yet. Most unit tests don't run threaded, but we want to test true parallelism at
                    // least sometimes.
                    pendingRedelivery.add(transfer)
                    return null
                }

                h
            }

            for (handler in deliverTo) {
                // Now deliver via the requested executor, or on this thread if no executor was provided at registration time.
                (handler.executor ?: MoreExecutors.directExecutor()).execute {
                    try {
                        handler.callback(transfer.message, handler)
                    } catch(e: Exception) {
                        loggerFor<InMemoryMessagingNetwork>().error("Caught exception in handler for $this/${handler.topic}", e)
                    }
                }
            }

            return transfer
        }
    }
}
