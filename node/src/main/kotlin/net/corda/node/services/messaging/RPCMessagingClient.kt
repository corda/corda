package net.corda.node.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.RPCUserService
import net.corda.node.utilities.*
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl

class RPCMessagingClient(private val config: SSLConfiguration,
                         private val serverAddress: NetworkHostAndPort
) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<RPCMessagingClient>()
    }

    private class Started(val sessionFactory: ClientSessionFactory,
                          val session: ClientSession,
                          val producer: ClientProducer,
                          val rpcServer: RPCServer) {
        fun dispose() {
            producer.close()
            // Ensure any trailing messages are committed to the journal
            session.commit()
            // Closing the factory closes all the sessions it produced as well.
            sessionFactory.close()
        }
    }

    private var started: Started? = null
    fun start(rpcOps: RPCOps, userService: RPCUserService) = synchronized(this) {
        check(started == null) { "start can't be called twice" }
        log.info("Connecting to message broker: $serverAddress")
        // TODO Add broker CN to config for host verification in case the embedded broker isn't used
        val tcpTransport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), serverAddress, config)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            // Never time out on our loopback Artemis connections. If we switch back to using the InVM transport this
            // would be the default and the two lines below can be deleted.
            connectionTTL = -1
            clientFailureCheckPeriod = -1
            minLargeMessageSize = ArtemisMessagingServer.MAX_FILE_SIZE
            isUseGlobalPools = nodeSerializationEnv != null
        }
        val sessionFactory = locator.createSessionFactory()
        // Login using the node username. The broker will authentiate us as its node (as opposed to another peer)
        // using our TLS certificate.
        // Note that the acknowledgement of messages is not flushed to the Artermis journal until the default buffer
        // size of 1MB is acknowledged.
        val session = sessionFactory!!.createSession(NODE_USER, NODE_USER, false, true, true, locator.isPreAcknowledge, DEFAULT_ACK_BATCH_SIZE)
        session.start()
        // Create a general purpose producer.
        val producer = session.createProducer()
        val myCert = loadKeyStore(config.sslKeystore, config.keyStorePassword).getX509Certificate(X509Utilities.CORDA_CLIENT_TLS)
        val rpcServer = RPCServer(rpcOps, NODE_USER, NODE_USER, locator, userService, CordaX500Name.build(myCert.subjectX500Principal))
        started = Started(sessionFactory, session, producer, rpcServer)
    }

    fun start2(serverControl: ActiveMQServerControl) = synchronized(this) {
        started!!.rpcServer.start(serverControl)
    }

    fun stop() = synchronized(this) {
        started!!.dispose()
        started = null
    }
}
