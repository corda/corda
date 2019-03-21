package net.corda.nodeapi.internal.bridging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.ArtemisMessagingComponent.RemoteInboxAddress.Companion.translateLocalQueueToInboxAddress
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.slf4j.MDC
import rx.Subscription
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *  The AMQPBridgeManager holds the list of independent AMQPBridge objects that actively ferry messages to remote Artemis
 *  inboxes.
 *  The AMQPBridgeManager also provides a single shared connection to Artemis, although each bridge then creates an
 *  independent Session for message consumption.
 *  The Netty thread pool used by the AMQPBridges is also shared and managed by the AMQPBridgeManager.
 */
@VisibleForTesting
open class AMQPBridgeManager(config: MutualSslConfiguration,
                             proxyConfig: ProxyConfig? = null,
                             maxMessageSize: Int,
                             crlCheckSoftFail: Boolean,
                             enableSNI: Boolean,
                             private val artemisMessageClientFactory: () -> ArtemisSessionProvider,
                             private val bridgeMetricsService: BridgeMetricsService? = null,
                             private val trace: Boolean) : BridgeManager {

    private val lock = ReentrantLock()
    private val queueNamesToBridgesMap = mutableMapOf<String, MutableList<AMQPBridge>>()

    private class AMQPConfigurationImpl(override val keyStore: CertificateStore,
                                        override val trustStore: CertificateStore,
                                        override val proxyConfig: ProxyConfig?,
                                        override val maxMessageSize: Int,
                                        override val crlCheckSoftFail: Boolean,
                                        override val useOpenSsl: Boolean,
                                        override val enableSNI: Boolean,
                                        override val sourceX500Name: String? = null,
                                        override val trace: Boolean) : AMQPConfiguration {
        constructor(config: MutualSslConfiguration, proxyConfig: ProxyConfig?, maxMessageSize: Int, crlCheckSoftFail: Boolean, enableSNI: Boolean, trace: Boolean) : this(config.keyStore.get(),
                config.trustStore.get(),
                proxyConfig,
                maxMessageSize,
                crlCheckSoftFail,
                config.useOpenSsl,
                enableSNI,
                trace = trace)
    }

    private val amqpConfig: AMQPConfiguration = AMQPConfigurationImpl(config, proxyConfig, maxMessageSize, crlCheckSoftFail, enableSNI, trace)
    private var sharedEventLoopGroup: EventLoopGroup? = null
    private var artemis: ArtemisSessionProvider? = null

    constructor(config: MutualSslConfiguration,
                p2pAddress: NetworkHostAndPort,
                maxMessageSize: Int,
                crlCheckSoftFail: Boolean,
                enableSNI: Boolean,
                proxyConfig: ProxyConfig? = null,
                trace: Boolean = false)
            : this(config, proxyConfig, maxMessageSize, crlCheckSoftFail, enableSNI, { ArtemisMessagingClient(config, p2pAddress, maxMessageSize) }, trace = trace)

    companion object {
        private const val NUM_BRIDGE_THREADS = 0 // Default sized pool
        private const val ARTEMIS_RETRY_TIME = 60000L
    }

    /**
     * Each AMQPBridge is an independent consumer of messages from the Artemis local queue per designated endpoint.
     * It attempts to deliver these messages via an AMQPClient instance to the remote Artemis inbox.
     * To prevent race conditions the Artemis session/consumer is only created when the AMQPClient has a stable AMQP connection.
     * The acknowledgement and removal of messages from the local queue only occurs if there successful end-to-end delivery.
     * If the delivery fails the session is rolled back to prevent loss of the message. This may cause duplicate delivery,
     * however Artemis and the remote Corda instanced will deduplicate these messages.
     */
    private class AMQPBridge(val sourceX500Name: String,
                             val queueName: String,
                             val targets: List<NetworkHostAndPort>,
                             val legalNames: Set<CordaX500Name>,
                             private val amqpConfig: AMQPConfiguration,
                             private val sharedEventGroup: EventLoopGroup,
                             private val artemis: ArtemisSessionProvider,
                             private val bridgeMetricsService: BridgeMetricsService?) {
        companion object {
            private val log = contextLogger()
        }

        private fun withMDC(block: () -> Unit) {
            val oldMDC = MDC.getCopyOfContextMap()
            try {
                MDC.put("queueName", queueName)
                MDC.put("source", amqpConfig.sourceX500Name)
                MDC.put("targets", targets.joinToString(separator = ";") { it.toString() })
                MDC.put("legalNames", legalNames.joinToString(separator = ";") { it.toString() })
                MDC.put("maxMessageSize", amqpConfig.maxMessageSize.toString())
                block()
            } finally {
                MDC.setContextMap(oldMDC)
            }
        }

        private fun logDebugWithMDC(msg: () -> String) {
            if (log.isDebugEnabled) {
                withMDC { log.debug(msg()) }
            }
        }

        private fun logInfoWithMDC(msg: String) = withMDC { log.info(msg) }

        private fun logWarnWithMDC(msg: String) = withMDC { log.warn(msg) }

        val amqpClient = AMQPClient(targets, legalNames, amqpConfig, sharedThreadPool = sharedEventGroup)
        private val lock = ReentrantLock() // lock to serialise session level access
        private var session: ClientSession? = null
        private var consumer: ClientConsumer? = null
        private var connectedSubscription: Subscription? = null
        private var messagesReceived: Boolean = false

        fun start() {
            logInfoWithMDC("Create new AMQP bridge")
            connectedSubscription = amqpClient.onConnection.subscribe { x -> onSocketConnected(x.connected) }
            amqpClient.start()
        }

        fun stop() {
            logInfoWithMDC("Stopping AMQP bridge")
            lock.withLock {
                synchronized(artemis) {
                    consumer?.apply {
                        if (!isClosed) {
                            close()
                        }
                    }
                    consumer = null
                    session?.apply {
                        if (!isClosed) {
                            stop()
                        }
                    }
                    session = null
                }
            }
            amqpClient.stop()
            connectedSubscription?.unsubscribe()
            connectedSubscription = null
        }

        private fun onSocketConnected(connected: Boolean) {
            lock.withLock {
                messagesReceived = false
                synchronized(artemis) {
                    if (connected) {
                        logInfoWithMDC("Bridge Connected")
                        sharedEventGroup.schedule({ // during testing we found that kill -9 can fail to replay messages into the consumer until the session is restarted/remade
                            if(!messagesReceived) { // we only make bridges if there is at least one message to process, so if none arrive hit artemis with a hammer
                                synchronized(artemis) {
                                    logInfoWithMDC("No messages received on new bridge. Restarting Artemis session")
                                    try {
                                        this.session?.apply {
                                            stop()
                                            start()
                                        }
                                    } catch(ex: Exception) {
                                        log.error("Restart artemis session error", ex)
                                    }
                                }
                            }
                        }, ARTEMIS_RETRY_TIME, TimeUnit.MILLISECONDS)
                        bridgeMetricsService?.bridgeConnected(targets, legalNames)
                        val sessionFactory = artemis.started!!.sessionFactory
                        val session = sessionFactory.createSession(NODE_P2P_USER, NODE_P2P_USER, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
                        this.session = session
                        // Several producers (in the case of shared bridge) can put messages in the same outbound p2p queue. The consumers are created using the source x500 name as a filter
                        val consumer = if (amqpConfig.enableSNI) {
                            session.createConsumer(queueName, "hyphenated_props:sender-subject-name = '${amqpConfig.sourceX500Name}'")
                        } else {
                            session.createConsumer(queueName)
                        }
                        this.consumer = consumer
                        consumer.setMessageHandler(this@AMQPBridge::clientArtemisMessageHandler)
                        session.start()
                    } else {
                        logInfoWithMDC("Bridge Disconnected")
                        bridgeMetricsService?.bridgeDisconnected(targets, legalNames)
                        consumer?.apply {
                            if (!isClosed) {
                                close()
                            }
                        }
                        consumer = null
                        session?.apply {
                            if (!isClosed) {
                                stop()
                            }
                        }
                        session = null
                    }
                }
            }
        }

        private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
            messagesReceived = true
            if (artemisMessage.bodySize > amqpConfig.maxMessageSize) {
                val msg = "Message exceeds maxMessageSize network parameter, maxMessageSize: [${amqpConfig.maxMessageSize}], message size: [${artemisMessage.bodySize}], " +
                        "dropping message, uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}"
                logWarnWithMDC(msg)
                bridgeMetricsService?.packetDropEvent(artemisMessage, msg)
                // Ack the message to prevent same message being sent to us again.
                try {
                    artemisMessage.individualAcknowledge()
                } catch(ex: ActiveMQObjectClosedException) {
                    log.warn("Artemis message was closed")
                }
                return
            }
            val properties = HashMap<String, Any?>()
            for (key in P2PMessagingHeaders.whitelistedHeaders) {
                if (artemisMessage.containsProperty(key)) {
                    var value = artemisMessage.getObjectProperty(key)
                    if (value is SimpleString) {
                        value = value.toString()
                    }
                    properties[key] = value
                }
            }
            logDebugWithMDC { "Bridged Send to ${legalNames.first()} uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}" }
            val peerInbox = translateLocalQueueToInboxAddress(queueName)
            val sendableMessage = amqpClient.createMessage(artemisMessage.payload(), peerInbox,
                    legalNames.first().toString(),
                    properties)
            sendableMessage.onComplete.then {
                logDebugWithMDC { "Bridge ACK ${sendableMessage.onComplete.get()}" }
                lock.withLock {
                    if (sendableMessage.onComplete.get() == MessageStatus.Acknowledged) {
                        try {
                            artemisMessage.individualAcknowledge()
                        } catch(ex: ActiveMQObjectClosedException) {
                            log.warn("Artemis message was closed")
                        }
                    } else {
                        logInfoWithMDC("Rollback rejected message uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}")
                        // We need to commit any acknowledged messages before rolling back the failed
                        // (unacknowledged) message.
                        session?.commit()
                        session?.rollback(false)
                    }
                }
            }
            try {
                amqpClient.write(sendableMessage)
            } catch (ex: IllegalStateException) {
                // Attempting to send a message while the AMQP client is disconnected may cause message loss.
                // The failed message is rolled back after committing acknowledged messages.
                lock.withLock {
                    ex.message?.let { logInfoWithMDC(it) }
                    logInfoWithMDC("Rollback rejected message uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}")
                    session?.commit()
                    session?.rollback(false)
                }
            }
            bridgeMetricsService?.packetAcceptedEvent(sendableMessage)
        }
    }

    override fun deployBridge(sourceX500Name: String, queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
        lock.withLock {
            val bridges = queueNamesToBridgesMap.getOrPut(queueName) { mutableListOf() }
            for (target in targets) {
                if (bridges.any { it.targets.contains(target) && it.sourceX500Name == sourceX500Name }) {
                    return
                }
            }
            val newAMQPConfig = with(amqpConfig) { AMQPConfigurationImpl(keyStore, trustStore, proxyConfig, maxMessageSize, crlCheckSoftFail, useOpenSsl, enableSNI, sourceX500Name, trace) }
            val newBridge = AMQPBridge(sourceX500Name, queueName, targets, legalNames, newAMQPConfig, sharedEventLoopGroup!!, artemis!!, bridgeMetricsService)
            bridges += newBridge
            bridgeMetricsService?.bridgeCreated(targets, legalNames)
            newBridge
        }.start()
    }

    override fun destroyBridge(queueName: String, targets: List<NetworkHostAndPort>) {
        lock.withLock {
            val bridges = queueNamesToBridgesMap[queueName] ?: mutableListOf()
            for (target in targets) {
                val bridge = bridges.firstOrNull { it.targets.contains(target) }
                if (bridge != null) {
                    bridges -= bridge
                    if (bridges.isEmpty()) {
                        queueNamesToBridgesMap.remove(queueName)
                    }
                    bridge.stop()
                    bridgeMetricsService?.bridgeDestroyed(bridge.targets, bridge.legalNames)
                }
            }
        }
    }

    fun destroyAllBridges(queueName: String): Map<String, BridgeEntry> {
        return lock.withLock {
            // queueNamesToBridgesMap returns a mutable list, .toList converts it to a immutable list so it won't be changed by the [destroyBridge] method.
            val bridges = queueNamesToBridgesMap[queueName]?.toList()
            destroyBridge(queueName, bridges?.flatMap { it.targets } ?: emptyList())
            bridges?.map {
                it.sourceX500Name to BridgeEntry(it.queueName, it.targets, it.legalNames.toList(), serviceAddress = false)
            }?.toMap() ?: emptyMap()
        }
    }

    override fun start() {
        sharedEventLoopGroup = NioEventLoopGroup(NUM_BRIDGE_THREADS)
        val artemis = artemisMessageClientFactory()
        this.artemis = artemis
        artemis.start()
    }

    override fun stop() = close()

    override fun close() {
        lock.withLock {
            for (bridge in queueNamesToBridgesMap.values.flatten()) {
                bridge.stop()
            }
            sharedEventLoopGroup?.shutdownGracefully()
            sharedEventLoopGroup?.terminationFuture()?.sync()
            sharedEventLoopGroup = null
            queueNamesToBridgesMap.clear()
            artemis?.stop()
        }
    }
}