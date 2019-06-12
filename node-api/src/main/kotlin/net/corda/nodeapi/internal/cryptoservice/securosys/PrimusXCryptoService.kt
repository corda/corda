package net.corda.nodeapi.internal.cryptoservice.securosys

import com.securosys.primus.jce.PrimusConfiguration
import com.securosys.primus.jce.PrimusLogin
import com.securosys.primus.jce.PrimusProvider
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.JCACryptoService
import java.nio.file.Path
import java.security.KeyStore
import java.security.Provider
import javax.security.auth.x500.X500Principal


class PrimusXCryptoService(keyStore: KeyStore, provider: Provider, x500Principal: X500Principal = DUMMY_X500_PRINCIPAL, private val auth: () -> PrimusXConfiguration): JCACryptoService(keyStore, provider) {

    override fun isLoggedIn(): Boolean {
        return PrimusLogin.isLoggedIn(auth().host, auth().port)
    }

    override fun logIn() {
        PrimusConfiguration.setHsmHostAndPortAndUser(auth().host, auth().port, auth().username)
        PrimusLogin.login(auth().username, auth().password.toCharArray())
        keyStore.load(null, null)
    }

    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return DEFAULT_TLS_SIGNATURE_SCHEME
    }

    companion object {

        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256

        fun fromConfigurationFile(legalName: X500Principal, cryptoServiceConf: Path?): CryptoService {
            val config = parseConfigFile(cryptoServiceConf!!)
            val provider = PrimusProvider()
            val keyStore = KeyStore.getInstance(PrimusProvider.getKeyStoreTypeName(), provider)
            return PrimusXCryptoService(keyStore, provider, legalName) { config }
        }

        fun parseConfigFile(cryptoServiceConf: Path): PrimusXConfiguration {
            try {
                val config = ConfigFactory.parseFile(cryptoServiceConf.toFile()).resolve()
                return config.parseAs(PrimusXConfiguration::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${cryptoServiceConf.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }
    }
    data class PrimusXConfiguration(val host: String, val port: Int, val username: String, val password: String)
}