package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_CONTROL_TOPIC
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readAll
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import rx.Subscription
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TunnelingBridgeReceiverService(val conf: FirewallConfiguration,
                                     val maximumMessageSize: Int,
                                     val auditService: FirewallAuditService,
                                     haService: BridgeMasterService,
                                     val filterService: IncomingMessageFilterService,
                                     private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeReceiverService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private var connectSubscriber: Subscription? = null
    private var receiveSubscriber: Subscription? = null
    private var amqpControlClient: AMQPClient? = null
    private val controlLinkSSLConfiguration: MutualSslConfiguration
    private val floatListenerSSLConfiguration: MutualSslConfiguration
    private val expectedCertificateSubject: CordaX500Name
    private val secureRandom: SecureRandom = newSecureRandom()

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService, haService, filterService))
        controlLinkSSLConfiguration = conf.bridgeInnerConfig?.customSSLConfiguration ?: conf.p2pSslOptions
        floatListenerSSLConfiguration = conf.bridgeInnerConfig?.customFloatOuterSSLConfiguration ?: conf.p2pSslOptions
        expectedCertificateSubject = conf.bridgeInnerConfig!!.expectedCertificateSubject
    }


    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            if (it) {
                val floatAddresses = conf.bridgeInnerConfig!!.floatAddresses
                val controlLinkKeyStore = controlLinkSSLConfiguration.keyStore.get()
                val controlLinkTrustStore = controlLinkSSLConfiguration.trustStore.get()
                val amqpConfig = object : AMQPConfiguration {
                    override val userName: String? = null
                    override val password: String? = null
                    override val keyStore = controlLinkKeyStore
                    override val trustStore = controlLinkTrustStore
                    override val crlCheckSoftFail: Boolean = conf.crlCheckSoftFail
                    override val maxMessageSize: Int = maximumMessageSize
                    override val trace: Boolean = conf.enableAMQPPacketTrace

                }
                val controlClient = AMQPClient(floatAddresses,
                        setOf(expectedCertificateSubject),
                        amqpConfig)
                connectSubscriber = controlClient.onConnection.subscribe({ onConnectToControl(it) }, { log.error("Connection event error", it) })
                receiveSubscriber = controlClient.onReceive.subscribe({ onFloatMessage(it) }, { log.error("Receive event error", it) })
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
            val deactivateMessage = DeactivateFloat()
            val amqpDeactivateMessage = amqpControlClient!!.createMessage(deactivateMessage.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes,
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
            val (freshKeyStorePassword, freshKeyStoreKeyPassword, recodedKeyStore) = recodeKeyStore(floatListenerSSLConfiguration)
            val trustStoreBytes = floatListenerSSLConfiguration.trustStore.path.readAll()
            val activateMessage = ActivateFloat(recodedKeyStore,
                    freshKeyStorePassword,
                    freshKeyStoreKeyPassword,
                    trustStoreBytes,
                    floatListenerSSLConfiguration.trustStore.password.toCharArray())
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
                //TODO Retry?
            }
        } else {
            stateHelper.active = false
        }
    }

    // Recode KeyStore to use a fresh random password for entries and overall
    private fun recodeKeyStore(sslConfiguration: MutualSslConfiguration): Triple<CharArray, CharArray, ByteArray> {
        val keyStoreOriginal = sslConfiguration.keyStore.get().value.internal
        val originalKeyStorePassword = sslConfiguration.keyStore.password.toCharArray()
        val freshKeyStorePassword = CharArray(20) { secureRandom.nextInt(0xD800).toChar() } // Stick to single character Unicode range
        val freshPrivateKeyPassword = CharArray(20) { secureRandom.nextInt(0xD800).toChar() } // Stick to single character Unicode range
        for (alias in keyStoreOriginal.aliases()) {
            if (keyStoreOriginal.isKeyEntry(alias)) {
                // Recode key entries to new password
                val privateKey = keyStoreOriginal.getKey(alias, originalKeyStorePassword)
                val certs = keyStoreOriginal.getCertificateChain(alias)
                keyStoreOriginal.setKeyEntry(alias, privateKey, freshPrivateKeyPassword, certs)
            }
        }
        // Serialize re-keyed KeyStore to ByteArray
        val recodedKeyStore = ByteArrayOutputStream().use {
            keyStoreOriginal.store(it, freshKeyStorePassword)
            it
        }.toByteArray()

        return Triple(freshKeyStorePassword, freshPrivateKeyPassword, recodedKeyStore)
    }

    private fun onFloatMessage(receivedMessage: ReceivedMessage) {
        if (!receivedMessage.checkTunnelDataTopic()) {
            auditService.packetDropEvent(receivedMessage, "Invalid float inbound topic received ${receivedMessage.topic}!!")
            receivedMessage.complete(true)
            return
        }
        val innerMessage = try {
            receivedMessage.payload.deserialize<FloatDataPacket>()
        } catch (ex: Exception) {
            auditService.packetDropEvent(receivedMessage, "Unable to decode Float Control message")
            receivedMessage.complete(true)
            return
        }
        log.debug { "Received message from ${innerMessage.sourceLegalName}" }
        val onwardMessage = object : ReceivedMessage {
            override val topic: String = innerMessage.topic
            override val applicationProperties: Map<String, Any?> = innerMessage.originalHeaders.toMap()
            override val payload: ByteArray = innerMessage.originalPayload
            override val sourceLegalName: String = innerMessage.sourceLegalName.toString()
            override val sourceLink: NetworkHostAndPort = receivedMessage.sourceLink

            override fun complete(accepted: Boolean) {
                receivedMessage.complete(accepted)
            }

            override val destinationLegalName: String = innerMessage.destinationLegalName.toString()
            override val destinationLink: NetworkHostAndPort = innerMessage.destinationLink
        }
        filterService.sendMessageToLocalBroker(onwardMessage)
    }

}