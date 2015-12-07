/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.messaging

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import core.sha256
import core.utilities.loggerFor
import core.utilities.trace
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.currentThread
import kotlin.concurrent.thread

/**
 * An in-memory network allows you to manufacture [Node]s for a set of participants. Each
 * [Node] maintains a queue of messages it has received, and a background thread that dispatches
 * messages one by one to registered handlers. Alternatively, a messaging system may be manually pumped, in which
 * case no thread is created and a caller is expected to force delivery one at a time (this is useful for unit
 * testing).
 */
@ThreadSafe
public class InMemoryNetwork {
    companion object {
        private val L = loggerFor<InMemoryNetwork>()
    }

    @GuardedBy("this") private var counter = 0   // -1 means stopped.
    private val networkMap: MutableMap<InMemoryNodeHandle, Node> = Collections.synchronizedMap(HashMap())

    /**
     * Creates a node and returns the new object that identifies its location on the network to senders, and the
     * [Node] that the recipient/in-memory node uses to receive messages and send messages itself.
     *
     * If [manuallyPumped] is set to true, then you are expected to call the [Node.pump] method on the [Node]
     * in order to cause the delivery of a single message, which will occur on the thread of the caller. If set to false
     * then this class will set up a background thread to deliver messages asynchronously, if the handler specifies no
     * executor.
     */
    @Synchronized
    fun createNode(manuallyPumped: Boolean): Pair<SingleMessageRecipient, MessagingSystemBuilder<Node>> {
        check(counter >= 0) { "In memory network stopped: please recreate. "}

        val id = InMemoryNodeHandle(counter)
        counter++
        return Pair(id, Builder(manuallyPumped, id))
    }

    val entireNetwork: AllPossibleRecipients = object : AllPossibleRecipients {}

    @Synchronized
    fun stop() {
        for (node in networkMap.values) {
            node.stop()
        }
        counter = -1
    }

    private inner class Builder(val manuallyPumped: Boolean, val id: InMemoryNodeHandle) : MessagingSystemBuilder<Node> {
        override fun start(): ListenableFuture<Node> {
            val node = Node(manuallyPumped)
            networkMap[id] = node
            return Futures.immediateFuture(node)
        }
    }

    private class InMemoryNodeHandle(val id: Int) : SingleMessageRecipient {
        override fun toString() = "In memory node $id"
        override fun equals(other: Any?) = other is InMemoryNodeHandle && other.id == id
        override fun hashCode() = id.hashCode()
    }

    /**
     * An [Node] provides a [MessagingSystem] that isn't backed by any kind of network or disk storage
     * system, but just uses regular queues on the heap instead. It is intended for unit testing and developer convenience
     * when all entities on 'the network' are being simulated in-process.
     *
     * An instance can be obtained by creating a builder and then using the start method.
     */
    inner class Node(private val manuallyPumped: Boolean): MessagingSystem {
        inner class Handler(val executor: Executor?, val topic: String, val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration
        @GuardedBy("this")
        protected val handlers: MutableList<Handler> = ArrayList()
        @GuardedBy("this")
        protected var running = true
        protected val q = LinkedBlockingQueue<Message>()

        protected val backgroundThread = if (manuallyPumped) null else thread(isDaemon = true, name = "In-memory message dispatcher ") {
            while (!currentThread.isInterrupted) pumpInternal(true)
        }

        @Synchronized
        override fun addMessageHandler(topic: String, executor: Executor?, callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
            check(running)
            return Handler(executor, topic, callback).apply { handlers.add(this) }
        }

        @Synchronized
        override fun removeMessageHandler(registration: MessageHandlerRegistration) {
            check(running)
            check(handlers.remove(registration as Handler))
        }

        @Synchronized
        override fun send(message: Message, target: MessageRecipients) {
            check(running)
            L.trace { "Sending message of topic '${message.topic}' to '$target'" }
            when (target) {
                is InMemoryNodeHandle -> {
                    val node = networkMap[target] ?: throw IllegalArgumentException("Unknown message recipient: $target")
                    node.q.put(message)
                }
                entireNetwork -> {
                    for (node in networkMap.values) {
                        node.q.put(message)
                    }
                }
                else -> throw IllegalArgumentException("Unhandled type of target: $target")
            }
        }

        @Synchronized
        override fun stop() {
            backgroundThread?.interrupt()
            running = false
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
         * is true, waits until one has been provided on a different thread via send. If block is false, the return result
         * indicates whether a message was delivered or not.
         */
        fun pump(block: Boolean): Boolean {
            check(manuallyPumped)
            synchronized(this) { check(running) }
            return pumpInternal(block)
        }

        private fun pumpInternal(block: Boolean): Boolean {
            val message = if (block) q.take() else q.poll()

            if (message == null)
                return false

            val deliverTo = synchronized(this) {
                handlers.filter { if (it.topic.isBlank()) true else message.topic == it.topic }
            }

            for (handler in deliverTo) {
                // Now deliver via the requested executor, or on this thread if no executor was provided at registration time.
                (handler.executor ?: MoreExecutors.directExecutor()).execute { handler.callback(message, handler) }
            }

            return true
        }
    }
}