package net.corda.attestation

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

@Suppress("UNUSED")
class KeyStoreProvider(private val storeName: String, private val storePassword: String) : TestRule {
    private lateinit var keyStore: KeyStore

    private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
        return KeyStore.getInstance(type).apply {
            KeyStoreProvider::class.java.classLoader.getResourceAsStream(resourceName)?.use { input ->
                load(input, password)
            }
        }
    }

    override fun apply(statement: Statement, description: Description?): Statement {
        keyStore = loadKeyStoreResource(storeName, storePassword.toCharArray())
        return statement
    }

    fun getKeyPair(alias: String, password: String): KeyPair {
        val privateKey = keyStore.getKey(alias, password.toCharArray()) as PrivateKey
        return KeyPair(keyStore.getCertificate(alias).publicKey, privateKey)
    }

    fun trustAnchorsFor(vararg aliases: String): Set<TrustAnchor>
            = aliases.map { alias -> TrustAnchor(keyStore.getCertificate(alias) as X509Certificate, null) }.toSet()
}
