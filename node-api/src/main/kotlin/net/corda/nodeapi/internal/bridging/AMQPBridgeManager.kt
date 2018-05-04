/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.bridging

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.RemoteInboxAddress.Companion.translateLocalQueueToInboxAddress
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.bridging.AMQPBridgeManager.AMQPBridge.Companion.getBridgeName
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.slf4j.LoggerFactory
import rx.Subscription
import java.security.KeyStore
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
class AMQPBridgeManager(config: NodeSSLConfiguration, private val socksProxyConfig: SocksProxyConfig? = null, val artemisMessageClientFactory: () -> ArtemisSessionProvider) : BridgeManager {

    private val lock = ReentrantLock()
    private val bridgeNameToBridgeMap = mutableMapOf<String, AMQPBridge>()
    private var sharedEventLoopGroup: EventLoopGroup? = null
    private val keyStore = config.loadSslKeyStore().internal
    private val keyStorePrivateKeyPassword: String = config.keyStorePassword
    private val trustStore = config.loadTrustStore().internal
    private var artemis: ArtemisSessionProvider? = null
    private val crlCheckSoftFail: Boolean = config.crlCheckSoftFail

    constructor(config: NodeSSLConfiguration, p2pAddress: NetworkHostAndPort, maxMessageSize: Int, socksProxyConfig: SocksProxyConfig? = null) : this(config, socksProxyConfig, { ArtemisMessagingClient(config, p2pAddress, maxMessageSize) })

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
    private class AMQPBridge(private val queueName: String,
                             private val target: NetworkHostAndPort,
                             private val legalNames: Set<CordaX500Name>,
                             keyStore: KeyStore,
                             keyStorePrivateKeyPassword: String,
                             trustStore: KeyStore,
                             crlCheckSoftFail: Boolean,
                             sharedEventGroup: EventLoopGroup,
                             socksProxyConfig: SocksProxyConfig?,
                             private val artemis: ArtemisSessionProvider) {
        companion object {
            fun getBridgeName(queueName: String, hostAndPort: NetworkHostAndPort): String = "$queueName -> $hostAndPort"
        }

        private val log = LoggerFactory.getLogger("$bridgeName:${legalNames.first()}")

        val amqpClient = AMQPClient(listOf(target), legalNames, PEER_USER, PEER_USER, keyStore, keyStorePrivateKeyPassword, trustStore, crlCheckSoftFail, sharedThreadPool = sharedEventGroup, socksProxyConfig = socksProxyConfig)
        val bridgeName: String get() = getBridgeName(queueName, target)
        private val lock = ReentrantLock() // lock to serialise session level access
        private var session: ClientSession? = null
        private var consumer: ClientConsumer? = null
        private var connectedSubscription: Subscription? = null

        fun start() {
            log.info("Create new AMQP bridge")
            connectedSubscription = amqpClient.onConnection.subscribe({ x -> onSocketConnected(x.connected) })
            amqpClient.start()
        }

        fun stop() {
            log.info("Stopping AMQP bridge")
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
                        log.info("Bridge Connected")
                        val sessionFactory = artemis.started!!.sessionFactory
                        val session = sessionFactory.createSession(NODE_USER, NODE_USER, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
                        this.session = session
                        val consumer = session.createConsumer(queueName)
                        this.consumer = consumer
                        consumer.setMessageHandler(this@AMQPBridge::clientArtemisMessageHandler)
                        session.start()
                    } else {
                        log.info("Bridge Disconnected")
                        consumer?.close()
                        consumer = null
                        session?.stop()
                        session = null
                    }
                }
            }
        }

        private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
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
            log.debug { "Bridged Send to ${legalNames.first()} uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}" }
            val peerInbox = translateLocalQueueToInboxAddress(queueName)
            val sendableMessage = amqpClient.createMessage(data, peerInbox,
                    legalNames.first().toString(),
                    properties)
            sendableMessage.onComplete.then {
                log.debug { "Bridge ACK ${sendableMessage.onComplete.get()}" }
                lock.withLock {
                    if (sendableMessage.onComplete.get() == MessageStatus.Acknowledged) {
                        artemisMessage.acknowledge()
                    } else {
                        log.info("Rollback rejected message uuid: ${artemisMessage.getObjectProperty("_AMQ_DUPL_ID")}")
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

    private fun gatherAddresses(node: NodeInfo): List<ArtemisMessagingComponent.NodeAddress> {
        return node.legalIdentitiesAndCerts.map { ArtemisMessagingComponent.NodeAddress(it.party.owningKey, node.addresses[0]) }
    }

    override fun deployBridge(queueName: String, target: NetworkHostAndPort, legalNames: Set<CordaX500Name>) {
        if (bridgeExists(getBridgeName(queueName, target))) {
            return
        }
        val newBridge = AMQPBridge(queueName, target, legalNames, keyStore, keyStorePrivateKeyPassword, trustStore, crlCheckSoftFail, sharedEventLoopGroup!!, socksProxyConfig, artemis!!)
        lock.withLock {
            bridgeNameToBridgeMap[newBridge.bridgeName] = newBridge
        }
        newBridge.start()
    }

    override fun destroyBridges(node: NodeInfo) {
        lock.withLock {
            gatherAddresses(node).forEach {
                val bridge = bridgeNameToBridgeMap.remove(getBridgeName(it.queueName, it.hostAndPort))
                bridge?.stop()
            }
        }
    }

    override fun destroyBridge(queueName: String, hostAndPort: NetworkHostAndPort) {
        lock.withLock {
            val bridge = bridgeNameToBridgeMap.remove(getBridgeName(queueName, hostAndPort))
            bridge?.stop()
        }
    }

    override fun bridgeExists(bridgeName: String): Boolean = lock.withLock { bridgeNameToBridgeMap.containsKey(bridgeName) }

    override fun start() {
        sharedEventLoopGroup = NioEventLoopGroup(NUM_BRIDGE_THREADS)
        val artemis = artemisMessageClientFactory()
        this.artemis = artemis
        artemis.start()
    }

    override fun stop() = close()

    override fun close() {
        lock.withLock {
            for (bridge in bridgeNameToBridgeMap.values) {
                bridge.stop()
            }
            sharedEventLoopGroup?.shutdownGracefully()
            sharedEventLoopGroup?.terminationFuture()?.sync()
            sharedEventLoopGroup = null
            bridgeNameToBridgeMap.clear()
            artemis?.stop()
        }
    }
}