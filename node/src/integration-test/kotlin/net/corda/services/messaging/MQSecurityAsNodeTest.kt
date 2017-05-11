package net.corda.services.messaging

import net.corda.nodeapi.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.RPCApi
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
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.configuration.p2pAddress)
    }

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test
    fun `send message to RPC requests address`() {
        assertSendAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `only the node running the broker can login using the special node user`() {
        val attacker = clientTo(alice.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER)
        }
    }

    @Test
    fun `login as the default cluster user`() {
        val attacker = clientTo(alice.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test
    fun `login without a username and password`() {
        val attacker = clientTo(alice.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }

    @Test
    fun `login to a non ssl port as a node user`() {
        val attacker = clientTo(alice.configuration.rpcAddress!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER, enableSSL = false)
        }
    }

    @Test
    fun `login to a non ssl port as a peer user`() {
        val attacker = clientTo(alice.configuration.rpcAddress!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER, enableSSL = false)  // Login as a peer
        }
    }
}
