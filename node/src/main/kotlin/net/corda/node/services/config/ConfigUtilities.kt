package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.CordaSystemUtils
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.node.internal.Node
import net.corda.node.services.config.schema.v1.V1NodeConfigurationSpec
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.toProperties
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.installDevNodeCaCertPath
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.nodeapi.internal.storeLegalIdentity
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.file.Path
import kotlin.math.min

fun configOf(vararg pairs: Pair<String, Any?>): Config = ConfigFactory.parseMap(mapOf(*pairs))
operator fun Config.plus(overrides: Map<String, Any?>): Config = ConfigFactory.parseMap(overrides).withFallback(this)

object ConfigHelper {

    private const val FLOW_EXTERNAL_OPERATION_THREAD_POOL_SIZE_MAX = 10

    private const val CORDA_PROPERTY_PREFIX = "corda."
    private const val UPPERCASE_PROPERTY_PREFIX = "CORDA."

    private val log = LoggerFactory.getLogger(javaClass)

    val DEFAULT_CONFIG_FILENAME = "node.conf"

    @Suppress("LongParameterList")
    fun loadConfig(baseDirectory: Path,
                   configFile: Path = baseDirectory / DEFAULT_CONFIG_FILENAME,
                   allowMissingConfig: Boolean = false,
                   configOverrides: Config = ConfigFactory.empty()): Config
        = loadConfig(baseDirectory,
            configFile = configFile,
            allowMissingConfig = allowMissingConfig,
            configOverrides = configOverrides,
            rawSystemOverrides = ConfigFactory.systemProperties(),
            rawEnvironmentOverrides = ConfigFactory.systemEnvironment())

    /**
     * Internal equivalent of [loadConfig] which allows the system and environment
     * overrides to be provided from a test.
     */
    @Suppress("LongParameterList")
    @VisibleForTesting
    internal fun loadConfig(baseDirectory: Path,
                   configFile: Path,
                   allowMissingConfig: Boolean,
                   configOverrides: Config,
                   rawSystemOverrides: Config,
                   rawEnvironmentOverrides: Config
    ): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources("corda-reference.conf", parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))

        // Detect the underlying OS. If mac or windows non-server then we assume we're running in devMode. Unless specified otherwise.
        val smartDevMode = CordaSystemUtils.isOsMac() || (CordaSystemUtils.isOsWindows() && !CordaSystemUtils.getOsName().toLowerCase().contains("server"))
        val devModeConfig = ConfigFactory.parseMap(mapOf("devMode" to smartDevMode))

        // Detect the number of cores
        val coreCount = Runtime.getRuntime().availableProcessors()
        val multiThreadingConfig = configOf(
                "flowExternalOperationThreadPoolSize" to min(coreCount, FLOW_EXTERNAL_OPERATION_THREAD_POOL_SIZE_MAX).toString()
        )

        val systemOverrides = rawSystemOverrides.cordaEntriesOnly()
        val environmentOverrides = rawEnvironmentOverrides.cordaEntriesOnly()
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to baseDirectory.toString())
                .withFallback(configOverrides)
                .withFallback(systemOverrides)
                .withFallback(environmentOverrides)
                .withFallback(appConfig)
                .withFallback(devModeConfig) // this needs to be after the appConfig, so it doesn't override the configured devMode
                .withFallback(multiThreadingConfig)
                .withFallback(defaultConfig)
                .resolve()

        val entrySet = finalConfig.entrySet().filter { entry ->
            // System properties can reasonably be expected to contain '.'. However, only
            // entries that match this pattern are allowed to contain a '"' character:
            //           systemProperties."text-without-any-quotes-in"
            with(entry.key) { contains("\"") && !matches("^systemProperties\\.\"[^\"]++\"\$".toRegex()) }
        }
        for (key in entrySet) {
            log.error("Config files should not contain \" in property names. Please fix: $key")
        }

        return finalConfig
    }

    private fun Config.cordaEntriesOnly(): Config {
        val cordaPropOccurrences = mutableSetOf<String>()
        val badKeyConversions = mutableSetOf<String>()

        return ConfigFactory.parseMap(
                toProperties()
                .mapKeys {
                    val original = it.key as String

                    // Reject environment variable that are in all caps
                    // since these cannot be properties.
                    if (original == original.toUpperCase()){
                        return@mapKeys original
                    }

                    var newKey = original
                                .replace('_', '.')
                                .replace(UPPERCASE_PROPERTY_PREFIX, CORDA_PROPERTY_PREFIX)

                    if (!newKey.startsWith(CORDA_PROPERTY_PREFIX)) {
                        return@mapKeys newKey
                    }

                    newKey = newKey.substring(CORDA_PROPERTY_PREFIX.length)

                    if (cordaPropOccurrences.contains(newKey))
                    {
                        throw ShadowingException(it.key.toString(), newKey)
                    }

                    cordaPropOccurrences.add(newKey)

                    newKey.let { key ->
                        val cfg = ConfigFactory.parseMap(mapOf(key to it.value))
                        val result = V1NodeConfigurationSpec.validate(cfg, Configuration.Options(strict = true))

                        val isInvalidProperty = result.errors.any { err -> err is Configuration.Validation.Error.Unknown }
                        if (isInvalidProperty) {
                            Node.printWarning(
                                    "${it.key} (property or environment variable) cannot be mapped to an existing Corda" +
                                            " config property and thus won't be used as a config override!" +
                                            " It won't be passed as a config override! If that was the intention " +
                                            " double check the spelling and ensure there is such config key.")
                            badKeyConversions.add(key)
                        }

                        CORDA_PROPERTY_PREFIX + key
                    }
                }.filterKeys { it.startsWith(CORDA_PROPERTY_PREFIX) }
                .mapKeys { it.key.substring(CORDA_PROPERTY_PREFIX.length) }
                .filterKeys { !badKeyConversions.contains(it) })
    }
}

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
// TODO Move this to KeyStoreConfigHelpers.
fun NodeConfiguration.configureWithDevSSLCertificate(cryptoService: CryptoService? = null, entropy: BigInteger? = null) =
        p2pSslOptions.configureDevKeyAndTrustStores(myLegalName, signingCertificateStore, certificatesDirectory, cryptoService, entropy)

// TODO Move this to KeyStoreConfigHelpers.
@Suppress("ComplexMethod")
fun MutualSslConfiguration.configureDevKeyAndTrustStores(myLegalName: CordaX500Name,
                                                         signingCertificateStore: FileBasedCertificateStoreSupplier,
                                                         certificatesDirectory: Path,
                                                         cryptoService: CryptoService? = null,
                                                         entropy: BigInteger? = null) {
    val specifiedTrustStore = trustStore.getOptional()

    val specifiedKeyStore = keyStore.getOptional()
    val specifiedSigningStore = signingCertificateStore.getOptional()

    if (specifiedTrustStore != null && specifiedKeyStore != null && specifiedSigningStore != null) return
    certificatesDirectory.createDirectories()

    if (specifiedTrustStore == null) {
        loadDevCaTrustStore().copyTo(trustStore.get(true))
    }

    if (specifiedKeyStore == null || specifiedSigningStore == null) {
        FileBasedCertificateStoreSupplier(keyStore.path, keyStore.storePassword, keyStore.entryPassword).get(true)
                .also { it.registerDevP2pCertificates(myLegalName) }
        when (cryptoService) {
            is BCCryptoService, null -> {
                val signingKeyStore = FileBasedCertificateStoreSupplier(signingCertificateStore.path, signingCertificateStore.storePassword, signingCertificateStore.entryPassword).get(true)
                        .also {
                            it.installDevNodeCaCertPath(myLegalName)
                            val keyPair = if (entropy != null) {
                                Crypto.deriveKeyPairFromEntropy(Crypto.DEFAULT_SIGNATURE_SCHEME, entropy)
                            } else {
                                Crypto.generateKeyPair()
                            }
                            it.storeLegalIdentity(X509Utilities.NODE_IDENTITY_KEY_ALIAS, keyPair)
                        }

                // Move distributed service composite key (generated by IdentityGenerator.generateToDisk) to keystore if exists.
                val distributedServiceKeystore = certificatesDirectory / "distributedService.jks"
                if (distributedServiceKeystore.exists()) {
                    val serviceKeystore = X509KeyStore.fromFile(distributedServiceKeystore, DEV_CA_KEY_STORE_PASS)

                    signingKeyStore.update {
                        serviceKeystore.aliases().forEach {
                            if (serviceKeystore.internal.isKeyEntry(it)) {
                                setPrivateKey(it, serviceKeystore.getPrivateKey(it, DEV_CA_KEY_STORE_PASS), serviceKeystore.getCertificateChain(it), signingKeyStore.entryPassword)
                            } else {
                                setCertificate(it, serviceKeystore.getCertificate(it))
                            }
                        }
                    }
                }
            }
            else -> throw IllegalArgumentException("CryptoService not supported.")
        }
    }
}
