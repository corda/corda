// TODO: Remove when configureTestSSL() is moved.
@file:JvmName("ConfigUtilities")

package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.core.copyTo
import net.corda.core.createDirectories
import net.corda.core.crypto.KeyStoreUtilities
import net.corda.core.crypto.X509Utilities
import net.corda.core.div
import net.corda.core.exists
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.config.SSLConfiguration
import org.bouncycastle.asn1.x500.X500Name
import java.nio.file.Path

fun configOf(vararg pairs: Pair<String, Any?>) = ConfigFactory.parseMap(mapOf(*pairs))
operator fun Config.plus(overrides: Map<String, Any?>) = ConfigFactory.parseMap(overrides).withFallback(this)

object ConfigHelper {
    private val log = loggerFor<ConfigHelper>()

    fun loadConfig(baseDirectory: Path,
                   configFile: Path = baseDirectory / "node.conf",
                   allowMissingConfig: Boolean = false,
                   configOverrides: Config = ConfigFactory.empty()): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources("reference.conf", parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))
        val finalConfig = configOf(
                // Add substitution values here
                "basedir" to baseDirectory.toString())
                .withFallback(configOverrides)
                .withFallback(appConfig)
                .withFallback(defaultConfig)
                .resolve()
        log.info("Config:\n${finalConfig.root().render(ConfigRenderOptions.defaults())}")
        return finalConfig
    }
}

/**
 * Strictly for dev only automatically construct a server certificate/private key signed from
 * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
 */
fun NodeConfiguration.configureWithDevSSLCertificate() = configureDevKeyAndTrustStores(myLegalName)

fun SSLConfiguration.configureDevKeyAndTrustStores(myLegalName: X500Name) {
    certificatesDirectory.createDirectories()
    if (!trustStoreFile.exists()) {
        javaClass.classLoader.getResourceAsStream("net/corda/node/internal/certificates/cordatruststore.jks").copyTo(trustStoreFile)
    }
    if (!keyStoreFile.exists()) {
        val caKeyStore = KeyStoreUtilities.loadKeyStore(javaClass.classLoader.getResourceAsStream("net/corda/node/internal/certificates/cordadevcakeys.jks"), "cordacadevpass")
        X509Utilities.createKeystoreForCordaNode(keyStoreFile, keyStorePassword, keyStorePassword, caKeyStore, "cordacadevkeypass", myLegalName)
    }
}
