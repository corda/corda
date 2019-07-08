package net.corda.nodeapi.internal.cryptoservice.futurex

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import fx.security.pkcs11.P11KeyParams
import fx.security.pkcs11.SunPKCS11
import fx.security.pkcs11.wrapper.PKCS11Constants.CKA_SIGN
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.JCACryptoService
import java.nio.file.Path
import java.security.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.security.auth.callback.PasswordCallback
import javax.security.auth.x500.X500Principal

class FutureXCryptoService(
        keyStore: KeyStore,
        provider: SunPKCS11,
        x500Principal: X500Principal = DUMMY_X500_PRINCIPAL,
        timeout:Duration? = null,
        private val auth: () -> FutureXConfiguration
) : JCACryptoService(keyStore, provider, x500Principal, timeout) {

    private val cachedKeyHandles: ConcurrentMap<String, PrivateKey> = ConcurrentHashMap()

    init {
        logIn()
    }

    /**
     * The FutureX provider does not have any API that can be used to determine whether login has been performed and is still valid.
     * As a result, this method returns always false, which means this component will attempt to login on every operation.
     * However, note that the underlying provider checks whether the associated user is already logged in and avoids repeating the login in this case.
     * As a result, this should not have any performance impact.
     */
    override fun isLoggedIn(): Boolean {
        return false
    }

    override fun logIn() {
        val config = auth()
        (provider as SunPKCS11).login(null) { callbacks -> (callbacks[0] as PasswordCallback).password = config.credentials.toCharArray() }
        keyStore.load(null, null)
    }

    override fun _generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        if (cachedKeyHandles.containsKey(alias)) {
            cachedKeyHandles.remove(alias)
        }
        return super.generateKeyPair(alias, scheme)
    }

    override fun _sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        return withAuthentication {
            val privateKeyHandle = if (cachedKeyHandles.containsKey(alias)) {
                cachedKeyHandles[alias]!!
            } else {
                val key = keyStore.getKey(alias, null) as PrivateKey? ?: throw CryptoServiceException("No key found for alias $alias")
                cachedKeyHandles[alias] = key
                key
            }
            privateKeyHandle.let {
                val algorithm = signAlgorithm?: if (it.algorithm == "RSA") {
                    "SHA256withRSA"
                } else {
                    "SHA256withECDSA"
                }
                val signature = Signature.getInstance(algorithm, provider)
                signature.initSign(it)
                signature.update(data)
                signature.sign()
            }
        }
    }
    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return DEFAULT_TLS_SIGNATURE_SCHEME
    }

    override fun keyPairGeneratorFromScheme(scheme: SignatureScheme): KeyPairGenerator {
        val algorithm = when (scheme) {
            Crypto.ECDSA_SECP256R1_SHA256 -> "EC"
            Crypto.RSA_SHA256 -> "RSA"
            else -> throw IllegalArgumentException("No algorithm for scheme ID ${scheme.schemeNumberID}")
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm, provider)
        // The Futurex provider uses P11KeyParams for both EC and RSA.
        // There is currently no way of specifying which curve is to be used.
        // The curve used by default is prime256v1, which is synonymous with  secp256r1.
        // Other curves like secp256k1 are currently not supported.
        val params = P11KeyParams().apply {
            token = true
            keySize = scheme.keySize!!.toLong()
            setKeyUsage(CKA_SIGN)
        }
        keyPairGenerator.initialize(params)
        return keyPairGenerator
    }

    companion object {
        val KEYSTORE_TYPE = "PKCS11"

        private fun parseConfigFile(cryptoServiceConf: Path): FutureXConfiguration {
            try {
                val config = ConfigFactory.parseFile(cryptoServiceConf.toFile()).resolve()
                return config.parseAs(FutureXConfiguration::class)
            } catch (e: Exception) {
                when (e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${cryptoServiceConf.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }

        fun fromConfigurationFile(legalName: X500Principal, cryptoServiceConf: Path?, timeout: Duration? = null): CryptoService {
            val config = parseConfigFile(cryptoServiceConf!!)
            val provider = SunPKCS11()
            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, provider)
            return FutureXCryptoService(keyStore, provider, legalName, timeout) { config }
        }

        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
    }

    data class FutureXConfiguration(val credentials: String)
}


