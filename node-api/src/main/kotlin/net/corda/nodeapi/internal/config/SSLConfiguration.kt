package net.corda.nodeapi.internal.config

import net.corda.core.internal.div
import net.corda.nodeapi.config.CertificateStoreSupplier
import net.corda.nodeapi.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.nio.file.Path

// TODO sollecitom use this
interface SslConfiguration {

    val keyStore: CertificateStoreSupplier
    val trustStore: CertificateStoreSupplier
}

class FileBasedCertificateStoreLoader(private val path: Path, private val password: String) : CertificateStoreSupplier {

    // TODO sollecitom check
//    private var cached: CertificateStore? = null
//
//    override fun get(createNew: Boolean): CertificateStore {
//
//        synchronized(this) {
//            if (cached == null) {
//                cached = DelegatingCertificateStore(X509KeyStore.fromFile(path, password, createNew), password)
//            }
//            return cached!!
//        }
//    }

    override fun get(createNew: Boolean): CertificateStore = DelegatingCertificateStore(X509KeyStore.fromFile(path, password, createNew), password)
}

private class DelegatingCertificateStore(override val value: X509KeyStore, override val password: String): CertificateStore

// TODO sollecitom - refactor this to have no defaults
// TODO sollecitom introduce CertificateStore type and use it
interface SSLConfiguration {

    val keyStorePassword: String
    val trustStorePassword: String
    // TODO sollecitom get rid of this in the interface, the implementation can know of defaults, not the API
    val certificatesDirectory: Path
    // TODO sollecitom see if you can replace the Path with X509KeyStore
    val sslKeystore: Path get() = certificatesDirectory / "sslkeystore.jks"
    val trustStoreFile: Path get() = certificatesDirectory / "truststore.jks"

    fun loadTrustStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(trustStoreFile, trustStorePassword, createNew)
    }

    fun loadSslKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(sslKeystore, keyStorePassword, createNew)
    }
}
