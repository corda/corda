package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import com.codahale.metrics.Clock
import com.codahale.metrics.MetricRegistry
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.NamedCacheFactory
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
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.*
import net.corda.node.VersionInfo
import net.corda.node.internal.LifecycleSupport
import net.corda.node.internal.artemis.ReactiveArtemisConsumer
import net.corda.node.internal.artemis.ReactiveArtemisConsumer.Companion.multiplex
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.SenderDeduplicationId
import net.corda.node.utilities.AffinityExecutor
import net.corda.node.utilities.currentFlowId
import net.corda.node.utilities.errorAndTerminate
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.*
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_CONTROL
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_NOTIFY
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.JOURNAL_HEADER_SIZE
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.p2pConnectorTcpTransport
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.p2pConnectorTcpTransportFromList
import net.corda.nodeapi.internal.RoundRobinConnectionPolicy
import net.corda.nodeapi.internal.bridging.BridgeControl
import net.corda.nodeapi.internal.bridging.BridgeEntry
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.requireMessageSize
import net.corda.nodeapi.internal.stillOpen
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID
import org.apache.activemq.artemis.api.core.Message.HDR_VALIDATED_USER
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.ThreadSafe

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
 * @param nodeExecutor The received messages are marshalled onto the server executor to prevent Netty buffers leaking during fiber suspends.
 * @param database The nodes database, which is used to deduplicate messages.
 */
@ThreadSafe
class P2PMessagingClient(val config: NodeConfiguration,
                         private val versionInfo: VersionInfo,
                         val serverAddress: NetworkHostAndPort,
                         private val nodeExecutor: AffinityExecutor.ServiceAffinityExecutor,
                         private val database: CordaPersistence,
                         private val networkMap: NetworkMapCacheInternal,
                         @Suppress("UNUSED")
                         private val metricRegistry: MetricRegistry,
                         cacheFactory: NamedCacheFactory,
                         private val isDrainingModeOn: () -> Boolean,
                         private val drainingModeWasChangedEvents: Observable<Pair<Boolean, Boolean>>,
                         platformClock: java.time.Clock
) : SingletonSerializeAsToken(), MessagingService, AddressToArtemisQueueResolver {
    companion object {
        private val log = contextLogger()
        private val detailedLogger = detailedLogger()
    }

    private class NodeClientMessage(override val topic: String,
                                    override val data: ByteSequence,
                                    override val uniqueMessageId: DeduplicationId,
                                    override val senderUUID: String?,
                                    override val additionalHeaders: Map<String, String>) : Message {
        override val debugTimestamp: Instant = Instant.now()
        override fun toString() = "$topic#${String(data.bytes)}"
    }

    private class InnerState {
        var started = false
        var running = false
        var eventsSubscription: Subscription? = null
        var p2pConsumer: P2PMessagingConsumer? = null
        var locator: ServerLocator? = null
        var executorProducer: ClientProducer? = null
        var executorSession: ClientSession? = null
        var producer: ClientProducer? = null
        var producerSession: ClientSession? = null
        var bridgeSession: ClientSession? = null
        var bridgeNotifyConsumer: ClientConsumer? = null
        var networkChangeSubscription: Subscription? = null
        var sessionFactory: ClientSessionFactory? = null
        val inboxes = mutableSetOf<String>()

        fun sendMessage(address: String, message: ClientMessage) = producer!!.send(address, message)
    }

    /** A registration to handle messages of different types */
    data class HandlerRegistration(val topic: String, val callback: Any) : MessageHandlerRegistration

    private lateinit var myIdentity: PublicKey
    private var serviceIdentity: PublicKey? = null
    private lateinit var advertisedAddress: NetworkHostAndPort
    private var maxMessageSize: Int = -1

    override val myAddress: SingleMessageRecipient get() = NodeAddress(myIdentity)
    override val ourSenderUUID = UUID.randomUUID().toString()

    private val state = ThreadBox(InnerState())
    private val knownQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val delayStartQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val externalBridge: Boolean = config.enterpriseConfiguration.externalBridge ?: false

    private val handlers = ConcurrentHashMap<String, MessageHandler>()
    private val handlersChangedSignal = java.lang.Object()

    private val deduplicator = P2PMessageDeduplicator(cacheFactory, database, platformClock)
    // Note: Public visibility for testing
    var messagingExecutor: MessagingExecutor? = null

    private fun failoverCallback(event: FailoverEventType) {
        when (event) {
            FailoverEventType.FAILURE_DETECTED -> {
                errorAndTerminate("Connection to the broker was lost. Node is shutting down.", null)
            }
            FailoverEventType.FAILOVER_COMPLETED -> {
                // Currently, this path will never be taken as we die on Artemis connection loss
                log.info("Connection to broker re-established.")
                state.locked {
                    enumerateBridges(bridgeSession!!, inboxes.toList())
                }
            }
            FailoverEventType.FAILOVER_FAILED -> state.locked {
                if (running) {
                    errorAndTerminate("Could not reconnect to the broker after ${config.enterpriseConfiguration.messagingServerConnectionConfiguration.reconnectAttempts(locator!!.isHA)} attempts. Node is shutting down.", null)
                }
            }
            else -> {
                log.warn("Cannot handle event $event.")
            }
        }
    }

    /**
     * @param myIdentity The primary identity of the node, which defines the messaging address for externally received messages.
     * It is also used to construct the myAddress field, which is ultimately advertised in the network map.
     * @param serviceIdentity An optional second identity if the node is also part of a group address, for example a notary.
     * @param advertisedAddress The externally advertised version of the Artemis broker address used to construct myAddress and included
     * in the network map data.
     * @param maxMessageSize A bound applied to the message size.
     */
    fun start(myIdentity: PublicKey, serviceIdentity: PublicKey?, maxMessageSize: Int, advertisedAddress: NetworkHostAndPort = serverAddress, legalName: String) {
        this.myIdentity = myIdentity
        this.serviceIdentity = serviceIdentity
        this.advertisedAddress = advertisedAddress
        this.maxMessageSize = maxMessageSize
        state.locked {
            require(!started) { "P2P messaging client can't be started twice" }
            started = true
            val sslOptions = if (config.messagingServerExternal) {
                config.enterpriseConfiguration.messagingServerSslConfiguration
            } else {
                config.p2pSslOptions
            }
            val tcpTransport = p2pConnectorTcpTransport(serverAddress, sslOptions)
            val backupTransports = p2pConnectorTcpTransportFromList(config.enterpriseConfiguration.messagingServerBackupAddresses, sslOptions)
            log.info("Connecting to message broker: $serverAddress")
            if (backupTransports.isNotEmpty()) {
                log.info("Back-up message broker addresses: ${config.enterpriseConfiguration.messagingServerBackupAddresses}")
            }
            // If back-up artemis addresses are configured, the locator will be created using HA mode.
            locator = ActiveMQClient.createServerLocator(backupTransports.isNotEmpty(), *(listOf(tcpTransport) + backupTransports).toTypedArray()).apply {
                // Never time out on our loopback Artemis connections. If we switch back to using the InVM transport this
                // would be the default and the two lines below can be deleted.
                connectionTTL = 60000
                clientFailureCheckPeriod = 30000
                callFailoverTimeout = 1000
                callTimeout = 1000
                minLargeMessageSize = maxMessageSize + JOURNAL_HEADER_SIZE
                isUseGlobalPools = nodeSerializationEnv != null
                confirmationWindowSize = config.enterpriseConfiguration.tuning.p2pConfirmationWindowSize
                producerWindowSize = -1
                // Configuration for dealing with external broker failover
                if (config.messagingServerExternal) {
                    connectionLoadBalancingPolicyClassName = RoundRobinConnectionPolicy::class.java.canonicalName
                    reconnectAttempts = config.enterpriseConfiguration.messagingServerConnectionConfiguration.reconnectAttempts(isHA)
                    retryInterval = config.enterpriseConfiguration.messagingServerConnectionConfiguration.retryInterval().toMillis()
                    retryIntervalMultiplier = config.enterpriseConfiguration.messagingServerConnectionConfiguration.retryIntervalMultiplier()
                    maxRetryInterval = config.enterpriseConfiguration.messagingServerConnectionConfiguration.maxRetryInterval(isHA).toMillis()
                    isFailoverOnInitialConnection = config.enterpriseConfiguration.messagingServerConnectionConfiguration.failoverOnInitialAttempt(isHA)
                    initialConnectAttempts = config.enterpriseConfiguration.messagingServerConnectionConfiguration.initialConnectAttempts(isHA)
                }
            }

            sessionFactory = locator!!.createSessionFactory().addFailoverListener(::failoverCallback)

            // Login using the node username. The broker will authenticate us as its node (as opposed to another peer)
            // using our TLS certificate.
            // Note that the acknowledgement of messages is not flushed to the Artermis journal until the default buffer
            // size of 1MB is acknowledged.
            val createNewSession = { sessionFactory!!.createSession(ArtemisMessagingComponent.NODE_P2P_USER, ArtemisMessagingComponent.NODE_P2P_USER, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE) }

            executorSession = createNewSession()
            producerSession = createNewSession()
            bridgeSession = createNewSession()
            executorSession!!.start()
            producerSession!!.start()
            bridgeSession!!.start()


            // Create a queue, consumer and producer for handling P2P network messages.
            // Create a general purpose producer.
            producer = producerSession!!.createProducer()
            executorProducer = executorSession!!.createProducer()


            inboxes += RemoteInboxAddress(myIdentity).queueName
            serviceIdentity?.let {
                inboxes += RemoteInboxAddress(it).queueName
            }

            inboxes.forEach { createQueueIfAbsent(it, producerSession!!, exclusive = true, isServiceAddress = false) }

            p2pConsumer = P2PMessagingConsumer(inboxes, createNewSession, isDrainingModeOn, drainingModeWasChangedEvents)

            val messagingExecutor = MessagingExecutor(
                    executorSession!!,
                    executorProducer!!,
                    versionInfo,
                    this@P2PMessagingClient,
                    metricRegistry,
                    queueBound = config.enterpriseConfiguration.tuning.maximumMessagingBatchSize,
                    ourSenderUUID = ourSenderUUID,
                    myLegalName = legalName,
                    enableSNI = config.enableSNI
            )
            this@P2PMessagingClient.messagingExecutor = messagingExecutor
            messagingExecutor.start()

            registerBridgeControl(bridgeSession!!, inboxes.toList())
            enumerateBridges(bridgeSession!!, inboxes.toList())
        }
    }

    private fun InnerState.registerBridgeControl(session: ClientSession, inboxes: List<String>) {
        val bridgeNotifyQueue = "$BRIDGE_NOTIFY.${myIdentity.toStringShort()}"
        if (!session.queueQuery(SimpleString(bridgeNotifyQueue)).isExists) {
            session.createTemporaryQueue(BRIDGE_NOTIFY, RoutingType.MULTICAST, bridgeNotifyQueue)
        }
        val bridgeConsumer = session.createConsumer(bridgeNotifyQueue)
        bridgeNotifyConsumer = bridgeConsumer
        bridgeConsumer.setMessageHandler { msg ->
            state.locked {
                val data: ByteArray = ByteArray(msg.bodySize).apply { msg.bodyBuffer.readBytes(this) }
                val notifyMessage = data.deserialize<BridgeControl>(context = SerializationDefaults.P2P_CONTEXT)
                log.info(notifyMessage.toString())
                when (notifyMessage) {
                    is BridgeControl.BridgeToNodeSnapshotRequest -> enumerateBridges(session, inboxes)
                    else -> log.error("Unexpected Bridge Control message type on notify topic $notifyMessage")
                }
                msg.acknowledge()
            }
        }
        networkChangeSubscription = networkMap.changed.subscribe { updateBridgesOnNetworkChange(it) }
    }

    private fun sendBridgeControl(message: BridgeControl) {
        state.locked {
            val controlPacket = message.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
            val artemisMessage = producerSession!!.createMessage(false)
            artemisMessage.writeBodyBufferBytes(controlPacket)
            sendMessage(BRIDGE_CONTROL, artemisMessage)
        }
    }

    private fun updateBridgesOnNetworkChange(change: NetworkMapCache.MapChange) {
        log.info("Updating bridges on network map change: ${change.node}")
        fun gatherAddresses(node: NodeInfo): Sequence<BridgeEntry> {
            return state.locked {
                node.legalIdentitiesAndCerts.asSequence().map {
                    val messagingAddress = NodeAddress(it.party.owningKey)
                    BridgeEntry(messagingAddress.queueName, node.addresses, node.legalIdentities.map(Party::name), serviceAddress = false)
                }.filter { producerSession!!.queueQuery(SimpleString(it.queueName)).isExists }
            }
        }

        fun deployBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .forEach {
                        sendBridgeControl(BridgeControl.Create(config.myLegalName.toString(), it))
                    }
        }

        fun destroyBridges(node: NodeInfo) {
            gatherAddresses(node)
                    .forEach {
                        sendBridgeControl(BridgeControl.Delete(config.myLegalName.toString(), it))
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
                val bridge = BridgeEntry(queueName.toString(), node.addresses, node.legalIdentities.map { it.name }, serviceAddress = false)
                requiredBridges += bridge
                knownQueues += queueName.toString()
            }
        }

        val queues = session.addressQuery(SimpleString("$PEERS_PREFIX#")).queueNames
        knownQueues.clear()
        for (queue in queues) {
            val queueQuery = session.queueQuery(queue)
            if (!config.lazyBridgeStart || queueQuery.messageCount > 0) {
                createBridgeEntry(queue)
            } else {
                delayStartQueues += queue.toString()
            }
        }
        val startupMessage = BridgeControl.NodeToBridgeSnapshot(config.myLegalName.toString(), inboxes, requiredBridges)
        sendBridgeControl(startupMessage)
    }

    private val shutdownLatch = CountDownLatch(1)

    /**
     * Starts the p2p event loop: this method only returns once [stop] has been called.
     */
    fun run() {
        val latch = CountDownLatch(1)
        try {
            synchronized(handlersChangedSignal) {
                while (handlers.isEmpty() && state.locked { (p2pConsumer != null) }) {
                    handlersChangedSignal.wait()
                }
            }
            val consumer = state.locked {
                check(started) { "start must be called first" }
                check(!running) { "run can't be called twice" }
                running = true
                // If it's null, it means we already called stop, so return immediately.
                if (p2pConsumer == null) {
                    return
                }
                eventsSubscription = p2pConsumer!!.messages
                        // this `run()` method is semantically meant to block until the message consumption runs, hence the latch here
                        .doOnCompleted(latch::countDown)
                        .subscribe({ message -> deliver(message) }, { error -> throw error })
                p2pConsumer!!
            }
            consumer.start()
            latch.await()
        } finally {
            shutdownLatch.countDown()
        }
    }

    private fun artemisToCordaMessage(message: ClientMessage): ReceivedMessage? {
        try {
            requireMessageSize(message.bodySize, maxMessageSize)
            val topic = message.required(P2PMessagingHeaders.topicProperty) { getStringProperty(it) }
            val user = requireNotNull(if (externalBridge) {
                message.getStringProperty(P2PMessagingHeaders.bridgedCertificateSubject)
                        ?: message.getStringProperty(HDR_VALIDATED_USER)
            } else {
                message.getStringProperty(HDR_VALIDATED_USER)
            }) { "Message is not authenticated" }

            val platformVersion = message.required(P2PMessagingHeaders.platformVersionProperty) { getIntProperty(it) }
            // Use the magic deduplication property built into Artemis as our message identity too
            val uniqueMessageId = message.required(HDR_DUPLICATE_DETECTION_ID) { DeduplicationId(message.getStringProperty(it)) }
            val receivedSenderUUID = message.getStringProperty(P2PMessagingHeaders.senderUUID)
            val receivedSenderSeqNo = if (message.containsProperty(P2PMessagingHeaders.senderSeqNo)) message.getLongProperty(P2PMessagingHeaders.senderSeqNo) else null
            val isSessionInit = message.getStringProperty(P2PMessagingHeaders.Type.KEY) == P2PMessagingHeaders.Type.SESSION_INIT_VALUE
            log.debug { "Received message from: ${message.address} user: $user topic: $topic id: $uniqueMessageId senderUUID: $receivedSenderUUID senderSeqNo: $receivedSenderSeqNo isSessionInit: $isSessionInit size: ${message.bodySize}" }

            return ArtemisReceivedMessage(topic, CordaX500Name.parse(user), platformVersion, uniqueMessageId, receivedSenderUUID, receivedSenderSeqNo, isSessionInit, message)
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
                                         override val uniqueMessageId: DeduplicationId,
                                         override val senderUUID: String?,
                                         override val senderSeqNo: Long?,
                                         override val isSessionInit: Boolean,
                                         private val message: ClientMessage) : ReceivedMessage {
        override val data: ByteSequence by lazy { OpaqueBytes(ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }) }
        override val debugTimestamp: Instant get() = Instant.ofEpochMilli(message.timestamp)
        override val additionalHeaders: Map<String, String> = emptyMap()
        override fun toString() = "ArtemisReceivedMessage(size=${message.bodySize}, message=$message)"
    }

    private val receiverDurationTimer = metricRegistry.timer("P2P.ReceiveDuration")
    private val receiverIntervalTimer = metricRegistry.timer("P2P.ReceiveInterval")
    private val receiveTimerClock = Clock.defaultClock()
    private var lastReceiveTimerTick = receiveTimerClock.tick
    private val receiveMessageSizeMetric = metricRegistry.histogram("P2P.ReceiveMessageSize")

    fun deliver(artemisMessage: ClientMessage) {
        val elapsed = receiveTimerClock.tick - lastReceiveTimerTick
        receiverIntervalTimer.update(elapsed, TimeUnit.NANOSECONDS)
        receiveMessageSizeMetric.update(artemisMessage.encodeSize)
        val latency = receiverDurationTimer.time()
        try {
            artemisToCordaMessage(artemisMessage)?.let { cordaMessage ->
                if (!deduplicator.isDuplicate(cordaMessage)) {
                    deduplicator.signalMessageProcessStart(cordaMessage)
                    deliver(cordaMessage, artemisMessage)
                } else {
                    log.debug { "Discard duplicate message id: ${cordaMessage.uniqueMessageId} senderUUID: ${cordaMessage.senderUUID} senderSeqNo: ${cordaMessage.senderSeqNo} isSessionInit: ${cordaMessage.isSessionInit}" }
                    messagingExecutor!!.acknowledge(artemisMessage)
                }
            }
        } finally {
            latency.stop()
            lastReceiveTimerTick = receiveTimerClock.tick
        }
    }

    private fun deliver(msg: ReceivedMessage, artemisMessage: ClientMessage) {
        detailedLogger.trace { "ReceiveMessage(size=${artemisMessage.encodeSize};id=${msg.uniqueMessageId.toString};platformVersion=${msg.platformVersion};from=${msg.peer})" }
        state.checkNotLocked()
        val deliverTo = handlers[msg.topic]
        if (deliverTo != null) {
            try {
                deliverTo(msg, HandlerRegistration(msg.topic, deliverTo), MessageDeduplicationHandler(artemisMessage, msg))
            } catch (e: Exception) {
                log.error("Caught exception whilst executing message handler for ${msg.topic}", e)
            }
        } else {
            log.warn("Received message ${msg.uniqueMessageId} for ${msg.topic} that doesn't have any registered handlers yet")
        }
    }

    private inner class MessageDeduplicationHandler(val artemisMessage: ClientMessage, override val receivedMessage: ReceivedMessage) : DeduplicationHandler, ExternalEvent.ExternalMessageEvent {
        override val externalCause: ExternalEvent
            get() = this
        override val deduplicationHandler: MessageDeduplicationHandler
            get() = this

        override fun insideDatabaseTransaction() {
            deduplicator.persistDeduplicationId(receivedMessage.uniqueMessageId)
        }

        override fun afterDatabaseTransaction() {
            deduplicator.signalMessageProcessFinish(receivedMessage.uniqueMessageId)
            messagingExecutor!!.acknowledge(artemisMessage)
        }

        override fun toString(): String {
            return "${javaClass.simpleName}(${receivedMessage.uniqueMessageId})"
        }
    }

    /**
     * Initiates shutdown: if called from a thread that isn't controlled by the executor passed to the constructor
     * then this will block until all in-flight messages have finished being handled and acknowledged. If called
     * from a thread that's a part of the [net.corda.node.utilities.AffinityExecutor] given to the constructor,
     * it returns immediately and shutdown is asynchronous.
     */
    fun stop() {
        log.info("Stopping P2PMessagingClient for: $serverAddress")
        val running = state.locked {
            // We allow stop() to be called without a run() in between, but it must have at least been started.
            check(started)
            started = false
            val prevRunning = running
            running = false
            networkChangeSubscription?.unsubscribe()
            messagingExecutor?.close()

            p2pConsumer?.let {
                close(p2pConsumer)
                p2pConsumer = null
            }

            producer?.let {
                close(producer)
                producer = null
            }

            producerSession?.let {
                if (it.stillOpen()) {
                    it.commit()
                }
            }

            executorProducer?.let {
                close(executorProducer)
                executorProducer = null
            }

            executorSession?.let {
                if (it.stillOpen()) {
                    it.commit()
                }
            }

            close(bridgeNotifyConsumer)
            knownQueues.clear()
            eventsSubscription?.unsubscribe()
            eventsSubscription = null

            // Clean-up sessionFactory
            sessionFactory?.let {
                it.removeFailoverListener(::failoverCallback)
                it.close()
            }
            prevRunning
        }
        synchronized(handlersChangedSignal) {
            handlersChangedSignal.notifyAll()
        }
        if (running && !nodeExecutor.isOnThread) {
            // Wait for the main loop to notice the consumer has gone and finish up.
            shutdownLatch.await()
        }
        state.locked {
            locator?.close()
        }
        log.info("Stopped P2PMessagingClient for: $serverAddress")
    }

    private fun close(target: AutoCloseable?) {
        try {
            target?.close()
        } catch (ignored: ActiveMQObjectClosedException) {
            // swallow
        }
    }

    override fun close() = stop()

    @Suspendable
    override fun send(message: Message, target: MessageRecipients, sequenceKey: Any) {
        requireMessageSize(message.data.size, maxMessageSize)
        detailedLogger.trace { "SendMessage(flowId=$currentFlowId;size=${message.data.size};id=${message.uniqueMessageId.toString})" }
        messagingExecutor!!.send(message, target)
    }

    @Suspendable
    override fun send(addressedMessages: List<MessagingService.AddressedMessage>) {
        for ((message, target, sequenceKey) in addressedMessages) {
            send(message, target, sequenceKey)
        }
    }

    override fun resolveTargetToArtemisQueue(address: MessageRecipients): String {
        return if (address == myAddress) {
            // If we are sending to ourselves then route the message directly to our P2P queue.
            RemoteInboxAddress(myIdentity).queueName
        } else {
            // Otherwise we send the message to an internal queue for the target residing on our broker. It's then the
            // broker's job to route the message to the target's P2P queue.
            val internalTargetQueue = (address as? ArtemisAddress)?.queueName
                    ?: throw IllegalArgumentException("Not an Artemis address")
            state.locked {
                val isServiceAddress = address is ServiceAddress
                val exclusive = if (config.enableSNI) false else !isServiceAddress
                createQueueIfAbsent(internalTargetQueue, producerSession!!, exclusive = exclusive, isServiceAddress = isServiceAddress)
            }
            internalTargetQueue
        }
    }

    /** Attempts to create a durable queue on the broker which is bound to an address of the same name. */
    private fun createQueueIfAbsent(queueName: String, session: ClientSession, exclusive: Boolean, isServiceAddress: Boolean) {
        fun sendBridgeCreateMessage() {
            val keyHash = queueName.substring(PEERS_PREFIX.length)
            val peers = networkMap.getNodesByOwningKeyIndex(keyHash)
            for (node in peers) {

                val bridge = BridgeEntry(queueName, node.addresses, node.legalIdentities.map { it.name }, isServiceAddress)
                val createBridgeMessage = BridgeControl.Create(config.myLegalName.toString(), bridge)
                sendBridgeControl(createBridgeMessage)
            }
        }
        if (!knownQueues.contains(queueName)) {
            if (delayStartQueues.contains(queueName)) {
                log.info("Start bridge for previously empty queue $queueName")
                sendBridgeCreateMessage()
                delayStartQueues -= queueName
            } else {
                val queueQuery = session.queueQuery(SimpleString(queueName))
                if (!queueQuery.isExists) {
                    log.info("Create fresh queue $queueName bound on same address")
                    session.createQueue(queueName, RoutingType.ANYCAST, queueName, null, true, false,
                            ActiveMQDefaultConfiguration.getDefaultMaxQueueConsumers(),
                            ActiveMQDefaultConfiguration.getDefaultPurgeOnNoConsumers(), exclusive, null)
                }
                // When there are multiple nodes sharing the firewall, the peer queue may already exist as it was created when
                // another node tried communicating with the target. A bridge is still needed as there has to be one per source-queue-target
                sendBridgeCreateMessage()
            }
            knownQueues += queueName
        }
    }

    override fun addMessageHandler(topic: String, callback: MessageHandler): MessageHandlerRegistration {
        require(!topic.isBlank()) { "Topic must not be blank, as the empty topic is a special case." }
        handlers.compute(topic) { _, handler ->
            if (handler != null) {
                throw IllegalStateException("Cannot add another acking handler for $topic, there is already an acking one")
            }
            callback
        }
        synchronized(handlersChangedSignal) {
            handlersChangedSignal.notifyAll()
        }
        return HandlerRegistration(topic, callback)
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        registration as HandlerRegistration
        handlers.remove(registration.topic)
    }

    override fun createMessage(topic: String, data: ByteArray, deduplicationId: SenderDeduplicationId, additionalHeaders: Map<String, String>): Message {
        return NodeClientMessage(topic, OpaqueBytes(data), deduplicationId.deduplicationId, deduplicationId.senderUUID, additionalHeaders)
    }

    override fun getAddressOfParty(partyInfo: PartyInfo): MessageRecipients {
        return when (partyInfo) {
            is PartyInfo.SingleNode -> NodeAddress(partyInfo.party.owningKey)
            is PartyInfo.DistributedNode -> ServiceAddress(partyInfo.party.owningKey)
        }
    }
}

private class P2PMessagingConsumer(
        queueNames: Set<String>,
        createSession: () -> ClientSession,
        private val isDrainingModeOn: () -> Boolean,
        private val drainingModeWasChangedEvents: Observable<Pair<Boolean, Boolean>>) : LifecycleSupport {

    private companion object {
        private const val initialSessionMessages = "${P2PMessagingHeaders.Type.KEY}<>'${P2PMessagingHeaders.Type.SESSION_INIT_VALUE}'"
    }

    private var startedFlag = false

    val messages: PublishSubject<ClientMessage> = PublishSubject.create<ClientMessage>()

    private val existingOnlyConsumer = multiplex(queueNames, createSession, initialSessionMessages)
    private val initialAndExistingConsumer = multiplex(queueNames, createSession)
    private val subscriptions = mutableSetOf<Subscription>()

    override fun start() {

        synchronized(this) {
            require(!startedFlag){"Must not already be started"}
            drainingModeWasChangedEvents.filter { change -> change.switchedOn() }.doOnNext { initialAndExistingConsumer.switchTo(existingOnlyConsumer) }.subscribe()
            drainingModeWasChangedEvents.filter { change -> change.switchedOff() }.doOnNext { existingOnlyConsumer.switchTo(initialAndExistingConsumer) }.subscribe()
            subscriptions += existingOnlyConsumer.messages.doOnNext(messages::onNext).subscribe()
            subscriptions += initialAndExistingConsumer.messages.doOnNext(messages::onNext).subscribe()
            if (isDrainingModeOn()) {
                existingOnlyConsumer.start()
            } else {
                initialAndExistingConsumer.start()
            }
            startedFlag = true
        }
    }

    override fun stop() {

        synchronized(this) {
            if (startedFlag) {
                existingOnlyConsumer.stop()
                initialAndExistingConsumer.stop()
                subscriptions.forEach(Subscription::unsubscribe)
                subscriptions.clear()
                startedFlag = false
            }
            messages.onCompleted()
        }
    }

    override val started: Boolean
        get() = startedFlag

    private fun Pair<Boolean, Boolean>.switchedOff() = first && !second

    private fun Pair<Boolean, Boolean>.switchedOn() = !first && second
}

private fun ReactiveArtemisConsumer.switchTo(other: ReactiveArtemisConsumer) {

    disconnect()
    when {
        !other.started -> other.start()
        !other.connected -> other.connect()
    }
}
