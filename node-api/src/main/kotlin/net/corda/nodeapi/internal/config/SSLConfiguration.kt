package net.corda.nodeapi.internal.config

import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.nio.file.Path

// TODO sollecitom - refactor this to have no defaults
// TODO sollecitom introduce CertificateStore type and use it
interface SSLConfiguration {

    val keyStorePassword: String
    val trustStorePassword: String
    // TODO sollecitom get rid of this in the interface, the implementation can know of defaults, not the API
    val certificatesDirectory: Path
    val sslKeystore: Path get() = certificatesDirectory / "sslkeystore.jks"
    val trustStoreFile: Path get() = certificatesDirectory / "truststore.jks"
    val crlCheckSoftFail: Boolean

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
