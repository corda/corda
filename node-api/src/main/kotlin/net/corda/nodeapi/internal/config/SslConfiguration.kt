package net.corda.nodeapi.internal.config

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?
    val useOpenSsl: Boolean

    companion object {

        fun mutual(keyStore: FileBasedCertificateStoreSupplier, trustStore: FileBasedCertificateStoreSupplier): MutualSslConfiguration {

            return MutualSslOptions(keyStore, trustStore)
        }
    }
}

interface MutualSslConfiguration : SslConfiguration {
    override val keyStore: FileBasedCertificateStoreSupplier
    override val trustStore: FileBasedCertificateStoreSupplier
}

private class MutualSslOptions(override val keyStore: FileBasedCertificateStoreSupplier,
                               override val trustStore: FileBasedCertificateStoreSupplier) : MutualSslConfiguration {
    override val useOpenSsl: Boolean = false
}

const val DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS = 60000L // Set at least 3 times higher than sun.security.provider.certpath.URICertStore.DEFAULT_CRL_CONNECT_TIMEOUT which is 15 sec

