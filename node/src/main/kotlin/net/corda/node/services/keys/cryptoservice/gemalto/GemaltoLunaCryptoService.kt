package net.corda.node.services.keys.cryptoservice.gemalto

import com.safenetinc.luna.LunaSlotManager
import com.safenetinc.luna.provider.LunaProvider
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.node.services.keys.cryptoservice.JCACryptoService
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import java.nio.file.Path
import java.security.KeyStore
import java.security.Provider
import javax.security.auth.x500.X500Principal

class GemaltoLunaCryptoService(keyStore: KeyStore, provider: Provider, x500Principal: X500Principal = DUMMY_X500_PRINCIPAL, private val auth: () -> GemaltoLunaConfiguration) : JCACryptoService(keyStore, provider, x500Principal) {

    override fun isLoggedIn(): Boolean {
        return LunaSlotManager.getInstance().isLoggedIn
    }

    override fun logIn() {
        val config = auth()
        keyStore.load(config.keyStore.byteInputStream(), config.password.toCharArray())
    }

    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return DEFAULT_TLS_SIGNATURE_SCHEME
    }

    /*
     * Unfortunately `calling LunaSlotManager.getInstance().isLoggedIn` is not reliable.
     * It is possible that isLoggedIn is true but a subsequent call on the a Luna JCA object like this.keyStore to throw an Exception
     * because the object is not initialized.
     */
    override fun <T> withAuthentication(block: () -> T): T {
        return try {
            super.withAuthentication(block)
        } catch (e: Exception) {
            logIn()
            block()
        }
    }

    companion object {
        val KEYSTORE_TYPE = "Luna"
        fun fromConfigurationFile(legalName: X500Principal, cryptoServiceConf: Path?): CryptoService {
            val config = parseConfigFile(cryptoServiceConf!!)
            val provider = LunaProvider.getInstance()
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, provider)
            return GemaltoLunaCryptoService(keyStore, provider, legalName) { config }
        }

        private fun parseConfigFile(cryptoServiceConf: Path): GemaltoLunaConfiguration {
            try {
                val config = ConfigFactory.parseFile(cryptoServiceConf.toFile()).resolve()
                return config.parseAs(GemaltoLunaConfiguration::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${cryptoServiceConf.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }

        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
    }

    data class GemaltoLunaConfiguration(val keyStore: String, val password: String)
}