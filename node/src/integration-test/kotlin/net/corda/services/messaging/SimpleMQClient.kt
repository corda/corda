package net.corda.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.p2pConnectorTcpTransport
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.testing.internal.configureTestSSL
import org.apache.activemq.artemis.api.core.client.*

/**
 * As the name suggests this is a simple client for connecting to MQ brokers.
 */
class SimpleMQClient(val target: NetworkHostAndPort,
                     private val config: MutualSslConfiguration? = configureTestSSL(DEFAULT_MQ_LEGAL_NAME)) {
    companion object {
        val DEFAULT_MQ_LEGAL_NAME = CordaX500Name(organisation = "SimpleMQClient", locality = "London", country = "GB")
    }

    lateinit var sessionFactory: ClientSessionFactory
    lateinit var session: ClientSession
    lateinit var producer: ClientProducer

    fun start(username: String? = null, password: String? = null, enableSSL: Boolean = true) {
        val tcpTransport = p2pConnectorTcpTransport(target, config, enableSSL = enableSSL)
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
