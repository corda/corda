package net.corda.node.services.messaging

import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE

class ArtemisMessagingClient(private val config: SSLConfiguration, private val serverAddress: NetworkHostAndPort) {
    companion object {
        private val log = loggerFor<ArtemisMessagingClient>()
    }

    class Started(val sessionFactory: ClientSessionFactory, val session: ClientSession, val producer: ClientProducer)

    var started: Started? = null
        private set

    fun start(): Started = synchronized(this) {
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
        return Started(sessionFactory, session, producer).also { started = it }
    }

    fun stop() = synchronized(this) {
        started!!.run {
            producer.close()
            // Ensure any trailing messages are committed to the journal
            session.commit()
            // Closing the factory closes all the sessions it produced as well.
            sessionFactory.close()
        }
        started = null
    }
}
