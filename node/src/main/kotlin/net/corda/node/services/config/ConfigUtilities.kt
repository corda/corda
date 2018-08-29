package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.toPath
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.TwoWaySslConfiguration
import net.corda.nodeapi.internal.config.toProperties
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.save
import org.slf4j.LoggerFactory
import java.nio.file.Path

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

    private fun Config.cordaEntriesOnly(): Config {

        return ConfigFactory.parseMap(toProperties().filterKeys { (it as String).startsWith(CORDA_PROPERTY_PREFIX) }.mapKeys { (it.key as String).removePrefix(CORDA_PROPERTY_PREFIX) })
    }
}

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
// TODO Move this to KeyStoreConfigHelpers
fun NodeConfiguration.configureWithDevSSLCertificate() = configureDevKeyAndTrustStores(myLegalName, signingCertificateStore, p2pSslConfiguration, certificatesDirectory)

// TODO Move this to KeyStoreConfigHelpers
fun configureDevKeyAndTrustStores(myLegalName: CordaX500Name, signingCertificateStore: FileBasedCertificateStoreSupplier, p2pSslConfig: TwoWaySslConfiguration, certificatesDirectory: Path) {

    val specifiedTrustStore = p2pSslConfig.trustStore.getOptional()

    val specifiedKeyStore = p2pSslConfig.keyStore.getOptional()
    val specifiedSigningStore = signingCertificateStore.getOptional()

    if (specifiedTrustStore != null && specifiedKeyStore != null && specifiedSigningStore != null) return
    certificatesDirectory.createDirectories()

    if (specifiedTrustStore == null) {
        val devCaTrustStore = X509KeyStore.fromFile(signingCertificateStore::class.java.classLoader.getResource("certificates/$DEV_CA_TRUST_STORE_FILE").toPath(), DEV_CA_TRUST_STORE_PASS)
        // TODO sollecitom refactor this
        devCaTrustStore.internal.save(p2pSslConfig.trustStore.path, p2pSslConfig.trustStore.password)
    }

    if (p2pSslConfig.keyStore.getOptional() == null || signingCertificateStore.getOptional() == null) {
        // TODO sollecitom refactor this
        val signingKeyStore = FileBasedCertificateStoreSupplier(signingCertificateStore.path, signingCertificateStore.password).createDevSigningKeyStore(myLegalName)

        // TODO sollecitom refactor this
        FileBasedCertificateStoreSupplier(p2pSslConfig.keyStore.path, p2pSslConfig.keyStore.password).createDevP2PKeyStore(myLegalName)

        // Move distributed service composite key (generated by IdentityGenerator.generateToDisk) to keystore if exists.
        val distributedServiceKeystore = certificatesDirectory / "distributedService.jks"
        if (distributedServiceKeystore.exists()) {
            val serviceKeystore = X509KeyStore.fromFile(distributedServiceKeystore, DEV_CA_KEY_STORE_PASS)
            signingKeyStore.update {
                serviceKeystore.aliases().forEach {
                    if (serviceKeystore.internal.isKeyEntry(it)) {
                        setPrivateKey(it, serviceKeystore.getPrivateKey(it, DEV_CA_PRIVATE_KEY_PASS), serviceKeystore.getCertificateChain(it))
                    } else {
                        setCertificate(it, serviceKeystore.getCertificate(it))
                    }
                }
            }
        }
    }
}

// TODO sollecitom try to remove this or refactor it
fun TwoWaySslConfiguration.configureDevKeyAndTrustStores(myLegalName: CordaX500Name, certificatesDirectory: Path) {
    certificatesDirectory.createDirectories()
    val specifiedTrustStore = trustStore.getOptional()
    if (specifiedTrustStore == null) {
        val devCaTrustStore = X509KeyStore.fromFile(trustStore::class.java.classLoader.getResource("certificates/$DEV_CA_TRUST_STORE_FILE").toPath(), DEV_CA_TRUST_STORE_PASS)
        // TODO sollecitom refactor this (try to use `update` with the cert in the devCaTrustStore
        devCaTrustStore.internal.save(trustStore.path, trustStore.password)
    }
    if (keyStore.getOptional() == null) {
        createDevP2PKeyStore(myLegalName)
    }
}

/** This is generally covered by commons-lang. */
object CordaSystemUtils {
    const val OS_NAME = "os.name"

    const val MAC_PREFIX = "Mac"
    const val WIN_PREFIX = "Windows"

    fun isOsMac() = getOsName().startsWith(MAC_PREFIX)
    fun isOsWindows() = getOsName().startsWith(WIN_PREFIX)
    fun getOsName() = System.getProperty(OS_NAME)
}