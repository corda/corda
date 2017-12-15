package net.corda.node.services.messaging

import io.netty.handler.ssl.SslHandler
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_QUEUE
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnection
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.spi.core.remoting.*
import org.apache.activemq.artemis.utils.ConfigurationHelper
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import javax.security.auth.x500.X500Principal

/**
 * This class simply moves the legacy CORE bridge code from [ArtemisMessagingServer]
 * into a class implementing [BridgeManager].
 * It has no lifecycle events, because the bridges are internal to the ActiveMQServer instance and thus
 * stop when it is stopped.
 */
internal class CoreBridgeManager(val config: NodeConfiguration, val activeMQServer: ActiveMQServer) : BridgeManager {
    companion object {
        private fun getBridgeName(queueName: String, hostAndPort: NetworkHostAndPort): String = "$queueName -> $hostAndPort"

        private val ArtemisMessagingComponent.ArtemisPeerAddress.bridgeName: String get() = getBridgeName(queueName, hostAndPort)
    }

    private fun gatherAddresses(node: NodeInfo): Sequence<ArtemisMessagingComponent.ArtemisPeerAddress> {
        val address = node.addresses.first()
        return node.legalIdentitiesAndCerts.map { ArtemisMessagingComponent.NodeAddress(it.party.owningKey, address) }.asSequence()
    }


    /**
     * All nodes are expected to have a public facing address called [ArtemisMessagingComponent.P2P_QUEUE] for receiving
     * messages from other nodes. When we want to send a message to a node we send it to our internal address/queue for it,
     * as defined by ArtemisAddress.queueName. A bridge is then created to forward messages from this queue to the node's
     * P2P address.
     */
    override fun deployBridge(queueName: String, target: NetworkHostAndPort, legalNames: Set<CordaX500Name>) {
        val connectionDirection = ConnectionDirection.Outbound(
                connectorFactoryClassName = VerifyingNettyConnectorFactory::class.java.name,
                expectedCommonNames = legalNames
        )
        val tcpTransport = ArtemisTcpTransport.tcpTransport(connectionDirection, target, config, enableSSL = true)
        tcpTransport.params[ArtemisMessagingServer::class.java.name] = this
        // We intentionally overwrite any previous connector config in case the peer legal name changed
        activeMQServer.configuration.addConnectorConfiguration(target.toString(), tcpTransport)

        activeMQServer.deployBridge(BridgeConfiguration().apply {
            name = getBridgeName(queueName, target)
            this.queueName = queueName
            forwardingAddress = P2P_QUEUE
            staticConnectors = listOf(target.toString())
            confirmationWindowSize = 100000 // a guess
            isUseDuplicateDetection = true // Enable the bridge's automatic deduplication logic
            // We keep trying until the network map deems the node unreachable and tells us it's been removed at which
            // point we destroy the bridge
            retryInterval = config.activeMQServer.bridge.retryIntervalMs
            retryIntervalMultiplier = config.activeMQServer.bridge.retryIntervalMultiplier
            maxRetryInterval = Duration.ofMinutes(config.activeMQServer.bridge.maxRetryIntervalMin).toMillis()
            // As a peer of the target node we must connect to it using the peer user. Actual authentication is done using
            // our TLS certificate.
            user = PEER_USER
            password = PEER_USER
        })
    }

    override fun bridgeExists(bridgeName: String): Boolean = activeMQServer.clusterManager.bridges.containsKey(bridgeName)

    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    override fun close() = stop()

    override fun destroyBridges(node: NodeInfo) {
        gatherAddresses(node).forEach {
            activeMQServer.destroyBridge(it.bridgeName)
        }
    }
}

class VerifyingNettyConnectorFactory : NettyConnectorFactory() {
    override fun createConnector(configuration: MutableMap<String, Any>,
                                 handler: BufferHandler?,
                                 listener: ClientConnectionLifeCycleListener?,
                                 closeExecutor: Executor?,
                                 threadPool: Executor?,
                                 scheduledThreadPool: ScheduledExecutorService?,
                                 protocolManager: ClientProtocolManager?): Connector {
        return VerifyingNettyConnector(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool,
                protocolManager)
    }

    private class VerifyingNettyConnector(configuration: MutableMap<String, Any>,
                                          handler: BufferHandler?,
                                          listener: ClientConnectionLifeCycleListener?,
                                          closeExecutor: Executor?,
                                          threadPool: Executor?,
                                          scheduledThreadPool: ScheduledExecutorService?,
                                          protocolManager: ClientProtocolManager?) :
            NettyConnector(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool, protocolManager) {
        companion object {
            private val log = contextLogger()
        }

        private val sslEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.SSL_ENABLED_PROP_NAME, TransportConstants.DEFAULT_SSL_ENABLED, configuration)

        override fun createConnection(): Connection? {
            val connection = super.createConnection() as? NettyConnection
            if (sslEnabled && connection != null) {
                val expectedLegalNames: Set<CordaX500Name> = uncheckedCast(configuration[ArtemisTcpTransport.VERIFY_PEER_LEGAL_NAME] ?: emptySet<CordaX500Name>())
                try {
                    val session = connection.channel
                            .pipeline()
                            .get(SslHandler::class.java)
                            .engine()
                            .session
                    // Checks the peer name is the one we are expecting.
                    // TODO Some problems here: after introduction of multiple legal identities on the node and removal of the main one,
                    //  we run into the issue, who are we connecting to. There are some solutions to that: advertise `network identity`;
                    //  have mapping port -> identity (but, design doc says about removing SingleMessageRecipient and having just NetworkHostAndPort,
                    //  it was convenient to store that this way); SNI.
                    val peerLegalName = CordaX500Name.parse(session.peerPrincipal.name)
                    val expectedLegalName = expectedLegalNames.singleOrNull { it == peerLegalName }
                    require(expectedLegalName != null) {
                        "Peer has wrong CN - expected $expectedLegalNames but got $peerLegalName. This is either a fatal " +
                                "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                    }
                    // Make sure certificate has the same name.
                    val peerCertificateName = CordaX500Name.build(X500Principal(session.peerCertificateChain[0].subjectDN.name))
                    require(peerCertificateName == expectedLegalName) {
                        "Peer has wrong subject name in the certificate - expected $expectedLegalNames but got $peerCertificateName. This is either a fatal " +
                                "misconfiguration by the remote peer or an SSL man-in-the-middle attack!"
                    }
                    X509Utilities.validateCertificateChain(session.localCertificates.last() as java.security.cert.X509Certificate, *session.peerCertificates)
                } catch (e: IllegalArgumentException) {
                    connection.close()
                    log.error(e.message)
                    return null
                }
            }
            return connection
        }
    }
}

