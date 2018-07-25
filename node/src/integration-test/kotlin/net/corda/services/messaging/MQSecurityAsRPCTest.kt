package net.corda.services.messaging

import net.corda.testing.node.User
import org.junit.Test

/**
 * Runs the security tests with the attacker being a valid RPC user of Alice.
 */
class MQSecurityAsRPCTest : RPCMQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.node.configuration.rpcOptions.address)
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
}
