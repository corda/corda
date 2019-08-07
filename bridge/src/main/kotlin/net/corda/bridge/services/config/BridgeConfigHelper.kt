package net.corda.bridge.services.config

import com.typesafe.config.*
import com.typesafe.config.ConfigFactory.systemEnvironment
import com.typesafe.config.ConfigFactory.systemProperties
import net.corda.bridge.services.api.CryptoServiceConfig
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.config.internal.Version3BridgeConfigurationImpl
import net.corda.bridge.services.config.internal.Version4FirewallConfiguration
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceFactory
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun configOf(vararg pairs: Pair<String, Any?>): Config = ConfigFactory.parseMap(mapOf(*pairs))
operator fun Config.plus(overrides: Map<String, Any?>): Config = ConfigFactory.parseMap(overrides).withFallback(this)

object BridgeConfigHelper {
    private const val BRIDGE_PROPERTY_PREFIX = "bridge."
    private val passwordRegex = ".*(pass|password|pwd)$".toRegex(RegexOption.IGNORE_CASE)

    internal val FLOAT_NAME = CordaX500Name(commonName = "Float", organisation = "Corda", locality = "London", country = "GB")
    internal val BRIDGE_NAME = CordaX500Name(commonName = "Bridge", organisation = "Corda", locality = "London", country = "GB")

    private val log = LoggerFactory.getLogger(javaClass)

    fun loadConfig(baseDirectory: Path, configFile: Path = baseDirectory / "firewall.conf"): FirewallConfiguration {
        val latestConfig = loadConfig(baseDirectory, configFile, "firewalldefault_latest.conf")
        try {
            val firewallConfig = latestConfig.parseAs(FirewallConfigurationImpl::class, baseDirectory = baseDirectory)
            latestConfig.log()
            return firewallConfig
        } catch (ex: UnknownConfigurationKeysException) {

            log.info("Attempting to parse using old formats. Modern format parsing failed due to:", ex)

            for ((configClass, defaultConfigFile) in listOf(
                    Pair(Version4FirewallConfiguration::class, "firewalldefault_v4.conf"),
                    Pair(Version3BridgeConfigurationImpl::class, "firewalldefault_v3.conf")
            )) {
                try {
                    val config = loadConfig(baseDirectory, configFile, defaultConfigFile)
                    val firewallConfig = config.parseAs(configClass, baseDirectory = baseDirectory).toConfig()
                    config.log()
                    log.warn("Old style config used. To avoid seeing this warning in the future, please upgrade to new style. " +
                            "New style config will look as follows:\n${firewallConfig.toConfig().asString()}")
                    return firewallConfig
                } catch (ex2: ConfigException) {
                    log.debug("Parsing with $configClass failed", ex2)
                } catch (ex2: UnknownConfigurationKeysException) {
                    log.debug("Parsing with $configClass failed", ex2)
                }
            }
            latestConfig.log()
            log.error("Old formats parsing failed as well.")
            throw ex
        }
    }

    fun Config.asString(): String = root().maskPassword().render(ConfigRenderOptions.defaults())

    private fun Config.log() {
        log.info("Config:\n${asString()}")
    }

    private fun loadConfig(baseDirectory: Path,
                           configFile: Path,
                           defaultConfigResource: String,
                           allowMissingConfig: Boolean = false,
                           configOverrides: Config = ConfigFactory.empty()): Config {
        val parseOptions = ConfigParseOptions.defaults()
        val defaultConfig = ConfigFactory.parseResources(defaultConfigResource, parseOptions.setAllowMissing(false))
        val appConfig = ConfigFactory.parseFile(configFile.toFile(), parseOptions.setAllowMissing(allowMissingConfig))
        val systemOverrides = systemProperties().bridgeEntriesOnly()
        val environmentOverrides = systemEnvironment().bridgeEntriesOnly()

        return configOverrides
                // Add substitution values here
                .withFallback(systemOverrides) //for database integration tests
                .withFallback(environmentOverrides) //for database integration tests
                .withFallback(configOf("baseDirectory" to baseDirectory.toString()))
                .withFallback(appConfig)
                .withFallback(defaultConfig)
                .resolve()
    }

    private fun Config.bridgeEntriesOnly(): Config {
        return ConfigFactory.parseMap(toProperties().filterKeys { (it as String).startsWith(BRIDGE_PROPERTY_PREFIX) }.mapKeys { (it.key as String).removePrefix(BRIDGE_PROPERTY_PREFIX) })
    }

    private fun ConfigObject.maskPassword(): ConfigObject {
        val maskedValue = ConfigValueFactory.fromAnyRef("****", "Content hidden")
        return toList().fold(this) { config, (key, value) ->
            when {
                passwordRegex.matches(key) && value.valueType() == ConfigValueType.STRING -> config.withoutKey(key).withValue(key, maskedValue)
                value is ConfigObject -> config.withoutKey(key).withValue(key, value.maskPassword())
                else -> config
            }
        }
    }

    fun makeCryptoService(csConf: CryptoServiceConfig?, legalName: CordaX500Name, keyStoreSupplier: FileBasedCertificateStoreSupplier? = null): CryptoService {
        return CryptoServiceFactory.makeCryptoService(
                csConf?.name ?: SupportedCryptoServices.BC_SIMPLE,
                legalName,
                keyStoreSupplier,
                csConf?.conf)
    }
}