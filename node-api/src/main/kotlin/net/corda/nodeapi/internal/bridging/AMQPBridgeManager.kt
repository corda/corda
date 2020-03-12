@file:Suppress("TooGenericExceptionCaught") // needs to catch and handle/rethrow *all* exceptions in many places
package net.corda.nodeapi.internal.bridging

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.channel.EventLoop
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
import net.corda.nodeapi.internal.ArtemisConstants.MESSAGE_ID_KEY
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.slf4j.MDC
import rx.Subscription
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
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
open class AMQPBridgeManager(keyStore: CertificateStore,
                             trustStore: CertificateStore,
                             useOpenSSL: Boolean,
                             proxyConfig: ProxyConfig? = null,
                             maxMessageSize: Int,
                             revocationConfig: RevocationConfig,
                             enableSNI: Boolean,
                             private val artemisMessageClientFactory: () -> ArtemisSessionProvider,
                             private val bridgeMetricsService: BridgeMetricsService? = null,
                             trace: Boolean,
                             sslHandshakeTimeout: Long?,
                             private val bridgeConnectionTTLSeconds: Int) : BridgeManager {

    private val lock = ReentrantLock()
    private val queueNamesToBridgesMap = mutableMapOf<String, MutableList<AMQPBridge>>()

    private class AMQPConfigurationImpl(override val keyStore: CertificateStore,
                                        override val trustStore: CertificateStore,
                                        override val proxyConfig: ProxyConfig?,
                                        override val maxMessageSize: Int,
                                        override val revocationConfig: RevocationConfig,
                                        override val useOpenSsl: Boolean,
                                        override val enableSNI: Boolean,
                                        override val sourceX500Name: String? = null,
                                        override val trace: Boolean,
                                        private val _sslHandshakeTimeout: Long?) : AMQPConfiguration {
        override val sslHandshakeTimeout: Long
            get() = _sslHandshakeTimeout ?: super.sslHandshakeTimeout
    }

    private val amqpConfig: AMQPConfiguration = AMQPConfigurationImpl(keyStore, trustStore, proxyConfig, maxMessageSize, revocationConfig,useOpenSSL, enableSNI, trace = trace, _sslHandshakeTimeout = sslHandshakeTimeout)
    private var sharedEventLoopGroup: EventLoopGroup? = null
    private var artemis: ArtemisSessionProvider? = null

    companion object {

        private const val CORDA_NUM_BRIDGE_THREADS_PROP_NAME = "net.corda.nodeapi.amqpbridgemanager.NumBridgeThreads"

        private val NUM_BRIDGE_THREADS = Integer.getInteger(CORDA_NUM_BRIDGE_THREADS_PROP_NAME, 0) // Default 0 means Netty default sized pool
        private const val ARTEMIS_RETRY_BACKOFF = 5000L
    }

    /**
     * Each AMQPBridge is an independent consumer of messages from the Artemis local queue per designated endpoint.
     * It attempts to deliver these messages via an AMQPClient instance to the remote Artemis inbox.
     * To prevent race conditions the Artemis session/consumer is only created when the AMQPClient has a stable AMQP connection.
     * The acknowledgement and removal of messages from the local queue only occurs if there successful end-to-end delivery.
     * If the delivery fails the session is rolled back to prevent loss of the message. This may cause duplicate delivery,
     * however Artemis and the remote Corda instanced will deduplicate these messages.
     */
    @Suppress("TooManyFunctions")
    private class AMQPBridge(val sourceX500Name: String,
                             val queueName: String,
                             val targets: List<NetworkHostAndPort>,
                             val legalNames: Set<CordaX500Name>,
                             private val amqpConfig: AMQPConfiguration,
                             sharedEventGroup: EventLoopGroup,
                             private val artemis: ArtemisSessionProvider,
                             private val bridgeMetricsService: BridgeMetricsService?,
                             private val bridgeConnectionTTLSeconds: Int) {
        companion object {
            private val log = contextLogger()
        }

        private fun withMDC(block: () -> Unit) {
            val oldMDC = MDC.getCopyOfContextMap() ?: emptyMap<String, String>()
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
        private var session: ClientSession? = null
        private var consumer: ClientConsumer? = null
        private var connectedSubscription: Subscription? = null
        @Volatile
        private var messagesReceived: Boolean = false
        private val eventLoop: EventLoop = sharedEventGroup.next()
        private var artemisState: ArtemisState = ArtemisState.STOPPED
            set(value) {
                logDebugWithMDC { "State change $field to $value" }
                field = value
            }
        @Suppress("MagicNumber")
        private var artemisHeartbeatPlusBackoff = TimeUnit.SECONDS.toMillis(90)
        private var amqpRestartEvent: ScheduledFuture<Unit>? = null
        private var scheduledExecutorService: ScheduledExecutorService
                = Executors.newSingleThreadScheduledExecutor(ThreadFactoryBuilder().setNameFormat("bridge-connection-reset-%d").build())

        @Suppress("ClassNaming")
        private sealed class ArtemisState {
            object STARTING : ArtemisState()
            data class STARTED(override val pending: ScheduledFuture<Unit>) : ArtemisState()

            object CHECKING : ArtemisState()
            object RESTARTED : ArtemisState()
            object RECEIVING : ArtemisState()

            object AMQP_STOPPED : ArtemisState()
            object AMQP_STARTING : ArtemisState()
            object AMQP_STARTED : ArtemisState()
            object AMQP_RESTARTED : ArtemisState()

            object STOPPING : ArtemisState()
            object STOPPED : ArtemisState()
            data class STOPPED_AMQP_START_SCHEDULED(override val pending: ScheduledFuture<Unit>) : ArtemisState()

            open val pending: ScheduledFuture<Unit>? = null

            override fun toString(): String = javaClass.simpleName
        }

        private fun artemis(inProgress: ArtemisState, block: (precedingState: ArtemisState) -> ArtemisState) {
            val runnable = {
                synchronized(artemis) {
                    try {
                        val precedingState = artemisState
                        artemisState.pending?.cancel(false)
                        artemisState = inProgress
                        artemisState = block(precedingState)
                    } catch (ex: Exception) {
                        withMDC { log.error("Unexpected error in Artemis processing in state $artemisState.", ex) }
                    }
                }
            }
            if (eventLoop.inEventLoop()) {
                runnable()
            } else {
                eventLoop.execute(runnable)
            }
        }

        private fun scheduledArtemis(delay: Long, unit: TimeUnit, inProgress: ArtemisState, block: (precedingState: ArtemisState) -> ArtemisState): ScheduledFuture<Unit> {
            return eventLoop.schedule<Unit>({
                artemis(inProgress, block)
            }, delay, unit)
        }

        private fun scheduledArtemisInExecutor(delay: Long, unit: TimeUnit, inProgress: ArtemisState, nextState: ArtemisState, block: () -> Unit): ScheduledFuture<Unit> {
            return scheduledExecutorService.schedule<Unit>({
                artemis(inProgress) {
                    nextState
                }
                block()
            }, delay, unit)
        }

        fun start() {
            logInfoWithMDC("Create new AMQP bridge")
            connectedSubscription = amqpClient.onConnection.subscribe { x -> onSocketConnected(x.connected) }
            amqpClient.start()
        }

        fun stop() {
            logInfoWithMDC("Stopping AMQP bridge")
            artemis(ArtemisState.STOPPING) {
                logInfoWithMDC("Stopping Artemis because stopping AMQP bridge")
                closeConsumer()
                consumer = null
                eventLoop.execute {
                    artemis(ArtemisState.STOPPING) {
                        stopSession()
                        session = null
                        ArtemisState.STOPPED
                    }
                }
                ArtemisState.STOPPING
            }
            bridgeMetricsService?.bridgeDisconnected(targets, legalNames)
            connectedSubscription?.unsubscribe()
            connectedSubscription = null
            // Do this last because we already scheduled the Artemis stop, so it's okay to unsubscribe onConnected first.
            amqpClient.stop()
        }

        @Suppress("ComplexMethod")
        private fun onSocketConnected(connected: Boolean) {
            if (connected) {
                logInfoWithMDC("Bridge Connected")

                bridgeMetricsService?.bridgeConnected(targets, legalNames)
                if (bridgeConnectionTTLSeconds > 0) {
                    // AMQP outbound connection will be restarted periodically with bridgeConnectionTTLSeconds interval
                    amqpRestartEvent = scheduledArtemisInExecutor(bridgeConnectionTTLSeconds.toLong(), TimeUnit.SECONDS,
                                                                                   ArtemisState.AMQP_STOPPED, ArtemisState.AMQP_RESTARTED) {
                        logInfoWithMDC("Bridge connection time to live exceeded. Restarting AMQP connection")
                        stopAndStartOutbound(ArtemisState.AMQP_RESTARTED)
                    }
                }
                artemis(ArtemisState.STARTING) {
                    val startedArtemis = artemis.started
                    if (startedArtemis == null) {
                        logInfoWithMDC("Bridge Connected but Artemis is disconnected")
                        ArtemisState.STOPPED
                    } else {
                        logInfoWithMDC("Bridge Connected so starting Artemis")
                        artemisHeartbeatPlusBackoff = startedArtemis.serverLocator.connectionTTL + ARTEMIS_RETRY_BACKOFF
                        try {
                            createSessionAndConsumer(startedArtemis)
                            ArtemisState.STARTED(scheduledArtemis(artemisHeartbeatPlusBackoff, TimeUnit.MILLISECONDS, ArtemisState.CHECKING) {
                                if (!messagesReceived) {
                                    logInfoWithMDC("No messages received on new bridge. Restarting Artemis session")
                                    if (restartSession()) {
                                        ArtemisState.RESTARTED
                                    } else {
                                        logInfoWithMDC("Artemis session restart failed. Aborting by restarting AMQP connection.")
                                        stopAndStartOutbound()
                                    }
                                } else {
                                    ArtemisState.RECEIVING
                                }
                            })
                        } catch (ex: Exception) {
                            // Now, bounce the AMQP connection to restart the sequence of establishing the connectivity back from the beginning.
                            withMDC { log.warn("Create Artemis start session error. Restarting AMQP connection", ex) }
                            stopAndStartOutbound()
                        }
                    }
                }
            } else {
                logInfoWithMDC("Bridge Disconnected")
                amqpRestartEvent?.cancel(false)
                if (artemisState != ArtemisState.AMQP_STARTING && artemisState != ArtemisState.STOPPED) {
                    bridgeMetricsService?.bridgeDisconnected(targets, legalNames)
                }
                artemis(ArtemisState.STOPPING) { precedingState: ArtemisState ->
                    logInfoWithMDC("Stopping Artemis because AMQP bridge disconnected")
                    closeConsumer()
                    consumer = null
                    eventLoop.execute {
                        artemis(ArtemisState.STOPPING) {
                            stopSession()
                            session = null
                            when (precedingState) {
                                ArtemisState.AMQP_STOPPED ->
                                                   ArtemisState.STOPPED_AMQP_START_SCHEDULED(scheduledArtemis(artemisHeartbeatPlusBackoff,
                                                                   TimeUnit.MILLISECONDS, ArtemisState.AMQP_STARTING) { startOutbound() })
                                ArtemisState.AMQP_RESTARTED -> {
                                    artemis(ArtemisState.AMQP_STARTING) { startOutbound() }
                                    ArtemisState.AMQP_STARTING
                                }
                                else -> ArtemisState.STOPPED
                            }
                        }
                    }
                    ArtemisState.STOPPING
                }
            }
        }

        private fun startOutbound(): ArtemisState {
            logInfoWithMDC("Starting AMQP client")
            amqpClient.start()
            return ArtemisState.AMQP_STARTED
        }

        private fun stopAndStartOutbound(state: ArtemisState = ArtemisState.AMQP_STOPPED): ArtemisState {
            amqpClient.stop()
            // Bridge disconnect will detect this state and schedule an AMQP start.
            return state
        }

        private fun createSessionAndConsumer(startedArtemis: ArtemisMessagingClient.Started): ClientSession {
            logInfoWithMDC("Creating session and consumer.")
            val sessionFactory = startedArtemis.sessionFactory
            val session = sessionFactory.createSession(NODE_P2P_USER, NODE_P2P_USER, false, true,
                                        true, false, DEFAULT_ACK_BATCH_SIZE)
            this.session = session
            // Several producers (in the case of shared bridge) can put messages in the same outbound p2p queue.
            // The consumers are created using the source x500 name as a filter
            val consumer = if (amqpConfig.enableSNI) {
                session.createConsumer(queueName, "hyphenated_props:sender-subject-name = '${amqpConfig.sourceX500Name}'")
            } else {
                session.createConsumer(queueName)
            }
            this.consumer = consumer
            session.start()
            consumer.setMessageHandler(this@AMQPBridge::clientArtemisMessageHandler)
            return session
        }

        private fun closeConsumer(): Boolean {
            var closed = false
            try {
                consumer?.apply {
                    if (!isClosed) {
                        close()
                    }
                }
                closed = true
            } catch (ex: Exception) {
                withMDC { log.warn("Close artemis consumer error", ex) }
            } finally {
                return closed
            }
        }

        private fun stopSession(): Boolean {
            var stopped = false
            try {
                session?.apply {
                    if (!isClosed) {
                        stop()
                    }
                }
                stopped = true
            } catch (ex: Exception) {
                withMDC { log.warn("Stop Artemis session error", ex) }
            } finally {
                return stopped
            }
        }

        private fun restartSession(): Boolean {
            if (!stopSession()) {
                // Session timed out stopping.  The request/responses can be out of sequence on the session now, so abandon it.
                session = null
                // The consumer is also dead now too as attached to the dead session.
                consumer = null
                return false
            }
            try {
                // Does not wait for a response.
                this.session?.start()
            } catch (ex: Exception) {
                withMDC { log.error("Start Artemis session error", ex) }
            }
            return true
        }

        private fun clientArtemisMessageHandler(artemisMessage: ClientMessage) {
            messagesReceived = true
            if (artemisMessage.bodySize > amqpConfig.maxMessageSize) {
                val msg = "Message exceeds maxMessageSize network parameter, maxMessageSize: [${amqpConfig.maxMessageSize}], message size: [${artemisMessage.bodySize}], " +
                        "dropping message, uuid: ${artemisMessage.getObjectProperty(MESSAGE_ID_KEY)}"
                logWarnWithMDC(msg)
                bridgeMetricsService?.packetDropEvent(artemisMessage, msg)
                // Ack the message to prevent same message being sent to us again.
                try {
                    artemisMessage.individualAcknowledge()
                } catch (ex: ActiveMQObjectClosedException) {
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
            logDebugWithMDC { "Bridged Send to ${legalNames.first()} uuid: ${artemisMessage.getObjectProperty(MESSAGE_ID_KEY)}" }
            val peerInbox = translateLocalQueueToInboxAddress(queueName)
            val sendableMessage = amqpClient.createMessage(artemisMessage.payload(), peerInbox,
                    legalNames.first().toString(),
                    properties)
            sendableMessage.onComplete.then {
                logDebugWithMDC { "Bridge ACK ${sendableMessage.onComplete.get()}" }
                eventLoop.submit {
                    if (sendableMessage.onComplete.get() == MessageStatus.Acknowledged) {
                        try {
                            artemisMessage.individualAcknowledge()
                        } catch (ex: ActiveMQObjectClosedException) {
                            log.warn("Artemis message was closed")
                        }
                    } else {
                        logInfoWithMDC("Rollback rejected message uuid: ${artemisMessage.getObjectProperty(MESSAGE_ID_KEY)}")
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
                eventLoop.submit {
                    ex.message?.let { logInfoWithMDC(it) }
                    logInfoWithMDC("Rollback rejected message uuid: ${artemisMessage.getObjectProperty(MESSAGE_ID_KEY)}")
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
            val newAMQPConfig = with(amqpConfig) { AMQPConfigurationImpl(keyStore, trustStore, proxyConfig, maxMessageSize,
                                                   revocationConfig, useOpenSsl, enableSNI, sourceX500Name, trace, sslHandshakeTimeout) }
            val newBridge = AMQPBridge(sourceX500Name, queueName, targets, legalNames, newAMQPConfig, sharedEventLoopGroup!!, artemis!!,
                                       bridgeMetricsService, bridgeConnectionTTLSeconds)
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