package net.corda.nodeapi.internal.cryptoservice.utimaco

import CryptoServerCXI.CryptoServerCXI
import CryptoServerJCE.CryptoServerProvider
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.utilities.detailedLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.*
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.UnsupportedOperationException
import java.net.ConnectException
import java.nio.file.Path
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.reflect.full.memberProperties

/**
 * Implementation of CryptoService for the Utimaco HSM.
 */
class UtimacoCryptoService(
        private val cryptoServerProvider: CryptoServerProvider,
        private val config: UtimacoConfig,
        private val auth: () -> UtimacoCredentials
) : CryptoService {

    override fun getType(): SupportedCryptoServices = SupportedCryptoServices.UTIMACO

    private val keyStore: KeyStore
    private val keyTemplate: CryptoServerCXI.KeyAttributes

    init {
        try {
            keyTemplate = toKeyTemplate(config)
            detailedLogger.trace { "CryptoService(action=authenticate_start)" }
            authenticate(auth())
            val authState = cryptoServerProvider.cryptoServer.authState
            detailedLogger.trace { "CryptoService(action=authenticate_end;authState=$authState)" }
            require((authState and 0x0000000F) >= config.authThreshold) {
                "Insufficient authentication: auth state is $authState, at least ${config.authThreshold} is required."
            }
            keyStore = KeyStore.getInstance("CryptoServer", cryptoServerProvider)
            detailedLogger.trace { "CryptoService(action=keystore_load_start)" }
            keyStore.load(null, null)
            detailedLogger.trace { "CryptoService(action=keystore_load_end)" }
        } catch (e: CryptoServerAPI.CryptoServerException) {
            throw UtimacoHSMException(HsmErrors.errors[e.ErrorCode], e)
        }
    }

    private inline fun <T> withAuthentication(block: () -> T): T {
        return withErrorMapping {
            if (cryptoServerProvider.cryptoServer.authState and 0x0000000F >= config.authThreshold) {
                block()
            } else {
                authenticate(auth())
                block()
            }
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        return generateKeyPair(alias, scheme, keyTemplate)
    }

    override fun containsKey(alias: String): Boolean {
        try {
            return withAuthentication {
                detailedLogger.trace { "CryptoService(action=key_lookup_start;alias=$alias)" }
                val exists = keyStore.containsAlias(alias)
                detailedLogger.trace { "CryptoService(action=key_lookup_end;alias=$alias;found=$exists)"}
                exists
            }
        } catch (e: CryptoServerAPI.CryptoServerException) {
            HsmErrors.errors[e.ErrorCode]
            throw UtimacoHSMException(HsmErrors.errors[e.ErrorCode], e)
        }
    }

    override fun getPublicKey(alias: String): PublicKey? {
        try {
            return withAuthentication {
                detailedLogger.trace { "CryptoService(action=key_get_start;alias=$alias)" }
                val key = keyStore.getCertificate(alias)?.publicKey?.let {
                    KeyFactory.getInstance(it.algorithm).generatePublic(X509EncodedKeySpec(it.encoded))
                }
                detailedLogger.trace { "CryptoService(action=key_get_end;alias=$alias)"}
                key
            }
        } catch (e: CryptoServerAPI.CryptoServerException) {
            HsmErrors.errors[e.ErrorCode]
            throw UtimacoHSMException(HsmErrors.errors[e.ErrorCode], e)
        }
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        try {
            return withAuthentication {
                (keyStore.getKey(alias, null) as PrivateKey?)?.let {
                    val algorithm = signAlgorithm ?: if (it.algorithm == "RSA") {
                        "SHA256withRSA"
                    } else {
                        "SHA256withECDSA"
                    }
                    detailedLogger.trace { "CryptoService(action=signing_start;alias=$alias;algorithm=$algorithm)" }
                    val signature = Signature.getInstance(algorithm, cryptoServerProvider)
                    signature.initSign(it)
                    signature.update(data)
                    val signedData = signature.sign()
                    detailedLogger.trace { "CryptoService(action=signing_end;alias=$alias;algorithm=$algorithm)" }
                    signedData
                } ?: throw CryptoServiceException("No key found for alias $alias", isRecoverable = false)
            }
        } catch (e: CryptoServerAPI.CryptoServerException) {
            HsmErrors.errors[e.ErrorCode]
            throw UtimacoHSMException(HsmErrors.errors[e.ErrorCode], e)
        }
    }

    override fun getSigner(alias: String): ContentSigner {
        detailedLogger.trace { "CryptoService(action=get_signer;alias=$alias)" }
        return object : ContentSigner {
            private val publicKey: PublicKey = getPublicKey(alias) ?: throw CryptoServiceException("No key found for alias $alias", isRecoverable = false)
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

    fun generateKeyPair(alias: String, scheme: SignatureScheme, keyTemplate: CryptoServerCXI.KeyAttributes): PublicKey {
        return withAuthentication {
            val keyAttributes = attributesForScheme(keyTemplate, scheme.schemeNumberID)
            keyAttributes.name = alias
            val overwrite = if (config.keyOverride) CryptoServerCXI.FLAG_OVERWRITE else 0
            detailedLogger.trace { "CryptoService(action=generate_key_pair_start;alias=$alias;scheme=$scheme)" }
            cryptoServerProvider.cryptoServer.generateKey(overwrite, keyAttributes, config.keyGenMechanism)
            detailedLogger.trace { "CryptoService(action=generate_key_pair_end;alias=$alias;scheme=$scheme)" }
            getPublicKey(alias) ?: throw CryptoServiceException("Key generation for alias $alias succeeded, but key could not be accessed afterwards.")
        }
    }

    fun logOff() {
        cryptoServerProvider.logoff()
    }

    override fun createWrappingKey(alias: String, failIfExists: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme): Pair<PublicKey, WrappedPrivateKey> {
        throw UnsupportedOperationException()
    }

    override fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray {
        throw UnsupportedOperationException()
    }

    override fun getWrappingMode(): WrappingMode? = null

    private fun authenticate(credentials: UtimacoCredentials) {
        if (credentials.keyFile != null) {
            cryptoServerProvider.loginSign(credentials.username, credentials.keyFile.toFile().absolutePath, String(credentials.password))
        } else {
            cryptoServerProvider.loginPassword(credentials.username, credentials.password)
        }
    }

    class UtimacoHSMException(message: String?, cause: Throwable? = null) : CryptoServiceException(message, cause)

    data class UtimacoCredentials(val username: String, val password: ByteArray, val keyFile: Path? = null)

    /*
     * Configuration for the Utimaco CryptoService.
     * @param host.
     * @param port port of the device.
     * @param connectionTimeout maximum wait in milliseconds upon connection to the device.
     * @param timeout maximum wait in milliseconds if the device is not responding.
     * @param keepSessionAlive if set to false, the session will expire after 15 minutes.
     * @param keyGroup the key group to be used when querying and generating keys.
     * @param keySpecifier the key specifier used when querying and generating keys.
     * @param authThreshold authentication threshold, refer to the Utimaco documentation.
     * @param keyOverride overwrite existing keys with the same alias when generating new keys.
     * @param keyExport indicates if keys should be created as exportable.
     * @param keyGenMechanism refer to the Utimaco documentation.
     * @param username username for login.
     * @param password password for login, or password to keyFile (if specified).
     * @param keyFile (optional) log in using a key file. Refer to the Utimaco documentation for details.
     */
    data class UtimacoConfig(
            val host: String,
            val port: Int,
            val connectionTimeout: Int = 30000,
            val timeout: Int = 60000,
            val keepSessionAlive: Boolean = false,
            val keyGroup: String = "*",
            val keySpecifier: Int = -1,
            val authThreshold: Int = 1,
            val keyOverride: Boolean = false,
            val keyExport: Boolean = false,
            val keyGenMechanism: Int = 4,
            val username: String,
            val password: String,
            val keyFile: Path? = null
    )

    /**
     * Taken from network-services.
     * Configuration class for [CryptoServerProvider].
     * Currently not supported: DefaultUser,KeyStorePath,LogFile,LogLevel,LogSize.
     */
    internal data class CryptoServerProviderConfig(
            val Device: String,
            val ConnectionTimeout: Int,
            val Timeout: Int,
            val KeepSessionAlive: Int,
            val KeyGroup: String,
            val KeySpecifier: Int
    )

    private fun attributesForScheme(keyTemplate: CryptoServerCXI.KeyAttributes, schemeId: Int): CryptoServerCXI.KeyAttributes {
        if (schemeId !in keyAttributeForScheme.keys) {
            throw NoSuchAlgorithmException("No mapping for scheme ID $schemeId.")
        }
        val schemeAttributes = keyAttributeForScheme[schemeId]!!
        return CryptoServerCXI.KeyAttributes().apply {
            specifier = keyTemplate.specifier
            group = keyTemplate.group
            export = keyTemplate.export
            algo = schemeAttributes.algo
            curve = schemeAttributes.curve
            size = schemeAttributes.size
        }
    }

    companion object {
        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        private const val E_JCSA_CLUSTER_OPEN = 0xB9800021.toInt() // "Unable to open any CryptoServer", see HsmErrors

        private val detailedLogger = detailedLogger()

        private val keyAttributeForScheme: Map<Int, CryptoServerCXI.KeyAttributes> = mapOf(
                Crypto.ECDSA_SECP256R1_SHA256.schemeNumberID to CryptoServerCXI.KeyAttributes().apply {
                    algo = CryptoServerCXI.KEY_ALGO_ECDSA
                    setCurve("secp256r1")
                },
                Crypto.ECDSA_SECP256K1_SHA256.schemeNumberID to CryptoServerCXI.KeyAttributes().apply {
                    algo = CryptoServerCXI.KEY_ALGO_ECDSA
                    setCurve("secp256k1")
                },
                Crypto.RSA_SHA256.schemeNumberID to CryptoServerCXI.KeyAttributes().apply {
                    algo = CryptoServerCXI.KEY_ALGO_RSA
                    size = Crypto.RSA_SHA256.keySize!!
                }
        )

        private inline fun <T> withErrorMapping(block: () -> T): T {
            try {
                return block()
            } catch (e: CryptoServerAPI.CryptoServerException) {
                if (e.ErrorCode == E_JCSA_CLUSTER_OPEN) {
                    // This error is reported when it's not possible to connect to any CryptoServer in a cluster mode.
                    // In non-cluster mode ConnectException is thrown instead.
                    // To let CryptoServiceSigningService start when connection to HSM cannot be temporarily established,
                    // the error needs to be converted to IOException which has a special handling inside CryptoServiceSigningService.
                    throw ConnectException("${CryptoServerAPI.CryptoServerException.getErrorMessage(e.ErrorCode, null)}: " +
                            HsmErrors.errors[e.ErrorCode])
                }
                throw UtimacoHSMException(HsmErrors.errors[e.ErrorCode], e)
            }
        }

        fun parseConfigFile(configFile: Path): UtimacoConfig {
            try {
                val config = ConfigFactory.parseFile(configFile.toFile()).resolve()
                return config.parseAs(UtimacoConfig::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${configFile.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }

        /**
         * Username and password are stored in files, base64-encoded.
         */
        fun fileBasedAuth(usernameFile: Path, passwordFile: Path): () -> UtimacoCredentials = {
            val username = String(Base64.getDecoder().decode(usernameFile.toFile().readLines().first()))
            val pw = if (usernameFile == passwordFile) {
                Base64.getDecoder().decode(passwordFile.toFile().readLines().get(1))
            } else {
                Base64.getDecoder().decode(passwordFile.toFile().readLines().get(0))
            }
            UtimacoCredentials(username, pw)
        }

        fun fromConfigurationFile(configFile: Path?): UtimacoCryptoService {
            val config = parseConfigFile(configFile!!)
            return fromConfig(config) { UtimacoCredentials(config.username, config.password.toByteArray(), config.keyFile) }
        }

        fun fromConfig(configuration: UtimacoConfig, auth: () -> UtimacoCredentials): UtimacoCryptoService {
            val providerConfig = toCryptoServerProviderConfig(configuration)
            val cryptoServerProvider = createProvider(providerConfig)
            return UtimacoCryptoService(cryptoServerProvider, configuration, auth)
        }

        /**
         * Note that some attributes cannot be determined at this point, as they depend on the scheme ID.
         */
        private fun toKeyTemplate(config: UtimacoConfig): CryptoServerCXI.KeyAttributes {
            return CryptoServerCXI.KeyAttributes().apply {
                specifier = config.keySpecifier
                group = config.keyGroup
                export = if (config.keyExport) 1 else 0
            }
        }

        private fun toCryptoServerProviderConfig(config: UtimacoConfig): CryptoServerProviderConfig {
            return CryptoServerProviderConfig(
                    // KeepSessionAlive=1 doesn't prevent from session expiry after 5 min of inactivity when using remote connection to HSM.
                    // As a workaround, we use HSM cluster with two identical nodes to handle network re-connections automatically.
                    "${config.port}@${config.host} ${config.port}@${config.host}",
                    config.connectionTimeout,
                    config.timeout,
                    if (config.keepSessionAlive) 1 else 0,
                    config.keyGroup,
                    config.keySpecifier
            )
        }

        /**
         * Taken from network-services.
         * Creates an instance of [CryptoServerProvider] configured accordingly to the passed configuration.
         *
         * @param config crypto server provider configuration.
         *
         * @return preconfigured instance of [CryptoServerProvider].
         */
        private fun createProvider(config: CryptoServerProviderConfig): CryptoServerProvider {
            val cfgBuffer = ByteArrayOutputStream()
            val writer = cfgBuffer.writer(Charsets.UTF_8)
            for (property in CryptoServerProviderConfig::class.memberProperties) {
                writer.write("${property.name} = ${property.get(config)}\n")
            }
            writer.close()
            val cfg = cfgBuffer.toByteArray().inputStream()
            return withErrorMapping { CryptoServerProvider(cfg) }
        }
    }
}

// This code (incl. the hsm_errors file) is duplicated with the Network-Management module.
object HsmErrors {
    val errors: Map<Int, String> by lazy(HsmErrors::load)
    fun load(): Map<Int, String> {
        val errors = HashMap<Int, String>()
        val hsmErrorsStream = HsmErrors::class.java.getResourceAsStream("hsm_errors")
        hsmErrorsStream.bufferedReader().lines().reduce(null) { previous, current ->
            if (previous == null) {
                current
            } else {
                errors[java.lang.Long.decode(previous).toInt()] = current
                null
            }
        }
        return errors
    }
}