package net.corda.node.services.config

import net.corda.core.utilities.ALICE
import net.corda.nodeapi.User
import net.corda.testing.testConfiguration
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.nio.file.Paths

class FullNodeConfigurationTest {
    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        fun configWithRPCUsername(username: String): FullNodeConfiguration {
            return testConfiguration(Paths.get("."), X500Name(ALICE.name), 0).copy(
                    rpcUsers = listOf(User(username, "pass", emptySet())))
        }

        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }
}
