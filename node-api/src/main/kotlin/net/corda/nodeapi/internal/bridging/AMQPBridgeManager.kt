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
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.slf4j.MDC
import rx.Subscription
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
class AMQPBridgeManager(config: MutualSslConfiguration, maxMessageSize: Int, private val artemisMessageClientFactory: () -> ArtemisSessionProvider) : BridgeManager {

    private val lock = ReentrantLock()
    private val queueNamesToBridgesMap = mutableMapOf<String, MutableList<AMQPBridge>>()

    private class AMQPConfigurationImpl private constructor(override val keyStore: CertificateStore,
                                                            override val trustStore: CertificateStore,
                                                            override val maxMessageSize: Int) : AMQPConfiguration {
        constructor(config: MutualSslConfiguration, maxMessageSize: Int) : this(config.keyStore.get(), config.trustStore.get(), maxMessageSize)
    }

    private val amqpConfig: AMQPConfiguration = AMQPConfigurationImpl(config, maxMessageSize)
    private var sharedEventLoopGroup: EventLoopGroup? = null
    private var artemis: ArtemisSessionProvider? = null

    constructor(config: MutualSslConfiguration, p2pAddress: NetworkHostAndPort, maxMessageSize: Int) : this(config, maxMessageSize, { ArtemisMessagingClient(config, p2pAddress, maxMessageSize) })

    companion object {
        private const val NUM_BRIDGE_THREADS = 0 // Default sized pool
    }

    /**
     * Each AMQPBridge is an independent consumer of messages from the Artemis local queue per designated endpoint.
     * It attempts to deliver these messages via an AMQPClient instance to the remote Artemis inbox.
     * To prevent race conditions the Artemis session/consumer is only created when the AMQPClient has a stable AMQP connection.
     * The acknowledgement and removal of messages from the local queue only occurs if there successful end-to-end delivery.
     * If the delivery fails the session is rolled back to prevent loss of the message. This may cause duplicate delivery,
     * however Artemis and the remote Corda instanced will deduplicate these messages.
     */
    private class AMQPBridge(val queueName: String,
                             val targets: List<NetworkHostAndPort>,
                             private val legalNames: Set<CordaX500Name>,
                             private val amqpConfig: AMQPConfiguration,
                             sharedEventGroup: EventLoopGroup,
                             private val artemis: ArtemisSessionProvider) {
        companion object {
            private val log = contextLogger()
        }

        private fun withMDC(block: () -> Unit) {
            val oldMDC = MDC.getCopyOfContextMap()
            try {
                MDC.put("queueName", queueName)
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

        fun start() {
            logInfoWithMDC("Create new AMQP bridge")
            connectedSubscription = amqpClient.onConnection.subscribe({ x -> onSocketConnected(x.connected) })
            amqpClient.start()
        }

        fun stop() {
            logInfoWithMDC("Stopping AMQP bridge")
            lock.withLock {
                synchronized(artemis) {
                    consumer?.close()
                    consumer = null
                    session?.stop()
                    session = null
                }
            }
            amqpClient.stop()
            connectedSubscription?.unsubscribe()
            connectedSubscription = null
        }

        private fun onSocketConnected(connected: Boolean) {
            lock.withLock {
                synchronized(artemis) {
                    if (connected) {
                        logInfoWithMDC("Bridge Connected")
                        val sessionFactory = artemis.started!!.sessionFactory
                        val session = sessionFactory.createSession(NODE_P2P_USER, NODE_P2P_USER, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
                        this.session = session
                        val consumer = session.createConsumer(queueName)
                        this.consumer = consumer
                        consumer.setMessageHandler(this@AMQPBridge::clientArtemisMessageHandler)
                        session.start()
                    } else {
                        logInfoWithMDC("Bridge Disconnected")
                        consumer?.close()
                        consumer = null
                        session?.stop()
                        session = null
                    }
                }
            }
        }

        private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
            if (artemisMessage.bodySize > amqpConfig.maxMessageSize) {
                logWarnWithMDC("Message exceeds maxMessageSize network parameter, maxMessageSize: [${amqpConfig.maxMessageSize}], message size: [${artemisMessage.bodySize}], " +
                        "dropping message, uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}")
                // Ack the message to prevent same message being sent to us again.
                artemisMessage.acknowledge()
                return
            }
            val data = ByteArray(artemisMessage.bodySize).apply { artemisMessage.bodyBuffer.readBytes(this) }
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
            val sendableMessage = amqpClient.createMessage(data, peerInbox,
                    legalNames.first().toString(),
                    properties)
            sendableMessage.onComplete.then {
                logDebugWithMDC { "Bridge ACK ${sendableMessage.onComplete.get()}" }
                lock.withLock {
                    if (sendableMessage.onComplete.get() == MessageStatus.Acknowledged) {
                        artemisMessage.acknowledge()
                    } else {
                        logInfoWithMDC("Rollback rejected message uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}")
                        // We need to commit any acknowledged messages before rolling back the failed
                        // (unacknowledged) message.
                        session?.commit()
                        session?.rollback(false)
                    }
                }
            }
            amqpClient.write(sendableMessage)
        }
    }

    override fun deployBridge(queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
        val newBridge = lock.withLock {
            val bridges = queueNamesToBridgesMap.getOrPut(queueName) { mutableListOf() }
            for (target in targets) {
                if (bridges.any { it.targets.contains(target) }) {
                    return
                }
            }
            val newBridge = AMQPBridge(queueName, targets, legalNames, amqpConfig, sharedEventLoopGroup!!, artemis!!)
            bridges += newBridge
            newBridge
        }
        newBridge.start()
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
                }
            }
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