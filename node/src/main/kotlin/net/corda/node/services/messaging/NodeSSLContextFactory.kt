package net.corda.node.services.messaging

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.TRUST_MANAGER_FACTORY_NAME
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.netty.createAndInitSslContext
import net.corda.nodeapi.internal.protonwrapper.netty.keyManagerFactory
import org.apache.activemq.artemis.core.remoting.impl.ssl.DefaultOpenSSLContextFactory
import org.apache.activemq.artemis.core.remoting.impl.ssl.DefaultSSLContextFactory
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig
import java.nio.file.Paths
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class NodeSSLContextFactory : DefaultSSLContextFactory() {
    override fun getSSLContext(config: SSLContextConfig, additionalOpts: Map<String, Any>): SSLContext {
        val trustManagerFactory = additionalOpts[TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?
        return if (trustManagerFactory != null) {
            createAndInitSslContext(loadKeyManagerFactory(config), trustManagerFactory)
        } else {
            super.getSSLContext(config, additionalOpts)
        }
    }

    override fun getPriority(): Int {
        // We make sure this factory is the one that's chosen, so any sufficiently large value will do.
        return 15
    }
}


class NodeOpenSSLContextFactory : DefaultOpenSSLContextFactory() {
    override fun getServerSslContext(config: SSLContextConfig, additionalOpts: Map<String, Any>): SslContext {
        val trustManagerFactory = additionalOpts[TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?
        return if (trustManagerFactory != null) {
            SslContextBuilder
                    .forServer(loadKeyManagerFactory(config))
                    .sslProvider(SslProvider.OPENSSL)
                    .trustManager(trustManagerFactory)
                    .build()
        } else {
            super.getServerSslContext(config, additionalOpts)
        }
    }

    override fun getPriority(): Int {
        // We make sure this factory is the one that's chosen, so any sufficiently large value will do.
        return 15
    }
}


private fun loadKeyManagerFactory(config: SSLContextConfig): KeyManagerFactory {
    val keyStore = CertificateStore.fromFile(Paths.get(config.keystorePath), config.keystorePassword, config.keystorePassword, false)
    return keyManagerFactory(keyStore)
}
