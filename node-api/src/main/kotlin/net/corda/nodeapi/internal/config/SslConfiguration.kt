package net.corda.nodeapi.internal.config

import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.nio.file.Path

interface SslConfiguration {

    val keyStore: FileBasedCertificateStoreSupplier?
    val trustStore: FileBasedCertificateStoreSupplier?
}

// TODO sollecitom move this?
interface TwoWaySslConfiguration : SslConfiguration {

    override val keyStore: FileBasedCertificateStoreSupplier
    override val trustStore: FileBasedCertificateStoreSupplier
}

// TODO sollecitom move this? maybe make it private with a factory method in SSLConfiguration?
class TwoWaySslOptions(override val keyStore: FileBasedCertificateStoreSupplier, override val trustStore: FileBasedCertificateStoreSupplier) : TwoWaySslConfiguration

// Don't use this internally. It's still here because it's used by ArtemisTcpTransport, which is in public node-api by mistake.
interface SSLConfiguration {
    val keyStorePassword: String
    val trustStorePassword: String
    val certificatesDirectory: Path
    val sslKeystore: Path get() = certificatesDirectory / "sslkeystore.jks"
    // TODO This looks like it should be in NodeSSLConfiguration
    val nodeKeystore: Path get() = certificatesDirectory / "nodekeystore.jks"
    val trustStoreFile: Path get() = certificatesDirectory / "truststore.jks"
    val crlCheckSoftFail: Boolean

    fun loadTrustStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(trustStoreFile, trustStorePassword, createNew)
    }

    fun loadNodeKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(nodeKeystore, keyStorePassword, createNew)
    }

    fun loadSslKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(sslKeystore, keyStorePassword, createNew)
    }
}