package net.corda.nodeapi.internal.config

import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import java.nio.file.Path

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?
    val useOpenSsl: Boolean

    companion object {

        fun mutual(keyStore: FileBasedCertificateStoreSupplier, trustStore: FileBasedCertificateStoreSupplier, useOpenSsl: Boolean = false ): MutualSslConfiguration {

            return MutualSslOptions(keyStore, trustStore, useOpenSsl)
        }
    }
}

interface MutualSslConfiguration : SslConfiguration {
    override val keyStore: FileBasedCertificateStoreSupplier
    override val trustStore: FileBasedCertificateStoreSupplier
}

private class MutualSslOptions(override val keyStore: FileBasedCertificateStoreSupplier, override val trustStore: FileBasedCertificateStoreSupplier, override val useOpenSsl: Boolean ) : MutualSslConfiguration

interface CryptoServiceConfig {
    val name: SupportedCryptoServices
    val conf: Path?
}

const val DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS = 60000L // Set at least 3 times higher than sun.security.provider.certpath.URICertStore.DEFAULT_CRL_CONNECT_TIMEOUT which is 15 sec

const val ARTEMIS_SIGNING_SERVICE_NAME = "ArtemisSigningService" // The artemis signing service security provider name