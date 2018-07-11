package net.corda.deterministic

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

class KeyStoreProvider(private val storeName: String, private val storePassword: String) : TestRule {
    private lateinit var keyStore: KeyStore

    private fun loadKeyStoreResource(resourceName: String, password: CharArray, type: String = "PKCS12"): KeyStore {
        return KeyStore.getInstance(type).apply {
            // Skip these tests if we cannot load the keystore.
            val keyStream = KeyStoreProvider::class.java.classLoader.getResourceAsStream(resourceName)
                ?: throw AssumptionViolatedException("KeyStore $resourceName not found")
            keyStream.use { input ->
                load(input, password)
            }
        }
    }

    override fun apply(statement: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                keyStore = loadKeyStoreResource(storeName, storePassword.toCharArray())
                statement.evaluate()
            }
        }
    }

    fun getKeyPair(alias: String): KeyPair {
        val privateKey = keyStore.getKey(alias, storePassword.toCharArray()) as PrivateKey
        return KeyPair(keyStore.getCertificate(alias).publicKey, privateKey)
    }

    @Suppress("UNUSED")
    fun trustAnchorsFor(vararg aliases: String): Set<TrustAnchor>
        = aliases.map { alias -> TrustAnchor(keyStore.getCertificate(alias) as X509Certificate, null) }.toSet()
}
