package net.corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.ThreadBox
import net.corda.core.crypto.CompositeKey
import net.corda.core.messaging.*
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.opaque
import net.corda.core.success
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.RPCUserService
import net.corda.node.services.api.MessagingServiceInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent.ConnectionDirection.Outbound
import net.corda.node.utilities.*
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.Message.HDR_VALIDATED_USER
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.bouncycastle.asn1.x500.X500Name
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.time.Instant
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import javax.annotation.concurrent.ThreadSafe

// TODO: Stop the wallet explorer and other clients from using this class and get rid of persistentInbox

/**
 * This class implements the [MessagingService] API using Apache Artemis, the successor to their ActiveMQ product.
 * Artemis is a message queue broker and here we run a client connecting to the specified broker instance
 * [ArtemisMessagingServer]. It's primarily concerned with peer-to-peer messaging.
 *
 * Message handlers are run on the provided [AffinityExecutor] synchronously, that is, the Artemis callback threads
 * are blocked until the handler is scheduled and completed. This allows backpressure to propagate from the given
 * executor through into Artemis and from there, back through to senders.
 *
 * An implementation of [CordaRPCOps] can be provided. If given, clients using the CordaMQClient RPC library can
 * invoke methods on the provided implementation. There is more documentation on this in the docsite and the
 * CordaRPCClient class.
 *
 * @param serverHostPort The address of the broker instance to connect to (might be running in the same process)
 * @param myIdentity Either the public key to be used as the ArtemisMQ address and queue name for the node globally, or null to indicate
 * that this is a NetworkMapService node which will be bound globally to the name "networkmap"
 * @param executor An executor to run received message tasks upon.
 */
@ThreadSafe
class NodeMessagingClient(override val config: NodeConfiguration,
                          val serverHostPort: HostAndPort,
                          val myIdentity: CompositeKey?,
                          val executor: AffinityExecutor,
                          val database: Database,
                          val networkMapRegistrationFuture: ListenableFuture<Unit>) : ArtemisMessagingComponent(), MessagingServiceInternal {
    companion object {
        private val log = loggerFor<NodeMessagingClient>()

        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        const val TOPIC_PROPERTY = "platform-topic"
        const val SESSION_ID_PROPERTY = "session-id"
    }

    private class InnerState {
        var started = false
        var running = false
        var producer: ClientProducer? = null
        var p2pConsumer: ClientConsumer? = null
        var session: ClientSession? = null
        var clientFactory: ClientSessionFactory? = null
        var rpcDispatcher: RPCDispatcher? = null
        // Consumer for inbound client RPC messages.
        var rpcConsumer: ClientConsumer? = null
        var rpcNotificationConsumer: ClientConsumer? = null
    }

    /** A registration to handle messages of different types */
    data class Handler(val topicSession: TopicSession,
                       val callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

    /**
     * Apart from the NetworkMapService this is the only other address accessible to the node outside of lookups against the NetworkMapCache.
     */
    override val myAddress: SingleMessageRecipient = if (myIdentity != null) NodeAddress.asPeer(myIdentity, serverHostPort) else NetworkMapAddress(serverHostPort)

    private val state = ThreadBox(InnerState())
    private val handlers = CopyOnWriteArrayList<Handler>()

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}message_ids") {
        val uuid = uuidString("message_id")
    }

    private val processedMessages: MutableSet<UUID> = Collections.synchronizedSet(
        object : AbstractJDBCHashSet<UUID, Table>(Table, loadOnInit = true) {
            override fun elementFromRow(row: ResultRow): UUID = row[table.uuid]
            override fun addElementToInsert(insert: InsertStatement, entry: UUID, finalizables: MutableList<() -> Unit>) {
                insert[table.uuid] = entry
            }
        })

    fun start(rpcOps: RPCOps, userService: RPCUserService) {
        state.locked {
            check(!started) { "start can't be called twice" }
            started = true

            log.info("Connecting to server: $serverHostPort")
            // TODO Add broker CN to config for host verification in case the embedded broker isn't used
            val tcpTransport = tcpTransport(Outbound(), serverHostPort.hostText, serverHostPort.port)
            val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport)
            clientFactory = locator.createSessionFactory()

            // Login using the node username. The broker will authentiate us as its node (as opposed to another peer)
            // using our TLS certificate.
            // Note that the acknowledgement of messages is not flushed to the Artermis journal until the default buffer
            // size of 1MB is acknowledged.
            val session = clientFactory!!.createSession(NODE_USER, NODE_USER, false, true, true, locator.isPreAcknowledge, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
            this.session = session
            session.start()

            // Create a general purpose producer.
            producer = session.createProducer()

            // Create a queue, consumer and producer for handling P2P network messages.
            p2pConsumer = makeP2PConsumer(session, true)
            networkMapRegistrationFuture.success {
                state.locked {
                    log.info("Network map is complete, so removing filter from P2P consumer.")
                    try {
                        p2pConsumer!!.close()
                    } catch(e: ActiveMQObjectClosedException) {
                        // Ignore it: this can happen if the server has gone away before we do.
                    }
                    p2pConsumer = makeP2PConsumer(session, false)
                }
            }

            rpcConsumer = session.createConsumer(RPC_REQUESTS_QUEUE)
            rpcNotificationConsumer = session.createConsumer(RPC_QUEUE_REMOVALS_QUEUE)
            rpcDispatcher = createRPCDispatcher(rpcOps, userService, config.myLegalName)
        }
    }

    /**
     * We make the consumer twice, once to filter for just network map messages, and then once that is complete, we close
     * the original and make another without a filter.  We do this so that there is a network map in place for all other
     * message handlers.
     */
    private fun makeP2PConsumer(session: ClientSession, networkMapOnly: Boolean): ClientConsumer {
        return if (networkMapOnly) {
            // Filter for just the network map messages.
            val messageFilter = "hyphenated_props:$TOPIC_PROPERTY like 'platform.network_map.%'"
            session.createConsumer(P2P_QUEUE, messageFilter)
        } else
            session.createConsumer(P2P_QUEUE)
    }

    private var shutdownLatch = CountDownLatch(1)

    private fun processMessage(consumer: ClientConsumer): Boolean {
        // Two possibilities here:
        //
        // 1. We block waiting for a message and the consumer is closed in another thread. In this case
        //    receive returns null and we break out of the loop.
        // 2. We receive a message and process it, and stop() is called during delivery. In this case,
        //    calling receive will throw and we break out of the loop.
        //
        // It's safe to call into receive simultaneous with other threads calling send on a producer.
        val artemisMessage: ClientMessage = try {
            consumer.receive()
        } catch(e: ActiveMQObjectClosedException) {
            null
        } ?: return false

        val message: ReceivedMessage? = artemisToCordaMessage(artemisMessage)
        if (message != null)
            deliver(message)

        // Ack the message so it won't be redelivered. We should only really do this when there were no
        // transient failures. If we caught an exception in the handler, we could back off and retry delivery
        // a few times before giving up and redirecting the message to a dead-letter address for admin or
        // developer inspection. Artemis has the features to do this for us, we just need to enable them.
        //
        // TODO: Setup Artemis delayed redelivery and dead letter addresses.
        //
        // ACKing a message calls back into the session which isn't thread safe, so we have to ensure it
        // doesn't collide with a send here. Note that stop() could have been called whilst we were
        // processing a message but if so, it'll be parked waiting for us to count down the latch, so
        // the session itself is still around and we can still ack messages as a result.
        state.locked {
            artemisMessage.acknowledge()
        }
        return true
    }

    private fun runPreNetworkMap() {
        val consumer = state.locked {
            check(started) { "start must be called first" }
            check(!running) { "run can't be called twice" }
            running = true
            rpcDispatcher!!.start(rpcConsumer!!, rpcNotificationConsumer!!, executor)
            p2pConsumer!!
        }

        while (!networkMapRegistrationFuture.isDone && processMessage(consumer)) {
        }
    }

    private fun runPostNetworkMap() {
        val consumer = state.locked {
            // If it's null, it means we already called stop, so return immediately.
            p2pConsumer ?: return
        }

        while (processMessage(consumer)) {
        }
    }

    /**
     * Starts the p2p event loop: this method only returns once [stop] has been called.
     *
     * This actually runs as two sequential loops. The first subscribes for and receives only network map messages until
     * we get our network map fetch response.  At that point the filtering consumer is closed and we proceed to the second loop and
     * consume all messages via a new consumer without a filter applied.
     */
    fun run() {
        // Build the network map.
        runPreNetworkMap()
        // Process everything else once we have the network map.
        runPostNetworkMap()
        shutdownLatch.countDown()
    }

    private fun artemisToCordaMessage(message: ClientMessage): ReceivedMessage? {
        try {
            if (!message.containsProperty(TOPIC_PROPERTY)) {
                log.warn("Received message without a $TOPIC_PROPERTY property, ignoring")
                return null
            }
            if (!message.containsProperty(SESSION_ID_PROPERTY)) {
                log.warn("Received message without a $SESSION_ID_PROPERTY property, ignoring")
                return null
            }
            val topic = message.getStringProperty(TOPIC_PROPERTY)
            val sessionID = message.getLongProperty(SESSION_ID_PROPERTY)
            // Use the magic deduplication property built into Artemis as our message identity too
            val uuid = UUID.fromString(message.getStringProperty(HDR_DUPLICATE_DETECTION_ID))
            val user = requireNotNull(message.getStringProperty(HDR_VALIDATED_USER)) { "Message is not authenticated" }
            log.info("Received message from: ${message.address} user: $user topic: $topic sessionID: $sessionID uuid: $uuid")

            val body = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }

            val msg = object : ReceivedMessage {
                override val topicSession = TopicSession(topic, sessionID)
                override val data: ByteArray = body
                override val peer: X500Name = X500Name(user)
                override val debugTimestamp: Instant = Instant.ofEpochMilli(message.timestamp)
                override val uniqueMessageId: UUID = uuid
                override fun toString() = "$topic#${data.opaque()}"
            }

            return msg
        } catch (e: Exception) {
            log.error("Internal error whilst reading MQ message", e)
            return null
        }
    }

    private fun deliver(msg: ReceivedMessage): Boolean {
        state.checkNotLocked()
        // Because handlers is a COW list, the loop inside filter will operate on a snapshot. Handlers being added
        // or removed whilst the filter is executing will not affect anything.
        val deliverTo = handlers.filter { it.topicSession.isBlank() || it.topicSession == msg.topicSession }
        try {
            // This will perform a BLOCKING call onto the executor. Thus if the handlers are slow, we will
            // be slow, and Artemis can handle that case intelligently. We don't just invoke the handler
            // directly in order to ensure that we have the features of the AffinityExecutor class throughout
            // the bulk of the codebase and other non-messaging jobs can be scheduled onto the server executor
            // easily.
            //
            // Note that handlers may re-enter this class. We aren't holding any locks and methods like
            // start/run/stop have re-entrancy assertions at the top, so it is OK.
            executor.fetchFrom {
                databaseTransaction(database) {
                    if (msg.uniqueMessageId in processedMessages) {
                        log.trace { "Discard duplicate message ${msg.uniqueMessageId} for ${msg.topicSession}" }
                    } else {
                        if (deliverTo.isEmpty()) {
                            // TODO: Implement dead letter queue, and send it there.
                            log.warn("Received message ${msg.uniqueMessageId} for ${msg.topicSession} that doesn't have any registered handlers yet")
                        } else {
                            callHandlers(msg, deliverTo)
                        }
                        // TODO We will at some point need to decide a trimming policy for the id's
                        processedMessages += msg.uniqueMessageId
                    }
                }
            }
        } catch(e: Exception) {
            log.error("Caught exception whilst executing message handler for ${msg.topicSession}", e)
        }
        return true
    }

    private fun callHandlers(msg: ReceivedMessage, deliverTo: List<Handler>) {
        for (handler in deliverTo) {
            handler.callback(msg, handler)
        }
    }

    override fun stop() {
        val running = state.locked {
            // We allow stop() to be called without a run() in between, but it must have at least been started.
            check(started)
            val prevRunning = running
            running = false
            val c = p2pConsumer ?: throw IllegalStateException("stop can't be called twice")
            try {
                c.close()
            } catch(e: ActiveMQObjectClosedException) {
                // Ignore it: this can happen if the server has gone away before we do.
            }
            p2pConsumer = null
            prevRunning
        }
        if (running && !executor.isOnThread) {
            // Wait for the main loop to notice the consumer has gone and finish up.
            shutdownLatch.await()
        }
        // Only first caller to gets running true to protect against double stop, which seems to happen in some integration tests.
        if (running) {
            state.locked {
                rpcConsumer?.close()
                rpcConsumer = null
                rpcNotificationConsumer?.close()
                rpcNotificationConsumer = null
                producer?.close()
                producer = null
                // Ensure any trailing messages are committed to the journal
                session!!.commit()
                // Closing the factory closes all the sessions it produced as well.
                clientFactory!!.close()
                clientFactory = null
            }
        }
    }

    override fun send(message: Message, target: MessageRecipients) {
        state.locked {
            val mqAddress = getMQAddress(target)
            val artemisMessage = session!!.createMessage(true).apply {
                val sessionID = message.topicSession.sessionID
                putStringProperty(TOPIC_PROPERTY, message.topicSession.topic)
                putLongProperty(SESSION_ID_PROPERTY, sessionID)
                writeBodyBufferBytes(message.data)
                // Use the magic deduplication property built into Artemis as our message identity too
                putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(message.uniqueMessageId.toString()))
            }

            log.info("Send to: $mqAddress topic: ${message.topicSession.topic} sessionID: ${message.topicSession.sessionID} " +
                    "uuid: ${message.uniqueMessageId}")
            producer!!.send(mqAddress, artemisMessage)
        }
    }

    private fun getMQAddress(target: MessageRecipients): String {
        return if (target == myAddress) {
            // If we are sending to ourselves then route the message directly to our P2P queue.
            P2P_QUEUE
        } else {
            // Otherwise we send the message to an internal queue for the target residing on our broker. It's then the
            // broker's job to route the message to the target's P2P queue.
            // TODO Make sure that if target is a service that we're part of and the broker routes the message back to us
            // it doesn't cause any issues.
            val internalTargetQueue = (target as? ArtemisAddress)?.queueName ?: throw IllegalArgumentException("Not an Artemis address")
            createQueueIfAbsent(internalTargetQueue)
            internalTargetQueue
        }
    }

    /** Attempts to create a durable queue on the broker which is bound to an address of the same name. */
    private fun createQueueIfAbsent(queueName: String) {
        state.alreadyLocked {
            val queueQuery = session!!.queueQuery(SimpleString(queueName))
            if (!queueQuery.isExists) {
                log.info("Create fresh queue $queueName bound on same address")
                session!!.createQueue(queueName, queueName, true)
            }
        }
    }

    override fun addMessageHandler(topic: String,
                                   sessionID: Long,
                                   callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        return addMessageHandler(TopicSession(topic, sessionID), callback)
    }

    override fun addMessageHandler(topicSession: TopicSession,
                                   callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        require(!topicSession.isBlank()) { "Topic must not be blank, as the empty topic is a special case." }
        val handler = Handler(topicSession, callback)
        handlers.add(handler)
        return handler
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        handlers.remove(registration)
    }

    override fun createMessage(topicSession: TopicSession, data: ByteArray, uuid: UUID): Message {
        // TODO: We could write an object that proxies directly to an underlying MQ message here and avoid copying.
        return object : Message {
            override val topicSession: TopicSession = topicSession
            override val data: ByteArray = data
            override val debugTimestamp: Instant = Instant.now()
            override val uniqueMessageId: UUID = uuid
            override fun toString() = "$topicSession#${String(data)}"
        }
    }

    private fun createRPCDispatcher(ops: RPCOps, userService: RPCUserService, nodeLegalName: String) =
            object : RPCDispatcher(ops, userService, nodeLegalName) {
                override fun send(data: SerializedBytes<*>, toAddress: String) {
                    state.locked {
                        val msg = session!!.createMessage(false).apply {
                            writeBodyBufferBytes(data.bytes)
                            // Use the magic deduplication property built into Artemis as our message identity too
                            putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
                        }
                        producer!!.send(toAddress, msg)
                    }
                }
            }

    override fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients {
        return when (partyInfo) {
            is PartyInfo.Node -> partyInfo.node.address
            is PartyInfo.Service -> ArtemisMessagingComponent.ServiceAddress(partyInfo.service.identity.owningKey)
        }
    }
}
