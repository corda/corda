package net.corda.node.services


import net.corda.core.context.AuthServiceId
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.nodeapi.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class RPCSecurityManagerTest {

    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    private fun configWithRPCUsername(username: String) {
        RPCSecurityManagerImpl.buildInMemory(users = listOf(User(username, "password", setOf())), id = AuthServiceId("TEST"))
    }
}