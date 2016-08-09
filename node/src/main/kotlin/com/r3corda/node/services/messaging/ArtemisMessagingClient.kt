package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.RunOnCallerThread
import com.r3corda.core.ThreadBox
import com.r3corda.core.messaging.*
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.internal.Node
import com.r3corda.node.services.api.MessagingServiceInternal
import com.r3corda.node.services.config.NodeConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements the [MessagingService] API using Apache Artemis, the successor to their ActiveMQ product.
 * Artemis is a message queue broker and here we run a client connecting to the specified broker instance [ArtemisMessagingServer]
 *
 * @param serverHostPort The address of the broker instance to connect to (might be running in the same process)
 * @param myHostPort What host and port to use as an address for incoming messages
 * @param defaultExecutor This will be used as the default executor to run message handlers on, if no other is specified.
 */
@ThreadSafe
class ArtemisMessagingClient(directory: Path,
                             config: NodeConfiguration,
                             val serverHostPort: HostAndPort,
                             val myHostPort: HostAndPort,
                             val defaultExecutor: Executor = RunOnCallerThread) : ArtemisMessagingComponent(directory, config), MessagingServiceInternal {
    companion object {
        val log = loggerFor<ArtemisMessagingClient>()

        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        val TOPIC_PROPERTY = "platform-topic"

        val SESSION_ID_PROPERTY = "session-id"

        /** Temp helper until network map is established. */
        fun makeRecipient(hostAndPort: HostAndPort): SingleMessageRecipient = Address(hostAndPort)

        fun makeRecipient(hostname: String) = makeRecipient(toHostAndPort(hostname))
        fun toHostAndPort(hostname: String) = HostAndPort.fromString(hostname).withDefaultPort(Node.DEFAULT_PORT)
    }

    private class InnerState {
        var running = false
        val producers = HashMap<Address, ClientProducer>()
    }

    /** A registration to handle messages of different types */
    data class Handler(val executor: Executor?,
                       val topicSession: TopicSession,
                       val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

    override val myAddress: SingleMessageRecipient = Address(myHostPort)

    private val mutex = ThreadBox(InnerState())
    private val handlers = CopyOnWriteArrayList<Handler>()

    private lateinit var clientFactory: ClientSessionFactory
    private var session: ClientSession? = null
    private var consumer: ClientConsumer? = null

    // TODO: This is not robust and needs to be replaced by more intelligently using the message queue server.
    private val undeliveredMessages = CopyOnWriteArrayList<Message>()

    init {
        require(directory.fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
    }

    fun start() = mutex.locked {
        if (!running) {
            configureAndStartClient()
            running = true
        }
    }

    private fun configureAndStartClient() {
        log.info("Connecting to server: $serverHostPort")
        // Connect to our server.
        clientFactory = ActiveMQClient.createServerLocatorWithoutHA(
                tcpTransport(ConnectionDirection.OUTBOUND, serverHostPort.hostText, serverHostPort.port)).createSessionFactory()

        // Create a queue on which to receive messages and set up the handler.
        val session = clientFactory.createSession()
        this.session = session

        val address = myHostPort.toString()
        val queueName = myHostPort.toString()
        session.createQueue(address, queueName, false)
        consumer = session.createConsumer(queueName).setMessageHandler { message: ClientMessage -> handleIncomingMessage(message) }
        session.start()
    }

    private fun handleIncomingMessage(message: ClientMessage) {
        // This code runs for every inbound message.
        try {
            if (!message.containsProperty(TOPIC_PROPERTY)) {
                log.warn("Received message without a $TOPIC_PROPERTY property, ignoring")
                return
            }
            if (!message.containsProperty(SESSION_ID_PROPERTY)) {
                log.warn("Received message without a $SESSION_ID_PROPERTY property, ignoring")
                return
            }
            val topic = message.getStringProperty(TOPIC_PROPERTY)
            val sessionID = message.getLongProperty(SESSION_ID_PROPERTY)

            val body = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }

            val msg = object : Message {
                override val topicSession = TopicSession(topic, sessionID)
                override val data: ByteArray = body
                override val debugTimestamp: Instant = Instant.ofEpochMilli(message.timestamp)
                override val debugMessageID: String = message.messageID.toString()
                override fun serialise(): ByteArray = body
                override fun toString() = topic + "#" + String(data)
            }

            deliverMessage(msg)
        } finally {
            // TODO the message is delivered onto an executor and so we may be acking the message before we've
            // finished processing it
            message.acknowledge()
        }
    }

    private fun deliverMessage(msg: Message): Boolean {
        // Because handlers is a COW list, the loop inside filter will operate on a snapshot. Handlers being added
        // or removed whilst the filter is executing will not affect anything.
        val deliverTo = handlers.filter { it.topicSession.isBlank() || it.topicSession == msg.topicSession }

        if (deliverTo.isEmpty()) {
            // This should probably be downgraded to a trace in future, so the protocol can evolve with new topics
            // without causing log spam.
            log.warn("Received message for ${msg.topicSession} that doesn't have any registered handlers yet")

            // This is a hack; transient messages held in memory isn't crash resistant.
            // TODO: Use Artemis API more effectively so we don't pop messages off a queue that we aren't ready to use.
            undeliveredMessages += msg

            return false
        }

        for (handler in deliverTo) {
            (handler.executor ?: defaultExecutor).execute {
                try {
                    handler.callback(msg, handler)
                } catch(e: Exception) {
                    log.error("Caught exception whilst executing message handler for ${msg.topicSession}", e)
                }
            }
        }

        return true
    }

    override fun stop() = mutex.locked {
        for (producer in producers.values) producer.close()
        producers.clear()
        consumer?.close()
        session?.close()
        // We expect to be garbage collected shortly after being stopped, so we don't null anything explicitly here.
        running = false
    }

    override fun send(message: Message, target: MessageRecipients) {
        if (target !is Address)
            TODO("Only simple sends to single recipients are currently implemented")
        val artemisMessage = session!!.createMessage(true).apply {
            val sessionID = message.topicSession.sessionID
            putStringProperty(TOPIC_PROPERTY, message.topicSession.topic)
            putLongProperty(SESSION_ID_PROPERTY, sessionID)
            writeBodyBufferBytes(message.data)
        }
        getProducerForAddress(target).send(artemisMessage)
    }

    private fun getProducerForAddress(address: Address): ClientProducer {
        return mutex.locked {
            producers.getOrPut(address) {
                if (address != myAddress) {
                    maybeCreateQueue(address.hostAndPort)
                }
                session!!.createProducer(address.hostAndPort.toString())
            }
        }
    }

    private fun maybeCreateQueue(hostAndPort: HostAndPort) {
        val name = hostAndPort.toString()
        val queueQuery = session!!.queueQuery(SimpleString(name))
        if (!queueQuery.isExists) {
            session!!.createQueue(name, name, true /* durable */)
        }
    }

    override fun addMessageHandler(topic: String, sessionID: Long, executor: Executor?,
                                   callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration
            = addMessageHandler(TopicSession(topic, sessionID), executor, callback)

    override fun addMessageHandler(topicSession: TopicSession,
                                   executor: Executor?,
                                   callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        require(!topicSession.isBlank()) { "Topic must not be blank, as the empty topic is a special case." }
        val handler = Handler(executor, topicSession, callback)
        handlers.add(handler)
        undeliveredMessages.removeIf { deliverMessage(it) }
        return handler
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        handlers.remove(registration)
    }

    override fun createMessage(topicSession: TopicSession, data: ByteArray): Message {
        // TODO: We could write an object that proxies directly to an underlying MQ message here and avoid copying.
        return object : Message {
            override val topicSession: TopicSession get() = topicSession
            override val data: ByteArray get() = data
            override val debugTimestamp: Instant = Instant.now()
            override fun serialise(): ByteArray = this.serialise()
            override val debugMessageID: String get() = Instant.now().toEpochMilli().toString()
            override fun toString() = topicSession.toString() + "#" + String(data)
        }
    }

    override fun createMessage(topic: String, sessionID: Long, data: ByteArray): Message
            = createMessage(TopicSession(topic, sessionID), data)
}
