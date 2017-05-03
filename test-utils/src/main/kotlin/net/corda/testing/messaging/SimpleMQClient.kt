package net.corda.testing.messaging

import com.google.common.net.HostAndPort
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.testing.configureTestSSL
import org.apache.activemq.artemis.api.core.client.*
import org.bouncycastle.asn1.x500.X500Name

/**
 * As the name suggests this is a simple client for connecting to MQ brokers.
 */
class SimpleMQClient(val target: HostAndPort,
                     override val config: SSLConfiguration? = configureTestSSL(DEFAULT_MQ_LEGAL_NAME)) : ArtemisMessagingComponent() {
    companion object {
        val DEFAULT_MQ_LEGAL_NAME = "SimpleMQClient"
    }
    lateinit var sessionFactory: ClientSessionFactory
    lateinit var session: ClientSession
    lateinit var producer: ClientProducer

    fun start(username: String? = null, password: String? = null, enableSSL: Boolean = true) {
        val tcpTransport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), target, config, enableSSL = enableSSL)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            isBlockOnNonDurableSend = true
            threadPoolMaxSize = 1
        }
        sessionFactory = locator.createSessionFactory()
        session = sessionFactory.createSession(username, password, false, true, true, locator.isPreAcknowledge, locator.ackBatchSize)
        session.start()
        producer = session.createProducer()
    }

    fun createMessage(): ClientMessage = session.createMessage(false)

    fun stop() {
        sessionFactory.close()
    }
}