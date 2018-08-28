package net.corda.nodeapi.internal.config

import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.io.OutputStream
import java.nio.file.Path

interface SslConfiguration {

    val keyStore: CertificateStore
    val trustStore: CertificateStore
}

// TODO sollecitom see if you can make the password private here
interface CertificateStore {

    // TODO sollecitom ideally make this private
    val value: X509KeyStore
    // TODO sollecitom see if this can stay private (by adding delegate functions over X509Store)
    val password: String

    fun writeTo(stream: OutputStream) = value.internal.store(stream, password.toCharArray())
}

interface CertificateStoreLoader {

    fun load(createNew: Boolean = false): CertificateStore
}

class FileBasedCertificateStoreLoader(private val path: Path, private val password: String): CertificateStoreLoader {

    override fun load(createNew: Boolean): CertificateStore = DelegatingCertificateStore(X509KeyStore.fromFile(path, password, createNew), password)
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

// TODO sollecitom - refactor this to have no defaults
interface NodeSSLConfiguration : SSLConfiguration {

    // TODO sollecitom get rid of this (nodeInfos directory should be explicit)
    // TODO sollecitom this should at best be part of the config impl, not the API
    // TODO sollecitom move this to NodeConfiguration initially
    val baseDirectory: Path

    override val certificatesDirectory: Path get() = baseDirectory / "certificates"

    val nodeKeystore: Path get() = certificatesDirectory / "nodekeystore.jks"

    fun loadNodeKeyStore(createNew: Boolean = false): X509KeyStore {
        return X509KeyStore.fromFile(nodeKeystore, keyStorePassword, createNew)
    }
}
