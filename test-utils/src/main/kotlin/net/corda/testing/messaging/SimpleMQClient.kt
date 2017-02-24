package net.corda.testing.messaging

import com.google.common.net.HostAndPort
import net.corda.node.services.config.SSLConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.ArtemisMessagingComponent.ConnectionDirection.Outbound
import net.corda.testing.configureTestSSL
import org.apache.activemq.artemis.api.core.client.*

/**
 * As the name suggests this is a simple client for connecting to MQ brokers.
 */
class SimpleMQClient(val target: HostAndPort,
                     override val config: SSLConfiguration? = configureTestSSL("SimpleMQClient")) : ArtemisMessagingComponent() {
    lateinit var sessionFactory: ClientSessionFactory
    lateinit var session: ClientSession
    lateinit var producer: ClientProducer

    fun start(username: String? = null, password: String? = null, enableSSL: Boolean = true) {
        val tcpTransport = tcpTransport(Outbound(), target.hostText, target.port, enableSSL)
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