package net.corda.services.messaging

import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.RPC_REQUESTS_QUEUE
import net.corda.testing.messaging.SimpleMQClient
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

/**
 * Runs the security tests with the attacker pretending to be a node on the network.
 */
class MQSecurityAsNodeTest : MQSecurityTest() {

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test
    fun `send message to RPC requests address`() {
        assertSendAttackFails(RPC_REQUESTS_QUEUE)
    }

    @Test
    fun `only the node running the broker can login using the special node user`() {
        val attacker = clientTo(alice.configuration.artemisAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER)
        }
    }

    @Test
    fun `login as the default cluster user`() {
        val attacker = clientTo(alice.configuration.artemisAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test
    fun `login without a username and password`() {
        val attacker = clientTo(alice.configuration.artemisAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }
}