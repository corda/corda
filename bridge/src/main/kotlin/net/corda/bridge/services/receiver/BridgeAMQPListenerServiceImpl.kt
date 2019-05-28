package net.corda.bridge.services.receiver

import net.corda.bridge.services.api.*
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.lang.String.valueOf
import java.security.KeyStore
import java.util.*

class BridgeAMQPListenerServiceImpl(val conf: FirewallConfiguration,
                                    val auditService: FirewallAuditService,
                                    private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeAMQPListenerService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
        private val consoleLogger = LoggerFactory.getLogger("BasicInfo")
    }

    private val statusFollower: ServiceStateCombiner = ServiceStateCombiner(listOf(auditService))
    private var statusSubscriber: Subscription? = null
    private var amqpServer: AMQPServer? = null
    private var keyStorePrivateKeyPassword: CharArray? = null
    private var onConnectSubscription: Subscription? = null
    private var onConnectAuditSubscription: Subscription? = null
    private var onReceiveSubscription: Subscription? = null

    override fun provisionKeysAndActivate(keyStore: CertificateStore, trustStore:CertificateStore, maximumMessageSize: Int) {
        require(active) { "AuditService must be active" }
        val bindAddress = conf.inboundConfig!!.listeningAddress
        val amqpConfiguration = object : AMQPConfiguration {
            //TODO: No password for delegated keystore. Refactor this?
            override val keyStore = keyStore
            override val trustStore = trustStore
            override val maxMessageSize: Int = maximumMessageSize
            override val trace: Boolean = conf.enableAMQPPacketTrace
            override val enableSNI: Boolean = conf.bridgeInnerConfig?.enableSNI ?: true
            override val healthCheckPhrase = conf.healthCheckPhrase
            override val silencedIPs: Set<String> = conf.silencedIPs
            override val sslHandshakeTimeout: Long = conf.sslHandshakeTimeout
            override val revocationConfig: RevocationConfig = conf.revocationConfig
        }
        val server = AMQPServer(bindAddress.host,
                bindAddress.port,
                amqpConfiguration)
        onConnectSubscription = server.onConnection.subscribe(_onConnection)
        onConnectAuditSubscription = server.onConnection.subscribe({
            if (it.connected) {
                auditService.successfulConnectionEvent(it.remoteAddress, it.remoteCert?.subjectDN?.name
                        ?: "", "Successful AMQP inbound connection", RoutingDirection.INBOUND)
            } else {
                auditService.failedConnectionEvent(it.remoteAddress, it.remoteCert?.subjectDN?.name
                        ?: "", "Failed AMQP inbound connection", RoutingDirection.INBOUND)
            }
        }, { log.error("Connection event error", it) })
        onReceiveSubscription = server.onReceive.subscribe(_onReceive)
        amqpServer = server
        server.start()
        val msg = "Now listening for incoming connections on $bindAddress"
        auditService.statusChangeEvent(msg)
        consoleLogger.info(msg)
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