package net.corda.bridge.services.config.internal

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.FirewallMode
import net.corda.bridge.services.config.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import java.nio.file.Path

internal interface ModernConfigurationAdaptor<T> {
    fun toConfig(): T
}

// Version 3

// Previously `proxyConfig` was known as `socksProxyConfig` and `artemisSSLConfiguration` as `customSSLConfiguration`
internal data class Version3BridgeOutboundConfigurationImpl(val artemisBrokerAddress: NetworkHostAndPort,
                                                            val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                                            val customSSLConfiguration: Version3BridgeSSLConfigurationImpl?,
                                                            val socksProxyConfig: ProxyConfig? = null) : ModernConfigurationAdaptor<BridgeOutboundConfigurationImpl> {
    override fun toConfig(): BridgeOutboundConfigurationImpl {
        return BridgeOutboundConfigurationImpl(artemisBrokerAddress, alternateArtemisBrokerAddresses,
                customSSLConfiguration?.toConfig(), socksProxyConfig)
    }
}

// Previously `tunnelSSLConfiguration` was known as `customSSLConfiguration`
internal data class Version3BridgeInnerConfigurationImpl(val floatAddresses: List<NetworkHostAndPort>,
                                                         val expectedCertificateSubject: CordaX500Name,
                                                         val customSSLConfiguration: Version3BridgeSSLConfigurationImpl?,
                                                         val enableSNI: Boolean = true) : ModernConfigurationAdaptor<BridgeInnerConfigurationImpl> {
    override fun toConfig(): BridgeInnerConfigurationImpl {
        return BridgeInnerConfigurationImpl(floatAddresses, expectedCertificateSubject, customSSLConfiguration?.toConfig(), enableSNI)
    }
}

// Previously `tunnelSSLConfiguration` was known as `customSSLConfiguration`
internal data class Version3FloatOuterConfigurationImpl(val floatAddress: NetworkHostAndPort,
                                               val expectedCertificateSubject: CordaX500Name,
                                               val customSSLConfiguration: Version3BridgeSSLConfigurationImpl?) : ModernConfigurationAdaptor<FloatOuterConfigurationImpl> {
    override fun toConfig(): FloatOuterConfigurationImpl {
        return FloatOuterConfigurationImpl(floatAddress, expectedCertificateSubject, customSSLConfiguration?.toConfig())
    }
}

data class Version3BridgeSSLConfigurationImpl(private val sslKeystore: Path,
                                              private val keyStorePassword: String,
                                              private val keyStorePrivateKeyPassword: String = keyStorePassword,
                                              private val trustStoreFile: Path,
                                              private val trustStorePassword: String,
                                              private val crlCheckSoftFail: Boolean,
                                              val useOpenSsl: Boolean = false) : ModernConfigurationAdaptor<BridgeSSLConfigurationImpl> {
    override fun toConfig(): BridgeSSLConfigurationImpl {
        return BridgeSSLConfigurationImpl(sslKeystore, keyStorePassword, keyStorePrivateKeyPassword, trustStoreFile, trustStorePassword, crlCheckSoftFail.toRevocationConfig(), useOpenSsl)
    }
}

data class Version3BridgeInboundConfigurationImpl(private val listeningAddress: NetworkHostAndPort,
                                                  private val customSSLConfiguration: Version3BridgeSSLConfigurationImpl?) : ModernConfigurationAdaptor<BridgeInboundConfigurationImpl> {
    override fun toConfig(): BridgeInboundConfigurationImpl {
        return BridgeInboundConfigurationImpl(listeningAddress, customSSLConfiguration?.toConfig())
    }
}

internal data class Version3BridgeConfigurationImpl(
        val baseDirectory: Path,
        val certificatesDirectory: Path = baseDirectory / "certificates",
        val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        val crlCheckSoftFail: Boolean = true,
        val keyStorePassword: String,
        val trustStorePassword: String,
        val bridgeMode: FirewallMode,
        val networkParametersPath: Path,
        val outboundConfig: Version3BridgeOutboundConfigurationImpl?,
        val inboundConfig: Version3BridgeInboundConfigurationImpl?,
        val bridgeInnerConfig: Version3BridgeInnerConfigurationImpl?,
        val floatOuterConfig: Version3FloatOuterConfigurationImpl?,
        val haConfig: BridgeHAConfigImpl?,
        val enableAMQPPacketTrace: Boolean,
        val artemisReconnectionIntervalMin: Int = 5000,
        val artemisReconnectionIntervalMax: Int = 60000,
        val politeShutdownPeriod: Int = 1000,
        val p2pConfirmationWindowSize: Int = 1048576,
        val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList(),
        val healthCheckPhrase: String? = null,
        val silencedIPs: Set<String> = emptySet()
) : ModernConfigurationAdaptor<FirewallConfiguration> {
    override fun toConfig(): FirewallConfiguration {
        return FirewallConfigurationImpl(
                baseDirectory,
                certificatesDirectory,
                sslKeystore,
                trustStoreFile,
                keyStorePassword,
                trustStorePassword,
                bridgeMode,
                networkParametersPath,
                outboundConfig?.toConfig(),
                inboundConfig?.toConfig(),
                bridgeInnerConfig?.toConfig(),
                floatOuterConfig?.toConfig(),
                haConfig,
                enableAMQPPacketTrace,
                artemisReconnectionIntervalMin,
                artemisReconnectionIntervalMax,
                politeShutdownPeriod,
                p2pConfirmationWindowSize,
                whitelistedHeaders,
                AuditServiceConfigurationImpl(60), // Same as `firewalldefault.conf`, new in v4
                healthCheckPhrase,
                silencedIPs,
                null,
                null,
                crlCheckSoftFail.toRevocationConfig()
        )
    }
}

// Version 4

data class Version4BridgeOutboundConfigurationImpl(private val artemisBrokerAddress: NetworkHostAndPort,
                                                   private val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                                   private val artemisSSLConfiguration: Version3BridgeSSLConfigurationImpl?,
                                                   private val proxyConfig: ProxyConfig? = null) : ModernConfigurationAdaptor<BridgeOutboundConfigurationImpl> {
    override fun toConfig(): BridgeOutboundConfigurationImpl {
        return BridgeOutboundConfigurationImpl(artemisBrokerAddress, alternateArtemisBrokerAddresses, artemisSSLConfiguration?.toConfig(), proxyConfig)
    }
}

data class Version4BridgeInnerConfigurationImpl(private val floatAddresses: List<NetworkHostAndPort>,
                                                private val expectedCertificateSubject: CordaX500Name,
                                                private val tunnelSSLConfiguration: Version3BridgeSSLConfigurationImpl?,
                                                private val enableSNI: Boolean = true) : ModernConfigurationAdaptor<BridgeInnerConfigurationImpl> {
    override fun toConfig(): BridgeInnerConfigurationImpl {
        return BridgeInnerConfigurationImpl(floatAddresses, expectedCertificateSubject, tunnelSSLConfiguration?.toConfig(), enableSNI)
    }
}

data class Version4FloatOuterConfigurationImpl(private val floatAddress: NetworkHostAndPort,
                                               private val expectedCertificateSubject: CordaX500Name,
                                               private val tunnelSSLConfiguration: Version3BridgeSSLConfigurationImpl?) : ModernConfigurationAdaptor<FloatOuterConfigurationImpl> {
    override fun toConfig(): FloatOuterConfigurationImpl {
        return FloatOuterConfigurationImpl(floatAddress, expectedCertificateSubject, tunnelSSLConfiguration?.toConfig())
    }
}

data class Version4FirewallConfiguration(
        val baseDirectory: Path,
        val certificatesDirectory: Path = baseDirectory / "certificates",
        val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        val crlCheckSoftFail: Boolean = true,
        private val keyStorePassword: String,
        private val trustStorePassword: String,
        val firewallMode: FirewallMode,
        val networkParametersPath: Path,
        val outboundConfig: Version4BridgeOutboundConfigurationImpl?,
        val inboundConfig: Version3BridgeInboundConfigurationImpl?,
        val bridgeInnerConfig: Version4BridgeInnerConfigurationImpl?,
        val floatOuterConfig: Version4FloatOuterConfigurationImpl?,
        val haConfig: BridgeHAConfigImpl?,
        val enableAMQPPacketTrace: Boolean,
        val artemisReconnectionIntervalMin: Int = 5000,
        val artemisReconnectionIntervalMax: Int = 60000,
        val politeShutdownPeriod: Int = 1000,
        val p2pConfirmationWindowSize: Int = 1048576,
        val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList(),
        val auditServiceConfiguration: AuditServiceConfigurationImpl,
        val healthCheckPhrase: String? = null,
        val silencedIPs: Set<String> = emptySet()) : ModernConfigurationAdaptor<FirewallConfiguration> {

    override fun toConfig(): FirewallConfiguration {
        return FirewallConfigurationImpl(
                baseDirectory,
                certificatesDirectory,
                sslKeystore,
                trustStoreFile,
                keyStorePassword,
                trustStorePassword,
                firewallMode,
                networkParametersPath,
                outboundConfig?.toConfig(),
                inboundConfig?.toConfig(),
                bridgeInnerConfig?.toConfig(),
                floatOuterConfig?.toConfig(),
                haConfig,
                enableAMQPPacketTrace,
                artemisReconnectionIntervalMin,
                artemisReconnectionIntervalMax,
                politeShutdownPeriod,
                p2pConfirmationWindowSize,
                whitelistedHeaders,
                AuditServiceConfigurationImpl(60), // Same as `firewalldefault.conf`, new in v4
                healthCheckPhrase,
                silencedIPs,
                null,
                null,
                crlCheckSoftFail.toRevocationConfig())
    }
}