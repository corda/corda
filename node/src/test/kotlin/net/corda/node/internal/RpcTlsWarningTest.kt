package net.corda.node.internal

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.nodeapi.BrokerRpcSslOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class RpcTlsWarningTest {
    private fun rpcOptions(useSsl: Boolean, standAloneBroker: Boolean = false, hasSslBlock: Boolean = useSsl) = object : NodeRpcOptions {
        override val address = NetworkHostAndPort("localhost", 10003)
        override val adminAddress = NetworkHostAndPort("localhost", 10004)
        override val standAloneBroker = standAloneBroker
        override val useSsl = useSsl
        override val sslConfig = if (hasSslBlock) BrokerRpcSslOptions(Paths.get("rpcsslkeystore.jks"), "password") else null
    }

    @Test(timeout = 300_000)
    fun `warns when RPC TLS is disabled`() {
        val warning = rpcTlsDisabledWarning(rpcOptions(useSsl = false))
        assertThat(warning).isNotNull()
        assertThat(warning).contains("without TLS", "generate-rpc-ssl-settings", "useSsl = true", "ClientRpcSslOptions")
        assertThat(warning).doesNotContain("being ignored")
    }

    @Test(timeout = 300_000)
    fun `warns that the ssl block is ignored when it is present but useSsl is false`() {
        val warning = rpcTlsDisabledWarning(rpcOptions(useSsl = false, hasSslBlock = true))
        assertThat(warning).contains("without TLS", "'rpcSettings.ssl' block is present but is being ignored")
    }

    @Test(timeout = 300_000)
    fun `does not warn when RPC TLS is enabled`() {
        assertThat(rpcTlsDisabledWarning(rpcOptions(useSsl = true))).isNull()
    }

    @Test(timeout = 300_000)
    fun `does not warn when the node uses a stand alone RPC broker`() {
        assertThat(rpcTlsDisabledWarning(rpcOptions(useSsl = false, standAloneBroker = true))).isNull()
    }
}
