package net.corda.bridge.services.config.internal

import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.FirewallMode
import net.corda.bridge.services.config.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import net.corda.nodeapi.internal.protonwrapper.netty.toRevocationConfig
import java.nio.file.Path

// Previously `proxyConfig` was known as `socksProxyConfig` and `artemisSSLConfiguration` as `customSSLConfiguration`
internal data class Version3BridgeOutboundConfigurationImpl(val artemisBrokerAddress: NetworkHostAndPort,
                                                            val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                                            val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                                            val socksProxyConfig: ProxyConfig? = null) {
    fun toConfig(): BridgeOutboundConfigurationImpl {
        return BridgeOutboundConfigurationImpl(artemisBrokerAddress, alternateArtemisBrokerAddresses,
                customSSLConfiguration, socksProxyConfig)
    }
}

// Previously `tunnelSSLConfiguration` was known as `customSSLConfiguration`
internal data class Version3BridgeInnerConfigurationImpl(val floatAddresses: List<NetworkHostAndPort>,
                                                         val expectedCertificateSubject: CordaX500Name,
                                                         val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                                         val enableSNI: Boolean = true) {
    fun toConfig(): BridgeInnerConfigurationImpl {
        return BridgeInnerConfigurationImpl(floatAddresses, expectedCertificateSubject, customSSLConfiguration, enableSNI)
    }
}

// Previously `tunnelSSLConfiguration` was known as `customSSLConfiguration`
internal data class Version3FloatOuterConfigurationImpl(val floatAddress: NetworkHostAndPort,
                                               val expectedCertificateSubject: CordaX500Name,
                                               val customSSLConfiguration: BridgeSSLConfigurationImpl?) {
    fun toConfig(): FloatOuterConfigurationImpl {
        return FloatOuterConfigurationImpl(floatAddress, expectedCertificateSubject, customSSLConfiguration)
    }
}

internal data class Version3BridgeConfigurationImpl(
        val baseDirectory: Path,
        val certificatesDirectory: Path = baseDirectory / "certificates",
        val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        val crlCheckSoftFail: Boolean,
        val keyStorePassword: String,
        val trustStorePassword: String,
        val bridgeMode: FirewallMode,
        val networkParametersPath: Path,
        val outboundConfig: Version3BridgeOutboundConfigurationImpl?,
        val inboundConfig: BridgeInboundConfigurationImpl?,
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
) {
    fun toConfig(): FirewallConfiguration {
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
                inboundConfig,
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
                crlCheckSoftFail.toRevocationConfig()
        )
    }
}