package net.corda.nodeapi.internal.config

import net.corda.core.utilities.seconds
import java.time.Duration

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?
    val useOpenSsl: Boolean
    val handshakeTimeout: Duration?

    companion object {
        fun mutual(keyStore: FileBasedCertificateStoreSupplier,
                   trustStore: FileBasedCertificateStoreSupplier,
                   handshakeTimeout: Duration? = null): MutualSslConfiguration {
            return MutualSslOptions(keyStore, trustStore, handshakeTimeout)
        }
    }
}

interface MutualSslConfiguration : SslConfiguration {
    override val keyStore: FileBasedCertificateStoreSupplier
    override val trustStore: FileBasedCertificateStoreSupplier
}

private class MutualSslOptions(override val keyStore: FileBasedCertificateStoreSupplier,
                               override val trustStore: FileBasedCertificateStoreSupplier,
                               override val handshakeTimeout: Duration?) : MutualSslConfiguration {
    override val useOpenSsl: Boolean = false
}

@Suppress("MagicNumber")
val DEFAULT_SSL_HANDSHAKE_TIMEOUT: Duration = 60.seconds // Set at least 3 times higher than sun.security.provider.certpath.URICertStore.DEFAULT_CRL_CONNECT_TIMEOUT which is 15 sec
