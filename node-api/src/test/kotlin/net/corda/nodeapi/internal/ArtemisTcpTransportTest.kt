package net.corda.nodeapi.internal

import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.SslConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Test
import java.nio.file.Paths
import java.time.Duration

class ArtemisTcpTransportTest {
    private val address = NetworkHostAndPort("localhost", 10003)

    private class PartialSslConfiguration(override val keyStore: FileBasedCertificateStoreSupplier?,
                                          override val trustStore: FileBasedCertificateStoreSupplier?) : SslConfiguration {
        override val useOpenSsl = false
        override val handshakeTimeout: Duration? = null
    }

    @Test(timeout = 300_000)
    fun `RPC acceptor with SSL options is TLS only`() {
        val transport = ArtemisTcpTransport.rpcAcceptorTcpTransport(address, BrokerRpcSslOptions(Paths.get("rpcsslkeystore.jks"), "password"), 1024)
        assertThat(transport.params[TransportConstants.SSL_ENABLED_PROP_NAME]).isEqualTo(true)
        // RPC clients are authenticated with a username and password, not a client certificate.
        assertThat(transport.params[TransportConstants.NEED_CLIENT_AUTH_PROP_NAME]).isEqualTo(false)
        assertThat(transport.params[TransportConstants.ENABLED_PROTOCOLS_PROP_NAME]).isEqualTo("TLSv1.2")
    }

    @Test(timeout = 300_000)
    fun `RPC acceptor without SSL options is explicitly plain-text`() {
        val transport = ArtemisTcpTransport.rpcAcceptorTcpTransport(address, null, 1024)
        assertThat(transport.params).doesNotContainKey(TransportConstants.SSL_ENABLED_PROP_NAME)
        assertThat(transport.params).doesNotContainKey(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME)
    }

    @Test(timeout = 300_000)
    fun `RPC connector with SSL options is TLS only`() {
        val transport = ArtemisTcpTransport.rpcConnectorTcpTransport(address, ClientRpcSslOptions(Paths.get("rpcssltruststore.jks"), "password"))
        assertThat(transport.params[TransportConstants.SSL_ENABLED_PROP_NAME]).isEqualTo(true)
        assertThat(transport.params[TransportConstants.ENABLED_PROTOCOLS_PROP_NAME]).isEqualTo("TLSv1.2")
    }

    @Test(timeout = 300_000)
    fun `internal RPC connector fails closed if the key store or trust store is missing`() {
        assertThatIllegalArgumentException().isThrownBy {
            ArtemisTcpTransport.rpcInternalClientTcpTransport(address, PartialSslConfiguration(null, null))
        }.withMessageContaining("key store is required")
    }
}
