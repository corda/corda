package net.corda.nodeapi.internal.provider

import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.security.KeyStore
import java.security.cert.X509Certificate

interface DelegatedSigningService {

    fun sign(alias: String, signatureAlgorithm: String, data: ByteArray): ByteArray?

    fun certificates(): Map<String, List<X509Certificate>>

    fun aliases(): Set<String> = certificates().keys

    fun certificates(alias: String): List<X509Certificate>? = certificates()[alias]

    fun certificate(alias: String): X509Certificate? = certificates(alias)?.first()

    fun keyStore(): CertificateStore {
        val provider = DelegatedKeystoreProvider(this)
        val keyStore = KeyStore.getInstance("Delegated", provider).also { it.load(null) }
        return CertificateStore.of(X509KeyStore(keyStore, "dummy"), "dummy", "dummy")
    }

    fun truststore(): CertificateStore
}