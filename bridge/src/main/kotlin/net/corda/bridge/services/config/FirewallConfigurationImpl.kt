package net.corda.bridge.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.bridge.FirewallCmdLineOptions
import net.corda.bridge.services.api.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import java.nio.file.Path

fun Config.parseAsFirewallConfiguration(): FirewallConfiguration {
    return try {
        parseAs<FirewallConfigurationImpl>()
    } catch (ex: UnknownConfigurationKeysException) {

        data class Version3BridgeConfigurationImpl(
                val baseDirectory: Path,
                val certificatesDirectory: Path = baseDirectory / "certificates",
                val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
                val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
                val crlCheckSoftFail: Boolean,
                val keyStorePassword: String,
                val trustStorePassword: String,
                val bridgeMode: FirewallMode,
                val networkParametersPath: Path,
                val outboundConfig: BridgeOutboundConfigurationImpl?,
                val inboundConfig: BridgeInboundConfigurationImpl?,
                val bridgeInnerConfig: BridgeInnerConfigurationImpl?,
                val floatOuterConfig: FloatOuterConfigurationImpl?,
                val haConfig: BridgeHAConfigImpl?,
                val enableAMQPPacketTrace: Boolean,
                val artemisReconnectionIntervalMin: Int = 5000,
                val artemisReconnectionIntervalMax: Int = 60000,
                val politeShutdownPeriod: Int = 1000,
                val p2pConfirmationWindowSize: Int = 1048576,
                val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList(),
                val healthCheckPhrase: String? = null
        ) {
            fun toConfig(): FirewallConfiguration {
                return FirewallConfigurationImpl(
                        baseDirectory,
                        certificatesDirectory,
                        sslKeystore,
                        trustStoreFile,
                        crlCheckSoftFail,
                        keyStorePassword,
                        trustStorePassword,
                        bridgeMode,
                        networkParametersPath,
                        outboundConfig,
                        inboundConfig,
                        bridgeInnerConfig,
                        floatOuterConfig,
                        haConfig,
                        enableAMQPPacketTrace,
                        artemisReconnectionIntervalMin,
                        artemisReconnectionIntervalMax,
                        politeShutdownPeriod,
                        p2pConfirmationWindowSize,
                        whitelistedHeaders,
                        AuditServiceConfigurationImpl(60), // Same as `firewalldefault.conf`, new in v4
                        healthCheckPhrase
                )
            }
        }

        // Note: "Ignore" is needed to disregard any default properties from "firewalldefault.conf" that are not applicable to V3 configuration
        val oldStyleConfig = parseAs<Version3BridgeConfigurationImpl>(UnknownConfigKeysPolicy.IGNORE::handle)
        val newStyleConfig = oldStyleConfig.toConfig()

        val configAsString = newStyleConfig.toConfig().root().render(ConfigRenderOptions.defaults())
        FirewallCmdLineOptions.logger.warn("Old style config used. To avoid seeing this warning in the future, please upgrade to new style. " +
                "New style config will look as follows:\n$configAsString")
        newStyleConfig
    }
}

data class BridgeSSLConfigurationImpl(private val sslKeystore: Path,
                                      private val keyStorePassword: String,
                                      private val keyStorePrivateKeyPassword: String = keyStorePassword,
                                      private val trustStoreFile: Path,
                                      private val trustStorePassword: String,
                                      private val crlCheckSoftFail: Boolean,
                                      override val useOpenSsl: Boolean = false) : BridgeSSLConfiguration {

    override val keyStore = FileBasedCertificateStoreSupplier(sslKeystore, keyStorePassword, keyStorePrivateKeyPassword)
    // Trust store does not and should not contain any keys therefore keys password for it should not matter
    override val trustStore = FileBasedCertificateStoreSupplier(trustStoreFile, trustStorePassword, trustStorePassword)
}

data class BridgeOutboundConfigurationImpl(override val artemisBrokerAddress: NetworkHostAndPort,
                                           override val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                           override val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                           override val socksProxyConfig: SocksProxyConfig? = null) : BridgeOutboundConfiguration

data class BridgeInboundConfigurationImpl(override val listeningAddress: NetworkHostAndPort,
                                          override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : BridgeInboundConfiguration

data class BridgeInnerConfigurationImpl(override val floatAddresses: List<NetworkHostAndPort>,
                                        override val expectedCertificateSubject: CordaX500Name,
                                        override val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                        override val customFloatOuterSSLConfiguration: BridgeSSLConfigurationImpl?,
                                        override val enableSNI: Boolean = true) : BridgeInnerConfiguration

data class FloatOuterConfigurationImpl(override val floatAddress: NetworkHostAndPort,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatOuterConfiguration

data class BridgeHAConfigImpl(override val haConnectionString: String, override val haPriority: Int = 10, override val haTopic: String = "/bridge/ha") : BridgeHAConfig

data class AuditServiceConfigurationImpl(override val loggingIntervalSec: Long) : AuditServiceConfiguration

data class FirewallConfigurationImpl(
        override val baseDirectory: Path,
        private val certificatesDirectory: Path = baseDirectory / "certificates",
        private val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        private val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        override val crlCheckSoftFail: Boolean,
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
        override val healthCheckPhrase: String? = null) : FirewallConfiguration {
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
    override val p2pSslOptions: MutualSslConfiguration = SslConfiguration.mutual(p2pKeyStore, p2pTrustStore)
}


