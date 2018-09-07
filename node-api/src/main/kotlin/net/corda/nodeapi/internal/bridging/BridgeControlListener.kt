package net.corda.nodeapi.internal.bridging

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
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import java.util.*

class BridgeControlListener(val config: MutualSslConfiguration,
                            maxMessageSize: Int,
                            val artemisMessageClientFactory: () -> ArtemisSessionProvider) : AutoCloseable {
    private val bridgeId: String = UUID.randomUUID().toString()
    private val bridgeManager: BridgeManager = AMQPBridgeManager(config, maxMessageSize, artemisMessageClientFactory)
    private val validInboundQueues = mutableSetOf<String>()
    private var artemis: ArtemisSessionProvider? = null
    private var controlConsumer: ClientConsumer? = null

    constructor(config: MutualSslConfiguration,
                p2pAddress: NetworkHostAndPort,
                maxMessageSize: Int) : this(config, maxMessageSize, { ArtemisMessagingClient(config, p2pAddress, maxMessageSize) })

    companion object {
        private val log = contextLogger()
    }

    fun start() {
        stop()
        bridgeManager.start()
        val artemis = artemisMessageClientFactory()
        this.artemis = artemis
        artemis.start()
        val artemisClient = artemis.started!!
        val artemisSession = artemisClient.session
        val bridgeControlQueue = "$BRIDGE_CONTROL.$bridgeId"
        artemisSession.createTemporaryQueue(BRIDGE_CONTROL, RoutingType.MULTICAST, bridgeControlQueue)
        val control = artemisSession.createConsumer(bridgeControlQueue)
        controlConsumer = control
        control.setMessageHandler { msg ->
            try {
                processControlMessage(msg)
            } catch (ex: Exception) {
                log.error("Unable to process bridge control message", ex)
            }
        }
        val startupMessage = BridgeControl.BridgeToNodeSnapshotRequest(bridgeId).serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
        val bridgeRequest = artemisSession.createMessage(false)
        bridgeRequest.writeBodyBufferBytes(startupMessage)
        artemisClient.producer.send(BRIDGE_NOTIFY, bridgeRequest)
    }

    fun stop() {
        validInboundQueues.clear()
        controlConsumer?.close()
        controlConsumer = null
        artemis?.stop()
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
                    bridgeManager.deployBridge(outQueue.queueName, outQueue.targets, outQueue.legalNames.toSet())
                }
                validInboundQueues.addAll(controlMessage.inboxQueues)
            }
            is BridgeControl.BridgeToNodeSnapshotRequest -> {
                log.error("Message from Bridge $controlMessage detected on wrong topic!")
            }
            is BridgeControl.Create -> {
                if (!validateBridgingQueueName((controlMessage.bridgeInfo.queueName))) {
                    log.error("Invalid queue names in control message $controlMessage")
                    return
                }
                bridgeManager.deployBridge(controlMessage.bridgeInfo.queueName, controlMessage.bridgeInfo.targets, controlMessage.bridgeInfo.legalNames.toSet())
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

}