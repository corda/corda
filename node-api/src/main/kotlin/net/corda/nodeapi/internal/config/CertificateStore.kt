package net.corda.nodeapi.internal.config

import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.internal.outputStream
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.addOrReplaceCertificate
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.OpenOption
import java.nio.file.Path
import java.security.PrivateKey
import java.security.cert.X509Certificate

interface CertificateStore : Iterable<Pair<String, X509Certificate>> {

    companion object {

        fun of(store: X509KeyStore, password: String, entryPassword: String): CertificateStore = DelegatingCertificateStore(store, password, entryPassword)

        fun fromFile(storePath: Path, password: String, entryPassword: String, createNew: Boolean): CertificateStore = DelegatingCertificateStore(X509KeyStore.fromFile(storePath, password, createNew), password, entryPassword)

        fun fromInputStream(stream: InputStream, password: String, entryPassword: String): CertificateStore = DelegatingCertificateStore(X509KeyStore.fromInputStream(stream, password), password, entryPassword)

        fun fromResource(storeResourceName: String, password: String, entryPassword: String, classLoader: ClassLoader = Thread.currentThread().contextClassLoader): CertificateStore = fromInputStream(classLoader.getResourceAsStream(storeResourceName), password, entryPassword)
    }

    val value: X509KeyStore
    val password: String
    val entryPassword: String

    fun writeTo(stream: OutputStream) = value.internal.store(stream, password.toCharArray())

    fun writeTo(path: Path, vararg options: OpenOption) = path.outputStream(*options)

    fun update(action: X509KeyStore.() -> Unit) {
        val result = action.invoke(value)
        value.save()
        return result
    }

    fun <RESULT> query(action: X509KeyStore.() -> RESULT): RESULT {
        return action.invoke(value)
    }

    operator fun set(alias: String, certificate: X509Certificate) {
        update {
            internal.addOrReplaceCertificate(alias, certificate)
        }
    }

    override fun iterator(): Iterator<Pair<String, X509Certificate>> {

        return query {
            aliases()
        }.asSequence().map { alias -> alias to get(alias) }.iterator()
    }

    fun forEach(action: (alias: String, certificate: X509Certificate) -> Unit) {

        forEach { (alias, certificate) -> action.invoke(alias, certificate) }
    }

    /**
     * @throws IllegalArgumentException if no certificate for the alias is found, or if the certificate is not an [X509Certificate].
     */
    operator fun get(alias: String): X509Certificate {
        return query {
            getCertificate(alias)
        }
    }

    operator fun contains(alias: String): Boolean = value.contains(alias)

    fun copyTo(certificateStore: CertificateStore) {

        certificateStore.update {
            this@CertificateStore.forEach(::setCertificate)
        }
    }

    fun setCertPathOnly(alias: String, certificates: List<X509Certificate>) {
        // In case CryptoService and CertificateStore share the same KeyStore (i.e., when BCCryptoService is used),
        // extract the existing key from the Keystore and store it again along with the new certificate chain.
        // This is because KeyStores do not support updateKeyEntry and thus we cannot update the certificate chain
        // without overriding the key entry.
        // Note that if the given alias already exists, the keystore information associated with it
        // is overridden by the given key (and associated certificate chain).
        val privateKey: PrivateKey = if (this.contains(alias)) {
            this.value.getPrivateKey(alias, entryPassword)
        } else {
            AliasPrivateKey(alias)
        }
        this.value.setPrivateKey(alias, privateKey, certificates, entryPassword)
    }
}

private class DelegatingCertificateStore(override val value: X509KeyStore, override val password: String, override val entryPassword: String) : CertificateStore