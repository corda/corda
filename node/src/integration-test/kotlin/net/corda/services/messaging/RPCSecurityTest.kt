package net.corda.services.messaging

import net.corda.node.services.User
import net.corda.testing.messaging.SimpleMQClient

/**
 * Runs the security tests with the attacker being a valid RPC user of Alice.
 */
class RPCSecurityTest : MQSecurityTest() {

    override val extraRPCUsers = listOf(User("evil", "pass", permissions = emptySet()))

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.loginToRPC(extraRPCUsers[0])
    }
}