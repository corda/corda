package net.corda.nodeapi.internal.bridging

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_CONTROL
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.BRIDGE_NOTIFY
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.RemoteInboxAddress.Companion.translateLocalQueueToInboxAddress
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class BridgeControlListener(val config: MutualSslConfiguration,
                            socksProxyConfig: SocksProxyConfig? = null,
                            maxMessageSize: Int,
                            private val artemisMessageClientFactory: () -> ArtemisSessionProvider,
                            bridgeMetricsService: BridgeMetricsService? = null) : AutoCloseable {
    private val bridgeId: String = UUID.randomUUID().toString()
    private val bridgeControlQueue = "$BRIDGE_CONTROL.$bridgeId"
    private val bridgeNotifyQueue = "$BRIDGE_NOTIFY.$bridgeId"
    private val validInboundQueues = mutableSetOf<String>()
    private val bridgeManager = LoopbackBridgeManagerWrapper(config, socksProxyConfig, maxMessageSize, artemisMessageClientFactory, bridgeMetricsService, this::validateReceiveTopic)
    private var artemis: ArtemisSessionProvider? = null
    private var controlConsumer: ClientConsumer? = null
    private var notifyConsumer: ClientConsumer? = null

    constructor(config: MutualSslConfiguration,
                p2pAddress: NetworkHostAndPort,
                maxMessageSize: Int,
                socksProxy: SocksProxyConfig? = null) : this(config, socksProxy, maxMessageSize, { ArtemisMessagingClient(config, p2pAddress, maxMessageSize) })

    companion object {
        private val log = contextLogger()
    }

    val active: Boolean
        get() = validInboundQueues.isNotEmpty()

    private val _activeChange = PublishSubject.create<Boolean>().toSerialized()
    val activeChange: Observable<Boolean>
        get() = _activeChange

    fun start() {
        stop()
        bridgeManager.start()
        val artemis = artemisMessageClientFactory()
        this.artemis = artemis
        artemis.start()
        val artemisClient = artemis.started!!
        val artemisSession = artemisClient.session
        registerBridgeControlListener(artemisSession)
        registerBridgeDuplicateChecker(artemisSession)
        val startupMessage = BridgeControl.BridgeToNodeSnapshotRequest(bridgeId).serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
        val bridgeRequest = artemisSession.createMessage(false)
        bridgeRequest.writeBodyBufferBytes(startupMessage)
        artemisClient.producer.send(BRIDGE_NOTIFY, bridgeRequest)
    }

    private fun registerBridgeControlListener(artemisSession: ClientSession) {
        try {
            artemisSession.createTemporaryQueue(BRIDGE_CONTROL, RoutingType.MULTICAST, bridgeControlQueue)
        } catch (ex: ActiveMQQueueExistsException) {
            // Ignore if there is a queue still not cleaned up
        }

        val control = artemisSession.createConsumer(bridgeControlQueue)
        controlConsumer = control
        control.setMessageHandler { msg ->
            try {
                processControlMessage(msg)
            } catch (ex: Exception) {
                log.error("Unable to process bridge control message", ex)
            }
        }
    }

    private fun registerBridgeDuplicateChecker(artemisSession: ClientSession) {
        try {
            artemisSession.createTemporaryQueue(BRIDGE_NOTIFY, RoutingType.MULTICAST, bridgeNotifyQueue)
        } catch (ex: ActiveMQQueueExistsException) {
            // Ignore if there is a queue still not cleaned up
        }
        val notify = artemisSession.createConsumer(bridgeNotifyQueue)
        notifyConsumer = notify
        notify.setMessageHandler { msg ->
            try {
                val data: ByteArray = ByteArray(msg.bodySize).apply { msg.bodyBuffer.readBytes(this) }
                val notifyMessage = data.deserialize<BridgeControl.BridgeToNodeSnapshotRequest>(context = SerializationDefaults.P2P_CONTEXT)
                if (notifyMessage.bridgeIdentity != bridgeId) {
                    log.error("Fatal Error! Two bridges have been configured simultaneously! Check the enterpriseConfiguration.externalBridge status")
                    System.exit(1)
                }
            } catch (ex: Exception) {
                log.error("Unable to process bridge notification message", ex)
            }
        }
    }

    fun stop() {
        if (active) {
            _activeChange.onNext(false)
        }
        validInboundQueues.clear()
        controlConsumer?.close()
        controlConsumer = null
        notifyConsumer?.close()
        notifyConsumer = null
        artemis?.apply {
            started?.session?.deleteQueue(bridgeControlQueue)
            started?.session?.deleteQueue(bridgeNotifyQueue)
            stop()
        }
        artemis = null
        bridgeManager.stop()
    }

    override fun close() = stop()

    fun validateReceiveTopic(topic: String): Boolean {
        return topic in validInboundQueues
    }

    private fun validateInboxQueueName(queueName: String): Boolean {
        return queueName.startsWith(P2P_PREFIX) && artemis!!.started!!.session.queueQuery(SimpleString(queueName)).isExists
    }

    private fun validateBridgingQueueName(queueName: String): Boolean {
        return queueName.startsWith(PEERS_PREFIX) && artemis!!.started!!.session.queueQuery(SimpleString(queueName)).isExists
    }

    private fun processControlMessage(msg: ClientMessage) {
        val data: ByteArray = ByteArray(msg.bodySize).apply { msg.bodyBuffer.readBytes(this) }
        val controlMessage = data.deserialize<BridgeControl>(context = SerializationDefaults.P2P_CONTEXT)
        log.info("Received bridge control message $controlMessage")
        when (controlMessage) {
            is BridgeControl.NodeToBridgeSnapshot -> {
                if (!controlMessage.inboxQueues.all { validateInboxQueueName(it) }) {
                    log.error("Invalid queue names in control message $controlMessage")
                    return
                }
                if (!controlMessage.sendQueues.all { validateBridgingQueueName(it.queueName) }) {
                    log.error("Invalid queue names in control message $controlMessage")
                    return
                }
                for (outQueue in controlMessage.sendQueues) {
                    bridgeManager.deployBridge(controlMessage.nodeIdentity, outQueue.queueName, outQueue.targets, outQueue.legalNames.toSet())
                }
                val wasActive = active
                validInboundQueues.addAll(controlMessage.inboxQueues)
                log.info("Added inbox: ${controlMessage.inboxQueues}")
                bridgeManager.inboxesAdded(controlMessage.inboxQueues)
                if (!wasActive && active) {
                    _activeChange.onNext(true)
                }
            }
            is BridgeControl.BridgeToNodeSnapshotRequest -> {
                log.error("Message from Bridge $controlMessage detected on wrong topic!")
            }
            is BridgeControl.Create -> {
                if (!validateBridgingQueueName((controlMessage.bridgeInfo.queueName))) {
                    log.error("Invalid queue names in control message $controlMessage")
                    return
                }
                bridgeManager.deployBridge(controlMessage.nodeIdentity, controlMessage.bridgeInfo.queueName, controlMessage.bridgeInfo.targets, controlMessage.bridgeInfo.legalNames.toSet())
            }
            is BridgeControl.Delete -> {
                if (!controlMessage.bridgeInfo.queueName.startsWith(PEERS_PREFIX)) {
                    log.error("Invalid queue names in control message $controlMessage")
                    return
                }
                bridgeManager.destroyBridge(controlMessage.bridgeInfo.queueName, controlMessage.bridgeInfo.targets)
            }
        }
    }

    private class LoopbackBridgeManagerWrapper(config: MutualSslConfiguration,
                                               socksProxyConfig: SocksProxyConfig? = null,
                                               maxMessageSize: Int,
                                               artemisMessageClientFactory: () -> ArtemisSessionProvider,
                                               bridgeMetricsService: BridgeMetricsService? = null,
                                               private val isLocalInbox: (String) -> Boolean) : BridgeManager {

        private val bridgeManager = AMQPBridgeManager(config, socksProxyConfig, maxMessageSize, artemisMessageClientFactory, bridgeMetricsService)
        private val loopbackBridgeManager = LoopbackBridgeManager(artemisMessageClientFactory, bridgeMetricsService)

        override fun deployBridge(sourceX500Name: String, queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
            val inboxAddress = translateLocalQueueToInboxAddress(queueName)
            if (isLocalInbox(inboxAddress)) {
                log.info("Deploying loopback bridge for $queueName, source $sourceX500Name")
                loopbackBridgeManager.deployBridge(sourceX500Name, queueName, targets, legalNames)
            } else {
                log.info("Deploying AMQP bridge for $queueName, source $sourceX500Name")
                bridgeManager.deployBridge(sourceX500Name, queueName, targets, legalNames)
            }
        }

        override fun destroyBridge(queueName: String, targets: List<NetworkHostAndPort>) {
            bridgeManager.destroyBridge(queueName, targets)
            loopbackBridgeManager.destroyBridge(queueName, targets)
        }

        override fun start() {
            bridgeManager.start()
            loopbackBridgeManager.start()
        }

        override fun stop() {
            bridgeManager.stop()
            loopbackBridgeManager.stop()
        }

        override fun close() = stop()

        /**
         * Remove any AMQP bridge for the local inbox and create a loopback bridge for that queue.
         */
        fun inboxesAdded(inboxes: List<String>) {
            for (inbox in inboxes) {
                bridgeManager.destroyAllBridge(inbox).forEach { source, bridgeEntry ->
                    loopbackBridgeManager.deployBridge(source, bridgeEntry.queueName, bridgeEntry.targets, bridgeEntry.legalNames.toSet())
                }
            }
        }
    }
}