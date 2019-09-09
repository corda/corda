package net.corda.nodeapi.internal.cryptoservice.securosys

import com.securosys.primus.jce.*
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.*
import java.nio.file.Path
import java.security.Key
import java.security.KeyStore
import java.security.Provider
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPrivateKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.security.auth.x500.X500Principal

class PrimusXCryptoService(keyStore: KeyStore, provider: Provider, x500Principal: X500Principal = DUMMY_X500_PRINCIPAL, private val auth: () -> PrimusXConfiguration): JCACryptoService(keyStore, provider) {

    override fun getType(): SupportedCryptoServices = SupportedCryptoServices.PRIMUS_X

    private val keyHandleCache: ConcurrentHashMap<String, Key> = ConcurrentHashMap()

    override fun isLoggedIn(): Boolean {
        // ENT-4130: Added the username as the HSM always returned false otherwise
        return PrimusLogin.isLoggedIn(auth().host, auth().port, auth().username)
    }

    @Synchronized
    override fun logIn() {
        PrimusConfiguration.setHsmHostAndPortAndUser(auth().host, auth().port, auth().username)
        PrimusLogin.login(auth().username, auth().password.toCharArray())
        keyStore.load(null, null)

        // invalidate the cache, since there is no guarantee that key handles are stable across sessions.
        keyHandleCache.clear()
    }

    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return DEFAULT_TLS_SIGNATURE_SCHEME
    }

    @Synchronized
    override fun createWrappingKey(alias: String, failIfExists: Boolean) {
        return withAuthentication {
            if (keyStore.containsAlias(alias)) {
                when (failIfExists) {
                    true -> throw IllegalArgumentException("There is an existing key with the alias: $alias")
                    false -> return@withAuthentication
                }
            }

            val keyGenerator = KeyGenerator.getInstance("AES", provider)
            keyGenerator.init(wrappingKeySize())
            val wrappingKey = keyGenerator.generateKey()
            keyStore.setKeyEntry(alias, wrappingKey, null, null)
        }
    }

    override fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme): Pair<PublicKey, WrappedPrivateKey> {
        return withAuthentication {
            val ephemeralKeyAlias = UUID.randomUUID().toString()
            val wrappingKey = getKey(masterKeyAlias) as? SecretKey ?: throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")


            val keyPairGenerator = keyPairGeneratorFromScheme(childKeyScheme)
            val keyPair = withAttributes(true, true) {
                PrimusName.generateKeyPair(keyPairGenerator, ephemeralKeyAlias)
            }

            val privateKeyMaterialWrapped = when (childKeyScheme) {
                Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256 -> PrimusWrap.aesWrapPadEc(wrappingKey, keyPair.private as ECPrivateKey)
                Crypto.RSA_SHA256 -> PrimusWrap.aesWrapPadRsa(wrappingKey, keyPair.private as RSAPrivateKey)
                else -> throw IllegalArgumentException("The scheme ID ${childKeyScheme.schemeNumberID} is not supported.")
            }
            // ephemeral keys are garbage collected eventually, but we explicitly delete them because we have noticed this can lag and lead to resource exhaustion.
            keyStore.deleteEntry(ephemeralKeyAlias)

            Pair(keyPair.public, WrappedPrivateKey(privateKeyMaterialWrapped, childKeyScheme))
        }
    }

    override fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray {
        return withAuthentication {
            val wrappingKey = getKey(masterKeyAlias) as? SecretKey ?: throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")

            when(wrappedPrivateKey.signatureScheme) {
                Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256 -> PrimusWrap.aesUnwrapPadEcSign(wrappedPrivateKey.signatureScheme.signatureName, payloadToSign, wrappingKey, wrappedPrivateKey.keyMaterial)
                // reason for passing custom string: PrimusX APIs are case-sensitive, so it won't work with the signature name "SHA256WITHRSA" provided by Crypto.
                Crypto.RSA_SHA256 -> PrimusWrap.aesUnwrapPadRsaSign("SHA256withRSA", payloadToSign, wrappingKey, wrappedPrivateKey.keyMaterial)
                else -> throw IllegalArgumentException("The scheme ID ${wrappedPrivateKey.signatureScheme.schemeNumberID} is not supported.")
            }
        }
    }

    override fun getWrappingMode(): WrappingMode? = WrappingMode.WRAPPED

    /**
     * By default, keys should be stored with the following values for the associated PKCS#11 attributes:
     * - extractable set to false, so that the key cannot be extracted from the HSM, even if it's encrypted.
     * - sensitive to true, so that even if the key can be extracted from the HSM, it can't be extracted in plain text.
     *
     * This method allows to override these attributes for a specific key in a safe way, so that defaults are restored.
     */
    private fun <T> withAttributes(extractable: Boolean, sensitive: Boolean, function: () -> T): T {
        PrimusKeyAttributes.setKeyAccessFlag(PrimusKeyAttributes.ACCESS_EXTRACTABLE, extractable)
        PrimusKeyAttributes.setKeyAccessFlag(PrimusKeyAttributes.ACCESS_SENSITIVE, sensitive)

        val result = function()

        PrimusKeyAttributes.setKeyAccessFlag(PrimusKeyAttributes.ACCESS_EXTRACTABLE, false)
        PrimusKeyAttributes.setKeyAccessFlag(PrimusKeyAttributes.ACCESS_SENSITIVE, true)

        return result
    }

    /**
     * Returns the key stored under the provided alias or null if, if there is no key stored under this alias.
     * A local cache is used as a performance optimisation to reduce network round trips to the HSM.
     */
    private fun getKey(alias: String): Key? {
        val key = keyHandleCache[alias] ?: keyStore.getKey(alias, null)

        if (key != null) {
            keyHandleCache[alias] = key
        }

        return key
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
                checkConfigurationFileExists(cryptoServiceConf)
                val config = ConfigFactory.parseFile(cryptoServiceConf.toFile()).resolve()
                return config.parseAs(PrimusXConfiguration::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${cryptoServiceConf.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }

        data class PrimusXConfiguration(val host: String, val port: Int, val username: String, val password: String)
    }
}