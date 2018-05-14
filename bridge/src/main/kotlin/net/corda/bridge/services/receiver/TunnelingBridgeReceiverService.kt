/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import rx.Subscription
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TunnelingBridgeReceiverService(val conf: BridgeConfiguration,
                                     val auditService: BridgeAuditService,
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
    private val controlLinkSSLConfiguration: SSLConfiguration
    private val floatListenerSSLConfiguration: SSLConfiguration
    private val controlLinkKeyStore: KeyStore
    private val controLinkKeyStorePrivateKeyPassword: String
    private val controlLinkTrustStore: KeyStore
    private val expectedCertificateSubject: CordaX500Name
    private val secureRandom: SecureRandom = newSecureRandom()

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService, haService, filterService))
        controlLinkSSLConfiguration = conf.bridgeInnerConfig?.customSSLConfiguration ?: conf
        floatListenerSSLConfiguration = conf.bridgeInnerConfig?.customFloatOuterSSLConfiguration ?: conf
        controlLinkKeyStore = controlLinkSSLConfiguration.loadSslKeyStore().internal
        controLinkKeyStorePrivateKeyPassword = controlLinkSSLConfiguration.keyStorePassword
        controlLinkTrustStore = controlLinkSSLConfiguration.loadTrustStore().internal
        expectedCertificateSubject = conf.bridgeInnerConfig!!.expectedCertificateSubject
    }


    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe {
            if (it) {
                val floatAddresses = conf.bridgeInnerConfig!!.floatAddresses
                val controlClient = AMQPClient(floatAddresses, setOf(expectedCertificateSubject), null, null, controlLinkKeyStore, controLinkKeyStorePrivateKeyPassword, controlLinkTrustStore, conf.crlCheckSoftFail, conf.enableAMQPPacketTrace)
                connectSubscriber = controlClient.onConnection.subscribe { onConnectToControl(it) }
                receiveSubscriber = controlClient.onReceive.subscribe { onFloatMessage(it) }
                amqpControlClient = controlClient
                controlClient.start()
            } else {
                stateHelper.active = false
                closeAMQPClient()
            }
        }
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
            val trustStoreBytes = floatListenerSSLConfiguration.trustStoreFile.readAll()
            val activateMessage = ActivateFloat(recodedKeyStore,
                    freshKeyStorePassword,
                    freshKeyStoreKeyPassword,
                    trustStoreBytes,
                    floatListenerSSLConfiguration.trustStorePassword.toCharArray())
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
    private fun recodeKeyStore(sslConfiguration: SSLConfiguration): Triple<CharArray, CharArray, ByteArray> {
        val keyStoreOriginal = sslConfiguration.loadSslKeyStore().internal
        val originalKeyStorePassword = sslConfiguration.keyStorePassword.toCharArray()
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