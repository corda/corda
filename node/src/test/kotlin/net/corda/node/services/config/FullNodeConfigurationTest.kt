package net.corda.node.services.config

import net.corda.nodeapi.User
import net.corda.testing.testConfiguration
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Paths

class FullNodeConfigurationTest {
    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        fun configWithRPCUsername(username: String): FullNodeConfiguration {
            return testConfiguration(Paths.get("."), "NodeA", 0).copy(
                    rpcUsers = listOf(User(username, "pass", emptySet())))
        }

        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }
}
