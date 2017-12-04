package net.corda.node.services


import net.corda.nodeapi.internal.config.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class RPCUserServiceTest {

    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    private fun configWithRPCUsername(username: String) {
        RPCUserServiceImpl(listOf(User(username, "password", setOf())))
    }
}