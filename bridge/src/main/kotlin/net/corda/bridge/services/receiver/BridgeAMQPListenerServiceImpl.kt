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

import net.corda.bridge.services.api.BridgeAMQPListenerService
import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.*

class BridgeAMQPListenerServiceImpl(val conf: FirewallConfiguration,
                                    val maximumMessageSize: Int,
                                    val auditService: FirewallAuditService,
                                    private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeAMQPListenerService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
        val consoleLogger = LoggerFactory.getLogger("BasicInfo")
    }

    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private var amqpServer: AMQPServer? = null
    private var keyStorePrivateKeyPassword: CharArray? = null
    private var onConnectSubscription: Subscription? = null
    private var onConnectAuditSubscription: Subscription? = null
    private var onReceiveSubscription: Subscription? = null

    init {
        statusFollower = ServiceStateCombiner(listOf(auditService))
    }

    override fun provisionKeysAndActivate(keyStoreBytes: ByteArray,
                                          keyStorePassword: CharArray,
                                          keyStorePrivateKeyPassword: CharArray,
                                          trustStoreBytes: ByteArray,
                                          trustStorePassword: CharArray) {
        require(active) { "AuditService must be active" }
        require(keyStorePassword !== keyStorePrivateKeyPassword) { "keyStorePassword and keyStorePrivateKeyPassword must reference distinct arrays!" }
        val keyStore = loadKeyStoreAndWipeKeys(keyStoreBytes, keyStorePassword)
        val trustStore = loadKeyStoreAndWipeKeys(trustStoreBytes, trustStorePassword)
        val bindAddress = conf.inboundConfig!!.listeningAddress
        val amqpConfiguration = object : AMQPConfiguration {
            override val keyStore: KeyStore = keyStore
            override val keyStorePrivateKeyPassword: CharArray = keyStorePrivateKeyPassword
            override val trustStore: KeyStore = trustStore
            override val crlCheckSoftFail: Boolean = conf.crlCheckSoftFail
            override val maxMessageSize: Int = maximumMessageSize
            override val trace: Boolean = conf.enableAMQPPacketTrace
        }
        val server = AMQPServer(bindAddress.host,
                bindAddress.port,
                amqpConfiguration)
        onConnectSubscription = server.onConnection.subscribe(_onConnection)
        onConnectAuditSubscription = server.onConnection.subscribe({
            if (it.connected) {
                auditService.successfulConnectionEvent(true, it.remoteAddress, it.remoteCert?.subjectDN?.name
                        ?: "", "Successful AMQP inbound connection")
            } else {
                auditService.failedConnectionEvent(true, it.remoteAddress, it.remoteCert?.subjectDN?.name
                        ?: "", "Failed AMQP inbound connection")
            }
        }, { log.error("Connection event error", it) })
        onReceiveSubscription = server.onReceive.subscribe(_onReceive)
        amqpServer = server
        server.start()
        val msg = "Now listening for incoming connections on $bindAddress"
        auditService.statusChangeEvent(msg)
        consoleLogger.info(msg)
    }

    private fun loadKeyStoreAndWipeKeys(keyStoreBytes: ByteArray, keyStorePassword: CharArray): KeyStore {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        ByteArrayInputStream(keyStoreBytes).use {
            keyStore.load(it, keyStorePassword)
        }
        // We overwrite the keys we don't need anymore
        Arrays.fill(keyStoreBytes, 0xAA.toByte())
        Arrays.fill(keyStorePassword, 0xAA55.toChar())
        return keyStore
    }

    override fun wipeKeysAndDeactivate() {
        onReceiveSubscription?.unsubscribe()
        onReceiveSubscription = null
        onConnectSubscription?.unsubscribe()
        onConnectSubscription = null
        onConnectAuditSubscription?.unsubscribe()
        onConnectAuditSubscription = null
        if (running) {
            val msg = "AMQP Listener shutting down"
            auditService.statusChangeEvent(msg)
            consoleLogger.info(msg)
        }
        amqpServer?.close()
        amqpServer = null
        if (keyStorePrivateKeyPassword != null) {
            // Wipe the old password
            Arrays.fill(keyStorePrivateKeyPassword, 0xAA55.toChar())
            keyStorePrivateKeyPassword = null
        }
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            stateHelper.active = it
        }, { log.error("Error in state change", it) })
    }

    override fun stop() {
        stateHelper.active = false
        wipeKeysAndDeactivate()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    override val running: Boolean
        get() = amqpServer?.listening ?: false

    private val _onReceive = PublishSubject.create<ReceivedMessage>().toSerialized()
    override val onReceive: Observable<ReceivedMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChange>().toSerialized()
    override val onConnection: Observable<ConnectionChange>
        get() = _onConnection

}