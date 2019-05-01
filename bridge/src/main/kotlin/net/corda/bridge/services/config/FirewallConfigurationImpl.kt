package net.corda.bridge.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import net.corda.bridge.FirewallCmdLineOptions
import net.corda.bridge.services.api.*
import net.corda.bridge.services.config.BridgeConfigHelper.maskPassword
import net.corda.bridge.services.config.internal.Version3BridgeConfigurationImpl
import net.corda.bridge.services.config.internal.Version4FirewallConfiguration
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import java.nio.file.Path

fun Config.parseAsFirewallConfiguration(): FirewallConfiguration {
    return try {
        parseAs<FirewallConfigurationImpl>()
    } catch (ex: UnknownConfigurationKeysException) {

        FirewallCmdLineOptions.logger.info("Attempting to parse using old formats")

        val legacyConfigurationClasses = mutableListOf(Version4FirewallConfiguration::class, Version3BridgeConfigurationImpl::class)

        while (!legacyConfigurationClasses.isEmpty()) {
            val configurationClass = legacyConfigurationClasses.removeAt(0)
            try {
                // Note: "Ignore" is needed to disregard any default properties from "firewalldefault.conf" that are not applicable to previous versions
                val oldStyleConfig = parseAs(configurationClass, UnknownConfigKeysPolicy.IGNORE::handle)
                val newStyleConfig = oldStyleConfig.toConfig()

                val configAsString = newStyleConfig.toConfig().root().maskPassword().render(ConfigRenderOptions.defaults())
                FirewallCmdLineOptions.logger.warn("Old style config used. To avoid seeing this warning in the future, please upgrade to new style. " +
                        "New style config will look as follows:\n$configAsString")
                return newStyleConfig
            } catch (oldFormatEx: ConfigException) {
                FirewallCmdLineOptions.logger.debug("Parsing with $configurationClass failed", oldFormatEx)
            }
        }

        FirewallCmdLineOptions.logger.error("Old formats parsing failed as well.")
        throw ex
    }
}

data class BridgeSSLConfigurationImpl(private val sslKeystore: Path,
                                      private val keyStorePassword: String,
                                      private val keyStorePrivateKeyPassword: String = keyStorePassword,
                                      private val trustStoreFile: Path,
                                      private val trustStorePassword: String,
                                      private val revocationConfig: RevocationConfig,
                                      override val useOpenSsl: Boolean = false) : BridgeSSLConfiguration {

    override val keyStore = FileBasedCertificateStoreSupplier(sslKeystore, keyStorePassword, keyStorePrivateKeyPassword)
    // Trust store does not and should not contain any keys therefore keys password for it should not matter
    override val trustStore = FileBasedCertificateStoreSupplier(trustStoreFile, trustStorePassword, trustStorePassword)
}

data class BridgeOutboundConfigurationImpl(override val artemisBrokerAddress: NetworkHostAndPort,
                                           override val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                           override val artemisSSLConfiguration: BridgeSSLConfigurationImpl?,
                                           override val proxyConfig: ProxyConfig? = null) : BridgeOutboundConfiguration

data class BridgeInboundConfigurationImpl(override val listeningAddress: NetworkHostAndPort,
                                          override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : BridgeInboundConfiguration

data class BridgeInnerConfigurationImpl(override val floatAddresses: List<NetworkHostAndPort>,
                                        override val expectedCertificateSubject: CordaX500Name,
                                        override val tunnelSSLConfiguration: BridgeSSLConfigurationImpl?,
                                        override val enableSNI: Boolean = true) : BridgeInnerConfiguration

data class FloatOuterConfigurationImpl(override val floatAddress: NetworkHostAndPort,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val tunnelSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatOuterConfiguration

data class BridgeHAConfigImpl(override val haConnectionString: String, override val haPriority: Int = 10, override val haTopic: String = "/bridge/ha") : BridgeHAConfig

data class AuditServiceConfigurationImpl(override val loggingIntervalSec: Long) : AuditServiceConfiguration

data class FirewallConfigurationImpl(
        override val baseDirectory: Path,
        override val certificatesDirectory: Path = baseDirectory / "certificates",
        override val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        override val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        private val keyStorePassword: String,
        private val trustStorePassword: String,
        override val firewallMode: FirewallMode,
        override val networkParametersPath: Path,
        override val outboundConfig: BridgeOutboundConfigurationImpl?,
        override val inboundConfig: BridgeInboundConfigurationImpl?,
        override val bridgeInnerConfig: BridgeInnerConfigurationImpl?,
        override val floatOuterConfig: FloatOuterConfigurationImpl?,
        override val haConfig: BridgeHAConfigImpl?,
        override val enableAMQPPacketTrace: Boolean,
        override val artemisReconnectionIntervalMin: Int = 5000,
        override val artemisReconnectionIntervalMax: Int = 60000,
        override val politeShutdownPeriod: Int = 1000,
        override val p2pConfirmationWindowSize: Int = 1048576,
        override val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList(),
        override val auditServiceConfiguration: AuditServiceConfigurationImpl,
        override val healthCheckPhrase: String? = null,
        override val silencedIPs: Set<String> = emptySet(),
        override val revocationConfig: RevocationConfig) : FirewallConfiguration {
    init {
        when (firewallMode) {
            FirewallMode.SenderReceiver -> require(inboundConfig != null && outboundConfig != null) { "Missing required configuration" }
            FirewallMode.BridgeInner -> require(bridgeInnerConfig != null && outboundConfig != null) { "Missing required configuration" }
            FirewallMode.FloatOuter -> require(inboundConfig != null && floatOuterConfig != null) { "Missing required configuration" }
        }
    }

    private val p2pKeystorePath = sslKeystore
    // Due to Artemis limitations setting private key password same as key store password.
    private val p2pKeyStore = FileBasedCertificateStoreSupplier(p2pKeystorePath, keyStorePassword, entryPassword = keyStorePassword)
    private val p2pTrustStoreFilePath = trustStoreFile
    // Due to Artemis limitations setting private key password same as key store password.
    private val p2pTrustStore = FileBasedCertificateStoreSupplier(p2pTrustStoreFilePath, trustStorePassword, entryPassword = trustStorePassword)
    override val publicSSLConfiguration: MutualSslConfiguration = SslConfiguration.mutual(p2pKeyStore, p2pTrustStore)
}


