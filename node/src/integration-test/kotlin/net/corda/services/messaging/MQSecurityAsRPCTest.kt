package net.corda.services.messaging

import net.corda.node.services.User
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
        attacker.loginToRPC(extraRPCUsers[0])
    }

    @Test
    fun `login to a ssl port as a RPC user`() {
        val attacker = clientTo(alice.configuration.artemisAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.loginToRPC(extraRPCUsers[0], enableSSL = true)
        }
    }
}
