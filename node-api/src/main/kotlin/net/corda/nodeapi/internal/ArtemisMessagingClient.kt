/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal

import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_P2P_USER
import net.corda.nodeapi.internal.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE

interface ArtemisSessionProvider {
    fun start(): ArtemisMessagingClient.Started
    fun stop()
    val started: ArtemisMessagingClient.Started?
}

class ArtemisMessagingClient(
        private val config: SSLConfiguration,
        private val serverAddress: NetworkHostAndPort,
        private val maxMessageSize: Int,
        private val autoCommitSends: Boolean = true,
        private val autoCommitAcks: Boolean = true,
        private val confirmationWindowSize: Int = -1
)  : ArtemisSessionProvider {
    companion object {
        private val log = loggerFor<ArtemisMessagingClient>()
    }

    class Started(val serverLocator: ServerLocator, val sessionFactory: ClientSessionFactory, val session: ClientSession, val producer: ClientProducer)

    override var started: Started? = null
        private set

    override fun start(): Started = synchronized(this) {
        check(started == null) { "start can't be called twice" }
        log.info("Connecting to message broker: $serverAddress")
        // TODO Add broker CN to config for host verification in case the embedded broker isn't used
        val tcpTransport = ArtemisTcpTransport.p2pConnectorTcpTransport(serverAddress, config)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            // Never time out on our loopback Artemis connections. If we switch back to using the InVM transport this
            // would be the default and the two lines below can be deleted.
            connectionTTL = 60000
            clientFailureCheckPeriod = -1
            minLargeMessageSize = maxMessageSize
            isUseGlobalPools = nodeSerializationEnv != null
            confirmationWindowSize = this@ArtemisMessagingClient.confirmationWindowSize
            addIncomingInterceptor(ArtemisMessageSizeChecksInterceptor(maxMessageSize))
        }
        val sessionFactory = locator.createSessionFactory()
        // Login using the node username. The broker will authenticate us as its node (as opposed to another peer)
        // using our TLS certificate.
        // Note that the acknowledgement of messages is not flushed to the Artermis journal until the default buffer
        // size of 1MB is acknowledged.
        val session = sessionFactory!!.createSession(NODE_P2P_USER, NODE_P2P_USER, false, autoCommitSends, autoCommitAcks, false, DEFAULT_ACK_BATCH_SIZE)
        session.start()
        // Create a general purpose producer.
        val producer = session.createProducer()
        return Started(locator, sessionFactory, session, producer).also { started = it }
    }

    override fun stop() = synchronized(this) {
        started?.run {
            producer.close()
            // Ensure any trailing messages are committed to the journal
            session.commit()
            // Closing the factory closes all the sessions it produced as well.
            sessionFactory.close()
            serverLocator.close()
        }
        started = null
    }
}
