package net.corda.services.messaging

import net.corda.nodeapi.User
import net.corda.testing.configureTestSSL
import net.corda.testing.messaging.SimpleMQClient
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

/**
 * Runs the security tests with the attacker being a valid RPC user of Alice.
 */
class MQSecurityAsRPCTest : MQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.configuration.rpcAddress!!)
    }

    @Test
    fun `send message on logged in user's RPC address`() {
        val user1Queue = loginToRPCAndGetClientQueue()
        assertSendAttackFails(user1Queue)
    }

    override val extraRPCUsers = listOf(User("evil", "pass", permissions = emptySet()))

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(extraRPCUsers[0].username, extraRPCUsers[0].password, false)
    }

    @Test
    fun `login to a ssl port as a RPC user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            loginToRPC(alice.configuration.p2pAddress, extraRPCUsers[0], configureTestSSL())
        }
    }
}
