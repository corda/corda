/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.config

import com.typesafe.config.Config
import net.corda.bridge.services.api.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import java.nio.file.Path
import java.nio.file.Paths


fun Config.parseAsBridgeConfiguration(): BridgeConfiguration = parseAs<BridgeConfigurationImpl>()

data class BridgeSSLConfigurationImpl(override val keyStorePassword: String,
                                      override val trustStorePassword: String,
                                      override val certificatesDirectory: Path = Paths.get("certificates"),
                                      override val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
                                      override val trustStoreFile: Path = certificatesDirectory / "truststore.jks") : BridgeSSLConfiguration {
    constructor(config: NodeSSLConfiguration) : this(config.keyStorePassword, config.trustStorePassword, config.certificatesDirectory, config.sslKeystore, config.trustStoreFile)
}

data class BridgeOutboundConfigurationImpl(override val artemisBrokerAddress: NetworkHostAndPort,
                                           override val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                           override val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                           override val socksProxyConfig: SocksProxyConfig? = null) : BridgeOutboundConfiguration

data class BridgeInboundConfigurationImpl(override val listeningAddress: NetworkHostAndPort,
                                          override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : BridgeInboundConfiguration

data class FloatInnerConfigurationImpl(override val floatAddresses: List<NetworkHostAndPort>,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val customSSLConfiguration: BridgeSSLConfigurationImpl?,
                                       override val customFloatOuterSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatInnerConfiguration

data class FloatOuterConfigurationImpl(override val floatAddress: NetworkHostAndPort,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatOuterConfiguration

data class BridgeHAConfigImpl(override val haConnectionString: String, override val haPriority: Int = 10) : BridgeHAConfig

data class BridgeConfigurationImpl(
        override val baseDirectory: Path,
        override val certificatesDirectory: Path = baseDirectory / "certificates",
        override val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        override val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        override val keyStorePassword: String,
        override val trustStorePassword: String,
        override val bridgeMode: BridgeMode,
        override val networkParametersPath: Path,
        override val outboundConfig: BridgeOutboundConfigurationImpl?,
        override val inboundConfig: BridgeInboundConfigurationImpl?,
        override val floatInnerConfig: FloatInnerConfigurationImpl?,
        override val floatOuterConfig: FloatOuterConfigurationImpl?,
        override val haConfig: BridgeHAConfigImpl?,
        override val enableAMQPPacketTrace: Boolean,
        override val artemisReconnectionInterval: Int = 5000,
        override val politeShutdownPeriod: Int = 1000,
        override val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList()
) : BridgeConfiguration {
    init {
        if (bridgeMode == BridgeMode.SenderReceiver) {
            require(inboundConfig != null && outboundConfig != null) { "Missing required configuration" }
        } else if (bridgeMode == BridgeMode.FloatInner) {
            require(floatInnerConfig != null && outboundConfig != null) { "Missing required configuration" }
        } else if (bridgeMode == BridgeMode.FloatOuter) {
            require(inboundConfig != null && floatOuterConfig != null) { "Missing required configuration" }
        }
    }
}


