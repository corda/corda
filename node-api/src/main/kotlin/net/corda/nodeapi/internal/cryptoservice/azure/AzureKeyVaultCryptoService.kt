package net.corda.nodeapi.internal.cryptoservice.azure

import com.microsoft.aad.adal4j.AsymmetricKeyCredential
import com.microsoft.aad.adal4j.AuthenticationContext
import com.microsoft.azure.keyvault.KeyVaultClient
import com.microsoft.azure.keyvault.authentication.KeyVaultCredentials
import com.microsoft.azure.keyvault.models.JsonWebKeyCurveName
import com.microsoft.azure.keyvault.models.KeyBundle
import com.microsoft.azure.keyvault.requests.CreateKeyRequest
import com.microsoft.azure.keyvault.webkey.JsonWebKeyCurveName.P_256
import com.microsoft.azure.keyvault.webkey.JsonWebKeyCurveName.P_256K
import com.microsoft.azure.keyvault.webkey.JsonWebKeyOperation
import com.microsoft.azure.keyvault.webkey.JsonWebKeySignatureAlgorithm
import com.microsoft.azure.keyvault.webkey.JsonWebKeyType
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.ECDSA_SECP256K1_SHA256
import net.corda.core.crypto.Crypto.ECDSA_SECP256R1_SHA256
import net.corda.core.crypto.Crypto.RSA_SHA256
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executors

/*
 * Implementation of the CryptoService interface for Azure KeyVault.
 * Uses the Azure KeyVault Java API https://docs.microsoft.com/en-us/java/api/overview/azure/keyvault .
 * Supported algorithms are ECDSA_SECP256R1_SHA256, ECDSA_SECP256K1_SHA256 and RSA_SHA256.
 */
class AzureKeyVaultCryptoService(private val keyVaultClient: KeyVaultClient, private val keyVaultUrl: String, private val protection: Protection = DEFAULT_PROTECTION) : CryptoService {

    /**
     * The protection parameter indicates if  KeyVault should store keys protected by an HSM or as "software-protected" keys.
     */
    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        checkAlias(alias)
        val keyRequest: CreateKeyRequest = createKeyRequest(scheme.schemeNumberID, alias, protection)
        val keyBundle = keyVaultClient.createKey(keyRequest)
        return toPublicKey(keyBundle)
    }

    private fun toPublicKey(keyBundle: KeyBundle): PublicKey {
        val kty = keyBundle.key().kty()
        return when (kty) {
            JsonWebKeyType.EC, JsonWebKeyType.EC_HSM -> keyBundle.key().toEC().public
            JsonWebKeyType.RSA, JsonWebKeyType.RSA_HSM -> keyBundle.key().toRSA().public
            else -> throw IllegalArgumentException("Key type $kty not supported")
        }
    }

    override fun containsKey(alias: String): Boolean {
        checkAlias(alias)
        val keyBundle = keyVaultClient.getKey(createIdentifier(alias))
        return keyBundle != null
    }

    override fun getPublicKey(alias: String): PublicKey? {
        checkAlias(alias)
        val keyBundle = keyVaultClient.getKey(createIdentifier(alias))
        if (keyBundle?.key() == null) {
            return null
        }
        return toPublicKey(keyBundle)
    }

    override fun sign(alias: String, data: ByteArray): ByteArray {
        checkAlias(alias)
        // KeyVault can only sign over hashed data.
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        val hash = digest.digest()
        // Signing requires us to make two calls to KeyVault. First, we need to get the key to look up
        // its type, which we need to specify when making the call for the actual signing operation.
        // TODO if we can make absolutely sure that only the default algorithm is used here, we could skip the first call.
        val keyBundle = keyVaultClient.getKey(createIdentifier(alias))
        val algorithm = determineAlgorithm(keyBundle)
        val result = keyVaultClient.sign(createIdentifier(alias), algorithm, hash)
        val keyType = keyBundle.key().kty()
        return when (keyType) {
            JsonWebKeyType.RSA, JsonWebKeyType.RSA_HSM -> result.result()
            JsonWebKeyType.EC, JsonWebKeyType.EC_HSM -> toSupportedSignature(result.result())
            else -> throw IllegalStateException("Key type $keyType not supported.")
        }
    }

    override fun getSigner(alias: String): ContentSigner {
        return object : ContentSigner {
            init {
                checkAlias(alias)
            }

            private val publicKey: PublicKey = getPublicKey(alias) ?: throw CryptoServiceException("No key found for alias $alias")
            private val sigAlgID: AlgorithmIdentifier = Crypto.findSignatureScheme(publicKey).signatureOID

            private val baos = ByteArrayOutputStream()
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
            override fun getOutputStream(): OutputStream = baos
            override fun getSignature(): ByteArray = sign(alias, baos.toByteArray())
        }
    }

    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    override fun defaultTLSSignatureScheme(): SignatureScheme {
        return DEFAULT_TLS_SIGNATURE_SCHEME
    }

    private fun createIdentifier(alias: String) = keyVaultUrl.removeSuffix("/") + "/keys/" + alias

    private fun createKeyRequest(schemeNumberID: Int, alias: String, protection: Protection): CreateKeyRequest {
        val keyType: JsonWebKeyType = determineKeyType(schemeNumberID, protection)
        val requestBuilder = when (schemeNumberID) {
            RSA_SHA256.schemeNumberID ->
                CreateKeyRequest.Builder(keyVaultUrl, alias, keyType)
                        .withKeySize(RSA_SHA256.keySize)
            ECDSA_SECP256K1_SHA256.schemeNumberID ->
                CreateKeyRequest.Builder(keyVaultUrl, alias, keyType)
                        .withCurve(JsonWebKeyCurveName.P_256K)
            ECDSA_SECP256R1_SHA256.schemeNumberID ->
                CreateKeyRequest.Builder(keyVaultUrl, alias, keyType)
                        .withCurve(JsonWebKeyCurveName.P_256)
            else -> throw IllegalArgumentException("Scheme $schemeNumberID not supported.")
        }
        return requestBuilder.withKeyOperations(listOf(JsonWebKeyOperation.SIGN)).build()
    }

    private fun determineKeyType(schemeNumberID: Int, protection: Protection): JsonWebKeyType {
        return when (schemeNumberID) {
            RSA_SHA256.schemeNumberID ->
                if (protection == Protection.HARDWARE) {
                    JsonWebKeyType.RSA_HSM
                } else {
                    JsonWebKeyType.RSA
                }
            ECDSA_SECP256K1_SHA256.schemeNumberID, ECDSA_SECP256R1_SHA256.schemeNumberID ->
                if (protection == Protection.HARDWARE) {
                    JsonWebKeyType.EC_HSM
                } else {
                    JsonWebKeyType.EC
                }
            else -> throw IllegalArgumentException("Scheme $schemeNumberID not supported.")
        }
    }

    private fun determineAlgorithm(keyBundle: KeyBundle): JsonWebKeySignatureAlgorithm {
        val keyType = keyBundle.key().kty()
        return if (keyType == JsonWebKeyType.EC_HSM || keyType == JsonWebKeyType.EC) {
            if (keyBundle.key().crv() == P_256) {
                JsonWebKeySignatureAlgorithm.ES256
            } else if (keyBundle.key().crv() == P_256K) {
                JsonWebKeySignatureAlgorithm.ES256K
            } else {
                throw IllegalArgumentException("Unsupported curve ${keyBundle.key().crv()}")
            }
        } else if (keyBundle.key().kty() == JsonWebKeyType.RSA_HSM || keyBundle.key().kty() == JsonWebKeyType.RSA) {
            JsonWebKeySignatureAlgorithm.RS256
        } else {
            throw IllegalArgumentException("Key type ${keyBundle.key().kty()} not supported.")
        }
    }

    /**
     * Used to indicate if generated keys should be hardware-protected or not.
     */
    enum class Protection {
        SOFTWARE, HARDWARE
    }

    companion object {
        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        // default protection when not set in config. See [AzureKeyVaultConfig].
        val DEFAULT_PROTECTION = Protection.HARDWARE

        /**
         * Convert from Microsoft's 'raw' (P1363) format to ASN.1 DER.
         * See https://github.com/Azure/azure-keyvault-java/issues/58
         */
        private fun toSupportedSignature(signature: ByteArray): ByteArray {
            val r = BigInteger(1, signature.sliceArray(IntRange(0, signature.size / 2 - 1))).toByteArray()
            val s = BigInteger(1, signature.sliceArray(IntRange(signature.size / 2, signature.size - 1))).toByteArray()
            val b2 = r.size
            val b3 = s.size
            val b1 = (4 + b2 + b3)
            return byteArrayOf(0x30, b1.toByte(), 0x02, b2.toByte(), *r, 0x02, b3.toByte(), *s)
        }

        /**
         *
         * For reference, @see <a href="https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-create-service-principal-portal">How to create an Azure Active Directory application</a>
         * for details.
         *
         * Sign in using the Azure Active Directory Authentication Library https://github.com/AzureAD/azure-activedirectory-library-for-java
         */
        fun createKeyVaultClient(path: String, password: String, alias: String, clientId: String): KeyVaultClient {
            val keyStore = KeyStore.getInstance("pkcs12", "SunJSSE")
            keyStore.load(FileInputStream(path), password.toCharArray())
            val certificate = keyStore.getCertificate(alias) as X509Certificate
            val certificateKey = keyStore.getKey(alias, password.toCharArray()) as PrivateKey
            val keyVaultCredentials: KeyVaultCredentials = object : KeyVaultCredentials() {
                override fun doAuthenticate(authorization: String?, resource: String?, scope: String?): String {
                    val context = AuthenticationContext(authorization, true, Executors.newCachedThreadPool())
                    val keyCredential = AsymmetricKeyCredential.create(clientId, certificateKey, certificate)
                    val result = context.acquireToken(resource, keyCredential, null).get()
                    return result.accessToken
                }
            }
            return KeyVaultClient(keyVaultCredentials)
        }

        /**
         * Parse the configuration file at the specified path configFile.
         */
        fun fromConfigurationFile(configFile: Path): AzureKeyVaultCryptoService {
            val config = parseConfigFile(configFile)
            val keyVaultClient: KeyVaultClient = createKeyVaultClient(config.path, config.password, config.alias, config.clientId)
            return AzureKeyVaultCryptoService(keyVaultClient, config.keyVaultURL, config.protection
                    ?: DEFAULT_PROTECTION)
        }

        internal fun parseConfigFile(configFile: Path): AzureKeyVaultConfig {
            try {
                val config = ConfigFactory.parseFile(configFile.toFile())
                return config.parseAs(Companion.AzureKeyVaultConfig::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${configFile.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }

        private fun checkAlias(alias: String) {
            require(alias.matches("^[0-9a-zA-Z-]{1,127}$".toRegex())) { "Alias $alias is not valid. Alias must conform to the following pattern: ^[0-9a-zA-Z-]{1,127}\$" }
        }

        /*
         * Configuration for Azure KeyVault.
         * @param path path to the keystore for login.
         * @param alias the alias of the key used for login.
         * @param password the password to the key store.
         * @param clientId the client id for the login.
         * @param keyVaultURL the URL of the key vault.
         * @param protection If set to "HARDWARE", 'hard' keys will be used, if set to "SOFTWARE", 'soft' keys will be used as described in the Azure KeyVault documentation.
         * Both types of key have the key stored in the HSM at rest. The difference is for a software-protected key when cryptographic operations are performed they
         * are performed in software in compute VMs while for HSM-protected keys the cryptographic operations are performed within the HSM. In test/dev environments
         * using the software-protected option is recommended while in production use HSM-protected. Please refer to Azure's Key Vault documentation for further details.
         * and use software-protected keys in only test/pilot scenarios.
         */
        data class AzureKeyVaultConfig(val path: String, val password: String, val alias: String, val clientId: String, val keyVaultURL: String, val protection: Protection? = null)
    }
}