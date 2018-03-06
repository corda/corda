/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.testing.internal.configureTestSSL
import org.apache.activemq.artemis.api.core.client.*

/**
 * As the name suggests this is a simple client for connecting to MQ brokers.
 */
class SimpleMQClient(val target: NetworkHostAndPort,
                     private val config: SSLConfiguration? = configureTestSSL(DEFAULT_MQ_LEGAL_NAME)) {
    companion object {
        val DEFAULT_MQ_LEGAL_NAME = CordaX500Name(organisation = "SimpleMQClient", locality = "London", country = "GB")
    }

    lateinit var sessionFactory: ClientSessionFactory
    lateinit var session: ClientSession
    lateinit var producer: ClientProducer

    fun start(username: String? = null, password: String? = null, enableSSL: Boolean = true) {
        val tcpTransport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), target, config, enableSSL = enableSSL)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            isBlockOnNonDurableSend = true
            threadPoolMaxSize = 1
            isUseGlobalPools = nodeSerializationEnv != null
        }
        sessionFactory = locator.createSessionFactory()
        session = sessionFactory.createSession(username, password, false, true, true, locator.isPreAcknowledge, locator.ackBatchSize)
        session.start()
        producer = session.createProducer()
    }

    fun createMessage(): ClientMessage = session.createMessage(false)

    fun stop() {
        try {
            sessionFactory.close()
        } catch (e: Exception) {
            // sessionFactory might not have initialised.
        }
    }
}
