package net.corda.nodeapi.internal.config

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?

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

private class MutualSslOptions(override val keyStore: FileBasedCertificateStoreSupplier, override val trustStore: FileBasedCertificateStoreSupplier) : MutualSslConfiguration