package net.corda.nodeapi.internal.bridging

import net.corda.nodeapi.internal.ConcurrentBox
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.RemoteInboxAddress.Companion.translateInboxAddressToLocalQueue
import net.corda.nodeapi.internal.ArtemisMessagingComponent.RemoteInboxAddress.Companion.translateLocalQueueToInboxAddress
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.ArtemisConstants.MESSAGE_ID_KEY
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.stillOpen
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.slf4j.MDC

/**
 *  The LoopbackBridgeManager holds the list of independent LoopbackBridge objects that actively loopback messages to local Artemis
 *  inboxes.
 */
@VisibleForTesting
class LoopbackBridgeManager(keyStore: CertificateStore,
                            trustStore: CertificateStore,
                            useOpenSSL: Boolean,
                            proxyConfig: ProxyConfig? = null,
                            maxMessageSize: Int,
                            revocationConfig: RevocationConfig,
                            enableSNI: Boolean,
                            private val artemisMessageClientFactory: () -> ArtemisSessionProvider,
                            private val bridgeMetricsService: BridgeMetricsService? = null,
                            private val isLocalInbox: (String) -> Boolean,
                            trace: Boolean,
                            sslHandshakeTimeout: Long? = null,
                            bridgeConnectionTTLSeconds: Int = 0) : AMQPBridgeManager(keyStore, trustStore, useOpenSSL, proxyConfig,
                                                                                     maxMessageSize, revocationConfig, enableSNI,
                                                                                     artemisMessageClientFactory, bridgeMetricsService,
                                                                                     trace, sslHandshakeTimeout,
                                                                                     bridgeConnectionTTLSeconds) {

    companion object {
        private val log = contextLogger()
    }

    private val queueNamesToBridgesMap = ConcurrentBox(mutableMapOf<String, MutableList<LoopbackBridge>>())
    private var artemis: ArtemisSessionProvider? = null

    /**
     * Each LoopbackBridge is an independent consumer of messages from the Artemis local queue per designated endpoint.
     * It attempts to loopback these messages via ArtemisClient to the local inbox.
     */
    private class LoopbackBridge(val sourceX500Name: String,
                                 val queueName: String,
                                 val targets: List<NetworkHostAndPort>,
                                 val legalNames: Set<CordaX500Name>,
                                 artemis: ArtemisSessionProvider,
                                 private val bridgeMetricsService: BridgeMetricsService?) {
        companion object {
            private val log = contextLogger()
        }

        // TODO: refactor MDC support, duplicated in AMQPBridgeManager.
        private fun withMDC(block: () -> Unit) {
            val oldMDC = MDC.getCopyOfContextMap()
            try {
                MDC.put("queueName", queueName)
                MDC.put("source", sourceX500Name)
                MDC.put("targets", targets.joinToString(separator = ";") { it.toString() })
                MDC.put("legalNames", legalNames.joinToString(separator = ";") { it.toString() })
                MDC.put("bridgeType", "loopback")
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

        private val artemis = ConcurrentBox(artemis)
        private var consumerSession: ClientSession? = null
        private var producerSession: ClientSession? = null
        private var consumer: ClientConsumer? = null
        private var producer: ClientProducer? = null

        fun start() {
            logInfoWithMDC("Create new Artemis loopback bridge")
            artemis.exclusive {
                logInfoWithMDC("Bridge Connected")
                bridgeMetricsService?.bridgeConnected(targets, legalNames)
                val sessionFactory = started!!.sessionFactory
                this@LoopbackBridge.consumerSession = sessionFactory.createSession(NODE_P2P_USER, NODE_P2P_USER, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
                this@LoopbackBridge.producerSession = sessionFactory.createSession(NODE_P2P_USER, NODE_P2P_USER, false, true, true, false, DEFAULT_ACK_BATCH_SIZE)
                // Several producers (in the case of shared bridge) can put messages in the same outbound p2p queue. The consumers are created using the source x500 name as a filter
                val consumer = consumerSession!!.createConsumer(queueName, "hyphenated_props:sender-subject-name = '$sourceX500Name'")
                consumer.setMessageHandler(this@LoopbackBridge::clientArtemisMessageHandler)
                this@LoopbackBridge.consumer = consumer
                this@LoopbackBridge.producer = producerSession!!.createProducer()
                consumerSession?.start()
                producerSession?.start()
            }
        }

        fun stop() {
            logInfoWithMDC("Stopping AMQP bridge")
            artemis.exclusive {
                bridgeMetricsService?.bridgeDisconnected(targets, legalNames)
                consumer?.apply { if (!isClosed) close() }
                consumer = null
                producer?.apply { if (!isClosed) close() }
                producer = null
                consumerSession?.apply { if (stillOpen()) stop() }
                consumerSession = null
                producerSession?.apply { if (stillOpen()) stop()}
                producerSession = null
            }
        }

        private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
            logDebugWithMDC { "Loopback Send to ${legalNames.first()} uuid: ${artemisMessage.getObjectProperty(MESSAGE_ID_KEY)}" }
            val peerInbox = translateLocalQueueToInboxAddress(queueName)
            producer?.send(SimpleString(peerInbox), artemisMessage) { artemisMessage.individualAcknowledge() }
            bridgeMetricsService?.let { metricsService ->
                val properties = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.mapNotNull { key ->
                    if (artemisMessage.containsProperty(key)) {
                        key to artemisMessage.getObjectProperty(key).let { (it as? SimpleString)?.toString() ?: it }
                    } else {
                        null
                    }
                }.toMap()
                metricsService.packetAcceptedEvent(SendableMessageImpl(artemisMessage.payload(), peerInbox, legalNames.first().toString(), targets.first(), properties))
            }
        }
    }

    override fun deployBridge(sourceX500Name: String, queueName: String, targets: List<NetworkHostAndPort>, legalNames: Set<CordaX500Name>) {
        val inboxAddress = translateLocalQueueToInboxAddress(queueName)
        if (isLocalInbox(inboxAddress)) {
            log.info("Deploying loopback bridge for $queueName, source $sourceX500Name")
            queueNamesToBridgesMap.exclusive {
                val bridges = getOrPut(queueName) { mutableListOf() }
                for (target in targets) {
                    if (bridges.any { it.targets.contains(target) && it.sourceX500Name == sourceX500Name }) {
                        return
                    }
                }
                val newBridge = LoopbackBridge(sourceX500Name, queueName, targets, legalNames, artemis!!, bridgeMetricsService)
                bridges += newBridge
                bridgeMetricsService?.bridgeCreated(targets, legalNames)
                newBridge
            }.start()
        } else {
            log.info("Deploying AMQP bridge for $queueName, source $sourceX500Name")
            super.deployBridge(sourceX500Name, queueName, targets, legalNames)
        }
    }

    override fun destroyBridge(queueName: String, targets: List<NetworkHostAndPort>) {
        super.destroyBridge(queueName, targets)
        queueNamesToBridgesMap.exclusive {
            val bridges = this[queueName] ?: mutableListOf()
            for (target in targets) {
                val bridge = bridges.firstOrNull { it.targets.contains(target) }
                if (bridge != null) {
                    bridges -= bridge
                    if (bridges.isEmpty()) {
                        remove(queueName)
                    }
                    bridge.stop()
                    bridgeMetricsService?.bridgeDestroyed(bridge.targets, bridge.legalNames)
                }
            }
        }
    }

    /**
     * Remove any AMQP bridge for the local inbox and create a loopback bridge for that queue.
     */
    fun inboxesAdded(inboxes: List<String>) {
        for (inbox in inboxes) {
            super.destroyAllBridges(translateInboxAddressToLocalQueue(inbox)).forEach { source, bridgeEntry ->
                log.info("Destroyed AMQP Bridge '${bridgeEntry.queueName}', creating Loopback bridge for local inbox.")
                deployBridge(source, bridgeEntry.queueName, bridgeEntry.targets, bridgeEntry.legalNames.toSet())
            }
        }
    }

    override fun start() {
        super.start()
        val artemis = artemisMessageClientFactory()
        this.artemis = artemis
        artemis.start()
    }

    override fun stop() = close()

    override fun close() {
        super.close()
        queueNamesToBridgesMap.exclusive {
            for (bridge in values.flatten()) {
                bridge.stop()
            }
            clear()
            artemis?.stop()
        }
    }
}
