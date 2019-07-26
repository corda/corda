package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.crl.CrlFetcher
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_CONTROL_TOPIC
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_CRL_TOPIC
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_DATA_TOPIC
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_SIGNING_TOPIC
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import rx.Subscription
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * @see BridgeReceiverService
 */
class TunnelingBridgeReceiverService(val conf: FirewallConfiguration,
                                     private val maximumMessageSize: Int,
                                     val auditService: FirewallAuditService,
                                     haService: BridgeMasterService,
                                     private val tunnelingSigningService: TLSSigningService,
                                     private val signingService: TLSSigningService,
                                     private val filterService: IncomingMessageFilterService,
                                     private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeReceiverService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
        private val emptyPayload = ByteArray(0)
    }

    private val statusFollower = ServiceStateCombiner(listOf(auditService, haService, filterService, tunnelingSigningService, signingService))
    private var statusSubscriber: Subscription? = null
    private var connectSubscriber: Subscription? = null
    private var receiveSubscriber: Subscription? = null
    private var amqpControlClient: AMQPClient? = null
    private val expectedCertificateSubject: CordaX500Name = conf.bridgeInnerConfig!!.expectedCertificateSubject
    private val crlFetcher = CrlFetcher(conf.outboundConfig?.proxyConfig)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            if (it) {
                val floatAddresses = conf.bridgeInnerConfig!!.floatAddresses
                val controlLinkKeyStore = tunnelingSigningService.keyStore()
                val controlLinkTrustStore = tunnelingSigningService.truststore()
                val amqpConfig = object : AMQPConfiguration {
                    override val userName: String? = null
                    override val password: String? = null
                    override val keyStore = controlLinkKeyStore
                    override val trustStore = controlLinkTrustStore
                    override val maxMessageSize: Int = Int.MAX_VALUE
                    override val trace: Boolean = conf.enableAMQPPacketTrace
                    override val enableSNI: Boolean = conf.bridgeInnerConfig!!.enableSNI
                    override val healthCheckPhrase = conf.healthCheckPhrase
                    override val sslHandshakeTimeout: Long = conf.sslHandshakeTimeout
                    override val revocationConfig: RevocationConfig = conf.revocationConfig
                }
                val controlClient = AMQPClient(floatAddresses,
                        setOf(expectedCertificateSubject),
                        amqpConfig)
                connectSubscriber = controlClient.onConnection.subscribe(::onConnectToControl) { log.error("Connection event error", it) }
                receiveSubscriber = controlClient.onReceive.subscribe(::onFloatMessage) { log.error("Receive event error", it) }
                amqpControlClient = controlClient
                controlClient.start()
            } else {
                stateHelper.active = false
                closeAMQPClient()
            }
        }, { log.error("Error in state change", it) })
    }

    private fun closeAMQPClient() {
        connectSubscriber?.unsubscribe()
        connectSubscriber = null
        receiveSubscriber?.unsubscribe()
        receiveSubscriber = null
        amqpControlClient?.apply {
            val amqpDeactivateMessage = amqpControlClient!!.createMessage(DeactivateFloat.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                    FLOAT_CONTROL_TOPIC,
                    expectedCertificateSubject.toString(),
                    emptyMap())
            try {
                amqpControlClient!!.write(amqpDeactivateMessage)
            } catch (ex: IllegalStateException) {
                // ignore if channel is already closed
            }
            try {
                // Await acknowledgement of the deactivate message, but don't block our shutdown forever.
                amqpDeactivateMessage.onComplete.get(conf.politeShutdownPeriod.toLong(), TimeUnit.MILLISECONDS)
            } catch (ex: TimeoutException) {
                // Ignore
            }
            stop()
        }
        amqpControlClient = null
    }

    override fun stop() {
        stateHelper.active = false
        closeAMQPClient()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    private fun onConnectToControl(connectionChange: ConnectionChange) {
        auditService.statusChangeEvent("Connection change on float control port $connectionChange")
        if (connectionChange.connected) {
            val trustStore = signingService.truststore()
            val trustStoreBytes = ByteArrayOutputStream()
            trustStore.writeTo(trustStoreBytes)
            val activateMessage = ActivateFloat(signingService.certificates(), trustStoreBytes.toByteArray(), trustStore.password.toCharArray(), maximumMessageSize)
            val amqpActivateMessage = amqpControlClient!!.createMessage(activateMessage.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                    FLOAT_CONTROL_TOPIC,
                    expectedCertificateSubject.toString(),
                    emptyMap())
            try {
                amqpControlClient!!.write(amqpActivateMessage)
            } catch (ex: IllegalStateException) {
                stateHelper.active = false // lost the channel
                return
            }
            amqpActivateMessage.onComplete.then {
                stateHelper.active = (it.get() == MessageStatus.Acknowledged)
            }
        } else {
            stateHelper.active = false
        }
    }

    private fun onFloatMessage(receivedMessage: ReceivedMessage) {
        when (receivedMessage.topic) {
            FLOAT_DATA_TOPIC -> processDataTopic(receivedMessage)
            FLOAT_SIGNING_TOPIC -> processSigningTopic(receivedMessage)
            FLOAT_CRL_TOPIC -> processCrlTopic(receivedMessage)
            else -> {
                auditService.packetDropEvent(receivedMessage, "Invalid float inbound topic received ${receivedMessage.topic}!!", RoutingDirection.INBOUND)
                receivedMessage.complete(true)
                return
            }
        }
    }

    private fun processDataTopic(receivedMessage: ReceivedMessage) {
        val innerMessage = try {
            receivedMessage.payload.deserialize<FloatDataPacket>()
        } catch (ex: Exception) {
            auditService.packetDropEvent(receivedMessage, "Unable to decode Float Control message", RoutingDirection.INBOUND)
            receivedMessage.complete(true)
            return
        }
        log.debug { "Received message from ${innerMessage.sourceLegalName}" }
        val onwardMessage = object : ReceivedMessage {
            override val topic: String = innerMessage.topic
            override val applicationProperties: Map<String, Any?> = innerMessage.originalHeaders.toMap()
            override var payload: ByteArray = innerMessage.originalPayload
            override val sourceLegalName: String = innerMessage.sourceLegalName.toString()
            override val sourceLink: NetworkHostAndPort = receivedMessage.sourceLink

            override fun release() {
                payload = emptyPayload
            }

            override fun complete(accepted: Boolean) {
                receivedMessage.complete(accepted)
            }

            override val destinationLegalName: String = innerMessage.destinationLegalName.toString()
            override val destinationLink: NetworkHostAndPort = innerMessage.destinationLink
        }
        receivedMessage.release()
        filterService.sendMessageToLocalBroker(onwardMessage)
    }

    private fun processSigningTopic(receivedMessage: ReceivedMessage) {
        val request = try {
            receivedMessage.payload.deserialize<SigningRequest>()
        } catch (ex: Exception) {
            val msg = "Unable to decode signing request message"
            log.error(msg, ex)
            auditService.packetDropEvent(receivedMessage, msg, RoutingDirection.INBOUND)
            return
        } finally {
            receivedMessage.complete(true)
        }
        log.info("Received signing request '${request.requestId}' using key ${request.alias}. Algo: ${request.sigAlgo}")
        executor.submit {
            val response = SigningResponse(request.requestId, signingService.sign(request.alias, request.sigAlgo, request.data))

            val amqpSigningResponse = amqpControlClient!!.createMessage(response.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                    FloatControlTopics.FLOAT_SIGNING_TOPIC,
                    expectedCertificateSubject.toString(),
                    emptyMap())
            amqpControlClient!!.write(amqpSigningResponse)
            log.info("Sent signing response '${request.requestId}' using key ${request.alias}.")
        }
    }

    private fun processCrlTopic(receivedMessage: ReceivedMessage) {
        val request = try {
            receivedMessage.payload.deserialize<CrlRequest>()
        } catch (ex: Exception) {
            val msg = "Unable to decode CRL request message"
            log.error(msg, ex)
            auditService.packetDropEvent(receivedMessage, msg, RoutingDirection.INBOUND)
            receivedMessage.complete(true)
            return
        } finally {
            receivedMessage.complete(true)
        }
        val certificate = request.certificate
        log.info("Received CRL request '${request.requestId}' for certificate with X.500 name: '${certificate.subjectX500Principal}'")
        val crls = crlFetcher.fetch(certificate)
        log.info("Obtained the following CRLs: $crls")
        val response = CrlResponse(request.requestId, crls)
        val amqpCrlResponse = amqpControlClient!!.createMessage(response.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
                FloatControlTopics.FLOAT_CRL_TOPIC,
                expectedCertificateSubject.toString(),
                emptyMap())
        amqpControlClient!!.write(amqpCrlResponse)
        log.info("Sent CRL response '${request.requestId}'")
    }
}