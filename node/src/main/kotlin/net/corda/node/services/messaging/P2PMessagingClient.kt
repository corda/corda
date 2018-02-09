package net.corda.node.services.messaging

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.PartyInfo
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.*
import net.corda.node.VersionInfo
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.StateMachineManagerImpl
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.PersistentMap
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.*
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_CONTROL
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_NOTIFY
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.bridging.BridgeControl
import net.corda.nodeapi.internal.bridging.BridgeEntry
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.Message.*
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import rx.Subscription
import java.security.PublicKey
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

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
 * @param config The configuration of the node, which is used for controlling the message redelivery options.
 * @param versionInfo All messages from the node carry the version info and received messages are checked against this for compatibility.
 * @param serverAddress The host and port of the Artemis broker.
 * @param myIdentity The primary identity of the node, which defines the messaging address for externally received messages.
 * It is also used to construct the myAddress field, which is ultimately advertised in the network map.
 * @param serviceIdentity An optional second identity if the node is also part of a group address, for example a notary.
 * @param nodeExecutor The received messages are marshalled onto the server executor to prevent Netty buffers leaking during fiber suspends.
 * @param database The nodes database, which is used to deduplicate messages.
 * @param advertisedAddress The externally advertised version of the Artemis broker address used to construct myAddress and included
 * in the network map data.
 * @param maxMessageSize A bound applied to the message size.
 */
@ThreadSafe
class P2PMessagingClient(config: NodeConfiguration,
                         private val versionInfo: VersionInfo,
                         serverAddress: NetworkHostAndPort,
                         private val myIdentity: PublicKey,
                         private val serviceIdentity: PublicKey?,
                         private val nodeExecutor: AffinityExecutor.ServiceAffinityExecutor,
                         private val database: CordaPersistence,
                         private val networkMap: NetworkMapCacheInternal,
                         advertisedAddress: NetworkHostAndPort = serverAddress,
                         maxMessageSize: Int
) : SingletonSerializeAsToken(), MessagingService {
    companion object {
        private val log = contextLogger()
        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        private val topicProperty = SimpleString("platform-topic")
        private val cordaVendorProperty = SimpleString("corda-vendor")
        private val releaseVersionProperty = SimpleString("release-version")
        private val platformVersionProperty = SimpleString("platform-version")
        private val amqDelayMillis = System.getProperty("amq.delivery.delay.ms", "0").toInt()
        private val messageMaxRetryCount: Int = 3

        fun createProcessedMessage(): AppendOnlyPersistentMap<String, Instant, ProcessedMessage, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = { Pair(it.uuid, it.insertionTime) },
                    toPersistentEntity = { key: String, value: Instant ->
                        ProcessedMessage().apply {
                            uuid = key
                            insertionTime = value
                        }
                    },
                    persistentEntityClass = ProcessedMessage::class.java
            )
        }

        fun createMessageToRedeliver(): PersistentMap<Long, Pair<Message, MessageRecipients>, RetryMessage, Long> {
            return PersistentMap(
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = {
                        Pair(it.key,
                                Pair(it.message.deserialize(context = SerializationDefaults.STORAGE_CONTEXT),
                                        it.recipients.deserialize(context = SerializationDefaults.STORAGE_CONTEXT))
                        )
                    },
                    toPersistentEntity = { _key: Long, (_message: Message, _recipient: MessageRecipients): Pair<Message, MessageRecipients> ->
                        RetryMessage().apply {
                            key = _key
                            message = _message.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                            recipients = _recipient.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                        }
                    },
                    persistentEntityClass = RetryMessage::class.java
            )
        }

        private class NodeClientMessage(override val topic: String, override val data: ByteSequence, override val uniqueMessageId: String) : Message {
            override val debugTimestamp: Instant = Instant.now()
            override fun toString() = "$topic#${String(data.bytes)}"
        }
    }

    private class InnerState {
        var running = false
        var p2pConsumer: ClientConsumer? = null
        var serviceConsumer: ClientConsumer? = null
        var bridgeNotifyConsumer: ClientConsumer? = null
        var networkChangeSubscription: Subscription? = null
    }

    private val messagesToRedeliver = database.transaction {
        createMessageToRedeliver()
    }

    private val scheduledMessageRedeliveries = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    /** A registration to handle messages of different types */
    data class Handler(val topic: String,
                       val callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

    private val cordaVendor = SimpleString(versionInfo.vendor)
    private val releaseVersion = SimpleString(versionInfo.releaseVersion)
    /** An executor for sending messages */
    private val messagingExecutor = AffinityExecutor.ServiceAffinityExecutor("Messaging ${myIdentity.toStringShort()}", 1)

    override val myAddress: SingleMessageRecipient = NodeAddress(myIdentity, advertisedAddress)
    private val messageRedeliveryDelaySeconds = config.messageRedeliveryDelaySeconds.toLong()
    private val artemis = ArtemisMessagingClient(config, serverAddress, maxMessageSize)
    private val state = ThreadBox(InnerState())
    private val knownQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val handlers = CopyOnWriteArrayList<Handler>()

    private val processedMessages = createProcessedMessage()

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}message_ids")
    class ProcessedMessage(
            @Id
            @Column(name = "message_id", length = 64)
            var uuid: String = "",

            @Column(name = "insertion_time")
            var insertionTime: Instant = Instant.now()
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}message_retry")
    class RetryMessage(
            @Id
            @Column(name = "message_id", length = 64)
            var key: Long = 0,

            @Lob
            @Column
            var message: ByteArray = EMPTY_BYTE_ARRAY,
            @Lob
            @Column
            var recipients: ByteArray = EMPTY_BYTE_ARRAY
    )

    fun start() {
        state.locked {
            val session = artemis.start().session
            val inbox = RemoteInboxAddress(myIdentity).queueName
            val inboxes = mutableListOf(inbox)
            // Create a queue, consumer and producer for handling P2P network messages.
            createQueueIfAbsent(inbox)
            p2pConsumer = session.createConsumer(inbox)
            if (serviceIdentity != null) {
                val serviceAddress = RemoteInboxAddress(serviceIdentity).queueName
                inboxes += serviceAddress
                createQueueIfAbsent(serviceAddress)
                val serviceHandler = session.createConsumer(serviceAddress)
                serviceHandler.setMessageHandler { msg ->
                    val message: ReceivedMessage? = artemisToCordaMessage(msg)
                    if (message != null)
                        deliver(message)
                    state.locked {
                        msg.individualAcknowledge()
                    }
                }
            }
            registerBridgeControl(session, inboxes)
            enumerateBridges(session, inboxes)
        }

        resumeMessageRedelivery()
    }

    private fun InnerState.registerBridgeControl(session: ClientSession, inboxes: List<String>) {
        val bridgeNotifyQueue = "$BRIDGE_NOTIFY.${myIdentity.toStringShort()}"
        session.createTemporaryQueue(BRIDGE_NOTIFY, RoutingType.MULTICAST, bridgeNotifyQueue)
        val bridgeConsumer = session.createConsumer(bridgeNotifyQueue)
        bridgeNotifyConsumer = bridgeConsumer
        bridgeConsumer.setMessageHandler { msg ->
            state.locked {
                val data: ByteArray = ByteArray(msg.bodySize).apply { msg.bodyBuffer.readBytes(this) }
                val notifyMessage = data.deserialize<BridgeControl>(context = SerializationDefaults.P2P_CONTEXT)
                log.info(notifyMessage.toString())
                when (notifyMessage) {
                    is BridgeControl.BridgeToNodeSnapshotRequest -> enumerateBridges(session, inboxes)
                    else -> log.error("Unexpected Bridge Control message type on notify topc $notifyMessage")
                }
                msg.acknowledge()
            }
        }
        networkChangeSubscription = networkMap.changed.subscribe { updateBridgesOnNetworkChange(it) }
    }

    private fun sendBridgeControl(message: BridgeControl) {
        val client = artemis.started!!
        val controlPacket = message.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
        val artemisMessage = client.session.createMessage(false)
        artemisMessage.writeBodyBufferBytes(controlPacket)
        client.producer.send(BRIDGE_CONTROL, artemisMessage)
    }

    private fun updateBridgesOnNetworkChange(change: NetworkMapCache.MapChange) {
        log.info("Updating bridges on network map change: ${change.node}")
        fun gatherAddresses(node: NodeInfo): Sequence<BridgeEntry> {
            return node.legalIdentitiesAndCerts.map {
                val messagingAddress = NodeAddress(it.party.owningKey, node.addresses.first())
                BridgeEntry(messagingAddress.queueName, node.addresses, node.legalIdentities.map { it.name })
            }.filter { artemis.started!!.session.queueQuery(SimpleString(it.queueName)).isExists }.asSequence()
        }

        fun deployBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .forEach {
                        sendBridgeControl(BridgeControl.Create(myIdentity.toStringShort(), it))
                    }
        }

        fun destroyBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .forEach {
                        sendBridgeControl(BridgeControl.Delete(myIdentity.toStringShort(), it))
                    }
        }

        when (change) {
            is NetworkMapCache.MapChange.Added -> {
                deployBridges(change.node)
            }
            is NetworkMapCache.MapChange.Removed -> {
                destroyBridges(change.node)
            }
            is NetworkMapCache.MapChange.Modified -> {
                destroyBridges(change.previousNode)
                deployBridges(change.node)
            }
        }
    }

    private fun enumerateBridges(session: ClientSession, inboxes: List<String>) {
        val requiredBridges = mutableListOf<BridgeEntry>()
        fun createBridgeEntry(queueName: SimpleString) {
            val keyHash = queueName.substring(PEERS_PREFIX.length)
            val peers = networkMap.getNodesByOwningKeyIndex(keyHash)
            for (node in peers) {
                val bridge = BridgeEntry(queueName.toString(), node.addresses, node.legalIdentities.map { it.name })
                requiredBridges += bridge
                knownQueues += queueName.toString()
            }
        }

        val queues = session.addressQuery(SimpleString("$PEERS_PREFIX#")).queueNames
        for (queue in queues) {
            createBridgeEntry(queue)
        }
        val startupMessage = BridgeControl.NodeToBridgeSnapshot(myIdentity.toStringShort(), inboxes, requiredBridges)
        sendBridgeControl(startupMessage)
    }

    private fun resumeMessageRedelivery() {
        messagesToRedeliver.forEach { retryId, (message, target) ->
            send(message, target, retryId)
        }
    }

    private val shutdownLatch = CountDownLatch(1)

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
        } catch (e: ActiveMQObjectClosedException) {
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

    /**
     * Starts the p2p event loop: this method only returns once [stop] has been called.
     */
    fun run() {
        try {
            val consumer = state.locked {
                check(artemis.started != null) { "start must be called first" }
                check(!running) { "run can't be called twice" }
                running = true
                // If it's null, it means we already called stop, so return immediately.
                p2pConsumer ?: return
            }

            while (processMessage(consumer)) { }
        } finally {
            shutdownLatch.countDown()
        }
    }

    private fun artemisToCordaMessage(message: ClientMessage): ReceivedMessage? {
        try {
            val topic = message.required(topicProperty) { getStringProperty(it) }
            val user = requireNotNull(message.getStringProperty(HDR_VALIDATED_USER)) { "Message is not authenticated" }
            val platformVersion = message.required(platformVersionProperty) { getIntProperty(it) }
            // Use the magic deduplication property built into Artemis as our message identity too
            val uuid = message.required(HDR_DUPLICATE_DETECTION_ID) { message.getStringProperty(it) }
            log.info("Received message from: ${message.address} user: $user topic: $topic uuid: $uuid")

            return ArtemisReceivedMessage(topic, CordaX500Name.parse(user), platformVersion, uuid, message)
        } catch (e: Exception) {
            log.error("Unable to process message, ignoring it: $message", e)
            return null
        }
    }

    private inline fun <T> ClientMessage.required(key: SimpleString, extractor: ClientMessage.(SimpleString) -> T): T {
        require(containsProperty(key)) { "Missing $key" }
        return extractor(key)
    }

    private class ArtemisReceivedMessage(override val topic: String,
                                         override val peer: CordaX500Name,
                                         override val platformVersion: Int,
                                         override val uniqueMessageId: String,
                                         private val message: ClientMessage) : ReceivedMessage {
        override val data: ByteSequence by lazy { OpaqueBytes(ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }) }
        override val debugTimestamp: Instant get() = Instant.ofEpochMilli(message.timestamp)
        override fun toString() = "$topic#$data"
    }

    private fun deliver(msg: ReceivedMessage): Boolean {
        state.checkNotLocked()
        // Because handlers is a COW list, the loop inside filter will operate on a snapshot. Handlers being added
        // or removed whilst the filter is executing will not affect anything.
        val deliverTo = handlers.filter { it.topic.isBlank() || it.topic== msg.topic }
        try {
            // This will perform a BLOCKING call onto the executor. Thus if the handlers are slow, we will
            // be slow, and Artemis can handle that case intelligently. We don't just invoke the handler
            // directly in order to ensure that we have the features of the AffinityExecutor class throughout
            // the bulk of the codebase and other non-messaging jobs can be scheduled onto the server executor
            // easily.
            //
            // Note that handlers may re-enter this class. We aren't holding any locks and methods like
            // start/run/stop have re-entrancy assertions at the top, so it is OK.
            nodeExecutor.fetchFrom {
                database.transaction {
                    if (msg.uniqueMessageId in processedMessages) {
                        log.trace { "Discard duplicate message ${msg.uniqueMessageId} for ${msg.topic}" }
                    } else {
                        if (deliverTo.isEmpty()) {
                            // TODO: Implement dead letter queue, and send it there.
                            log.warn("Received message ${msg.uniqueMessageId} for ${msg.topic} that doesn't have any registered handlers yet")
                        } else {
                            callHandlers(msg, deliverTo)
                        }
                        // TODO We will at some point need to decide a trimming policy for the id's
                        processedMessages[msg.uniqueMessageId] = Instant.now()
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Caught exception whilst executing message handler for ${msg.topic}", e)
        }
        return true
    }

    private fun callHandlers(msg: ReceivedMessage, deliverTo: List<Handler>) {
        for (handler in deliverTo) {
            handler.callback(msg, handler)
        }
    }

    /**
     * Initiates shutdown: if called from a thread that isn't controlled by the executor passed to the constructor
     * then this will block until all in-flight messages have finished being handled and acknowledged. If called
     * from a thread that's a part of the [net.corda.node.utilities.AffinityExecutor] given to the constructor,
     * it returns immediately and shutdown is asynchronous.
     */
    fun stop() {
        val running = state.locked {
            // We allow stop() to be called without a run() in between, but it must have at least been started.
            check(artemis.started != null)
            val prevRunning = running
            running = false
            networkChangeSubscription?.unsubscribe()
            val c = p2pConsumer ?: throw IllegalStateException("stop can't be called twice")
            try {
                c.close()
            } catch (e: ActiveMQObjectClosedException) {
                // Ignore it: this can happen if the server has gone away before we do.
            }
            try {
                bridgeNotifyConsumer!!.close()
            } catch (e: ActiveMQObjectClosedException) {
                // Ignore it: this can happen if the server has gone away before we do.
            }
            p2pConsumer = null
            val s = serviceConsumer
            try {
                s?.close()
            } catch (e: ActiveMQObjectClosedException) {
                // Ignore it: this can happen if the server has gone away before we do.
            }
            serviceConsumer = null
            knownQueues.clear()
            prevRunning
        }
        if (running && !nodeExecutor.isOnThread) {
            // Wait for the main loop to notice the consumer has gone and finish up.
            shutdownLatch.await()
        }
        // Only first caller to gets running true to protect against double stop, which seems to happen in some integration tests.
        if (running) {
            state.locked {
                artemis.stop()
            }
        }
    }

    override fun send(message: Message, target: MessageRecipients, retryId: Long?, sequenceKey: Any) {
        // We have to perform sending on a different thread pool, since using the same pool for messaging and
        // fibers leads to Netty buffer memory leaks, caused by both Netty and Quasar fiddling with thread-locals.
        messagingExecutor.fetchFrom {
            state.locked {
                val mqAddress = getMQAddress(target)
                val artemis = artemis.started!!
                val artemisMessage = artemis.session.createMessage(true).apply {
                    putStringProperty(cordaVendorProperty, cordaVendor)
                    putStringProperty(releaseVersionProperty, releaseVersion)
                    putIntProperty(platformVersionProperty, versionInfo.platformVersion)
                    putStringProperty(topicProperty, SimpleString(message.topic))
                    writeBodyBufferBytes(message.data.bytes)
                    // Use the magic deduplication property built into Artemis as our message identity too
                    putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(message.uniqueMessageId.toString()))

                    // For demo purposes - if set then add a delay to messages in order to demonstrate that the flows are doing as intended
                    if (amqDelayMillis > 0 && message.topic == StateMachineManagerImpl.sessionTopic) {
                        putLongProperty(HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + amqDelayMillis)
                    }
                }
                log.trace {
                    "Send to: $mqAddress topic: ${message.topic} uuid: ${message.uniqueMessageId}"
                }
                artemis.producer.send(mqAddress, artemisMessage)
                retryId?.let {
                    database.transaction {
                        messagesToRedeliver.computeIfAbsent(it, { Pair(message, target) })
                    }
                    scheduledMessageRedeliveries[it] = messagingExecutor.schedule({
                        sendWithRetry(0, mqAddress, artemisMessage, it)
                    }, messageRedeliveryDelaySeconds, TimeUnit.SECONDS)

                }
            }
        }
    }

    override fun send(addressedMessages: List<MessagingService.AddressedMessage>) {
        for ((message, target, retryId, sequenceKey) in addressedMessages) {
            send(message, target, retryId, sequenceKey)
        }
    }

    private fun sendWithRetry(retryCount: Int, address: String, message: ClientMessage, retryId: Long) {
        fun ClientMessage.randomiseDuplicateId() {
            putStringProperty(HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
        }

        log.trace { "Attempting to retry #$retryCount message delivery for $retryId" }
        if (retryCount >= messageMaxRetryCount) {
            log.warn("Reached the maximum number of retries ($messageMaxRetryCount) for message $message redelivery to $address")
            scheduledMessageRedeliveries.remove(retryId)
            return
        }

        message.randomiseDuplicateId()

        state.locked {
            log.trace { "Retry #$retryCount sending message $message to $address for $retryId" }
            artemis.started!!.producer.send(address, message)
        }

        scheduledMessageRedeliveries[retryId] = messagingExecutor.schedule({
            sendWithRetry(retryCount + 1, address, message, retryId)
        }, messageRedeliveryDelaySeconds, TimeUnit.SECONDS)
    }

    override fun cancelRedelivery(retryId: Long) {
        database.transaction {
            messagesToRedeliver.remove(retryId)
        }
        scheduledMessageRedeliveries[retryId]?.let {
            log.trace { "Cancelling message redelivery for retry id $retryId" }
            if (!it.isDone) it.cancel(true)
            scheduledMessageRedeliveries.remove(retryId)
        }
    }

    private fun getMQAddress(target: MessageRecipients): String {
        return if (target == myAddress) {
            // If we are sending to ourselves then route the message directly to our P2P queue.
            RemoteInboxAddress(myIdentity).queueName
        } else {
            // Otherwise we send the message to an internal queue for the target residing on our broker. It's then the
            // broker's job to route the message to the target's P2P queue.
            val internalTargetQueue = (target as? ArtemisAddress)?.queueName ?: throw IllegalArgumentException("Not an Artemis address")
            createQueueIfAbsent(internalTargetQueue)
            internalTargetQueue
        }
    }

    /** Attempts to create a durable queue on the broker which is bound to an address of the same name. */
    private fun createQueueIfAbsent(queueName: String) {
        if (!knownQueues.contains(queueName)) {
            state.alreadyLocked {
                val session = artemis.started!!.session
                val queueQuery = session.queueQuery(SimpleString(queueName))
                if (!queueQuery.isExists) {
                    log.info("Create fresh queue $queueName bound on same address")
                    session.createQueue(queueName, RoutingType.ANYCAST, queueName, true)
                    if (queueName.startsWith(PEERS_PREFIX)) {
                        val keyHash = queueName.substring(PEERS_PREFIX.length)
                        val peers = networkMap.getNodesByOwningKeyIndex(keyHash)
                        for (node in peers) {
                            val bridge = BridgeEntry(queueName, node.addresses, node.legalIdentities.map { it.name })
                            val createBridgeMessage = BridgeControl.Create(myIdentity.toStringShort(), bridge)
                            sendBridgeControl(createBridgeMessage)
                        }
                    }
                }
            }
            knownQueues += queueName
        }
    }

    override fun addMessageHandler(topic: String,
                                   callback: (ReceivedMessage, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        require(!topic.isBlank()) { "Topic must not be blank, as the empty topic is a special case." }
        val handler = Handler(topic, callback)
        handlers.add(handler)
        return handler
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        handlers.remove(registration)
    }

    override fun createMessage(topic: String, data: ByteArray, deduplicationId: String): Message {
        // TODO: We could write an object that proxies directly to an underlying MQ message here and avoid copying.
        return NodeClientMessage(topic, OpaqueBytes(data), deduplicationId)
    }

    // TODO Rethink PartyInfo idea and merging PeerAddress/ServiceAddress (the only difference is that Service address doesn't hold host and port)
    override fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients {
        return when (partyInfo) {
            is PartyInfo.SingleNode -> NodeAddress(partyInfo.party.owningKey, partyInfo.addresses.single())
            is PartyInfo.DistributedNode -> ServiceAddress(partyInfo.party.owningKey)
        }
    }
}
