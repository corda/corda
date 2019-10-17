package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.CordaSystemUtils
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.node.internal.Node
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.toProperties
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.nodeapi.internal.installDevNodeCaCertPath
import net.corda.nodeapi.internal.loadDevCaTrustStore
import net.corda.nodeapi.internal.registerDevP2pCertificates
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

fun configOf(vararg pairs: Pair<String, Any?>): Config = ConfigFactory.parseMap(mapOf(*pairs))
operator fun Config.plus(overrides: Map<String, Any?>): Config = ConfigFactory.parseMap(overrides).withFallback(this)

object ConfigHelper {

    private const val CORDA_PROPERTY_PREFIX = "corda."

    private val log = LoggerFactory.getLogger(javaClass)
    fun loadConfig(baseDirectory: Path,
                   configFile: Path = baseDirectory / "node.conf",
                   allowMissingConfig: Boolean = false,
                   configOverrides: Config = ConfigFactory.empty()): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources("reference.conf", parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))

        // Detect the underlying OS. If mac or windows non-server then we assume we're running in devMode. Unless specified otherwise.
        val smartDevMode = CordaSystemUtils.isOsMac() || (CordaSystemUtils.isOsWindows() && !CordaSystemUtils.getOsName().toLowerCase().contains("server"))
        val devModeConfig = ConfigFactory.parseMap(mapOf("devMode" to smartDevMode))

        val systemOverrides = ConfigFactory.systemProperties().cordaEntriesOnly()
        val environmentOverrides = ConfigFactory.systemEnvironment().cordaEntriesOnly()
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to baseDirectory.toString())
                .withFallback(configOverrides)
                .withFallback(systemOverrides)
                .withFallback(environmentOverrides)
                .withFallback(appConfig)
                .withFallback(devModeConfig) // this needs to be after the appConfig, so it doesn't override the configured devMode
                .withFallback(defaultConfig)
                .resolve()

        val entrySet = finalConfig.entrySet().filter { entry -> entry.key.contains("\"") }
        for ((key) in entrySet) {
            log.error("Config files should not contain \" in property names. Please fix: $key")
        }

        return finalConfig
    }

    private fun <T: Any> getCaseSensitivePropertyPath(target : KClass<T>?, path : List<String>) : String {

        require(path.isNotEmpty()) { "Path to config property cannot be empty." }

        val lookFor = path.first()
        target?.memberProperties?.forEach {
            if (it.name.toLowerCase() == lookFor.toLowerCase()) {
                return if (path.size > 1)
                    "${it.name}." +
                            getCaseSensitivePropertyPath(
                                    (it.getter.returnType.classifier as KClass<*>),
                                    path.subList(1, path.size))
                else
                    it.name
            }
        }

        return ""
    }

    /*
     * Gets
     */
    private fun Config.cordaEntriesOnly(): Config {

        val cordaPropOccurrences = mutableSetOf<String>()
        val badKeyConversions = mutableSetOf<String>()

        return ConfigFactory.parseMap(
                toProperties()
                .mapKeys {
                    val newKey = (it.key as String)
                                .replace("_", ".")
                                .toLowerCase()

                    if (newKey.contains(CORDA_PROPERTY_PREFIX) && cordaPropOccurrences.contains(newKey)) {
                            throw ShadowingException(it.key.toString(), newKey)
                    }

                    cordaPropOccurrences.add(newKey)
                    newKey.let { key ->
                        if (!key.contains(CORDA_PROPERTY_PREFIX))
                            return@let key

                        val nodeConfKey = key.removePrefix(CORDA_PROPERTY_PREFIX)
                        val configPath = getCaseSensitivePropertyPath(
                                NodeConfigurationImpl::class,
                                nodeConfKey.split(".")
                        )

                        if (nodeConfKey.length != configPath.length) {
                            Node.printWarning(
                                    "${it.key} (property or environment variable) cannot be mapped to an existing Corda" +
                                            " config property and thus won't be used as a config override!" +
                                            " It won't be passed as a config override! If that was the intention " +
                                            " double check the spelling and ensure there is such config key.")
                            badKeyConversions.add(configPath)
                        }
                        CORDA_PROPERTY_PREFIX + configPath
                    }
                }.filterKeys { it.startsWith(CORDA_PROPERTY_PREFIX) }
                .mapKeys { it.key.removePrefix(CORDA_PROPERTY_PREFIX) }
                .filterKeys { !badKeyConversions.contains(it) })
    }
}

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
// TODO Move this to KeyStoreConfigHelpers.
fun NodeConfiguration.configureWithDevSSLCertificate(cryptoService: CryptoService? = null) = p2pSslOptions.configureDevKeyAndTrustStores(myLegalName, signingCertificateStore, certificatesDirectory, cryptoService)

// TODO Move this to KeyStoreConfigHelpers.
fun MutualSslConfiguration.configureDevKeyAndTrustStores(myLegalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier, certificatesDirectory: Path, cryptoService: CryptoService? = null) {
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
                        .also { it.installDevNodeCaCertPath(myLegalName) }

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