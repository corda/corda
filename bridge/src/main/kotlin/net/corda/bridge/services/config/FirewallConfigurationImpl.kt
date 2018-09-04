package net.corda.bridge.services.config

import com.typesafe.config.Config
import net.corda.bridge.services.api.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.SslConfiguration
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import java.nio.file.Path

fun Config.parseAsFirewallConfiguration(): FirewallConfiguration = parseAs<FirewallConfigurationImpl>()

data class BridgeSSLConfigurationImpl(private val sslKeystore: Path,
                                      private val keyStorePassword: String,
                                      private val trustStoreFile: Path,
                                      private val trustStorePassword: String,
                                      private val crlCheckSoftFail: Boolean) : BridgeSSLConfiguration {

    override val keyStore = FileBasedCertificateStoreSupplier(sslKeystore, keyStorePassword)
    override val trustStore = FileBasedCertificateStoreSupplier(trustStoreFile, trustStorePassword)
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
                                        override val customFloatOuterSSLConfiguration: BridgeSSLConfigurationImpl?) : BridgeInnerConfiguration

data class FloatOuterConfigurationImpl(override val floatAddress: NetworkHostAndPort,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val customSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatOuterConfiguration

data class BridgeHAConfigImpl(override val haConnectionString: String, override val haPriority: Int = 10, override val haTopic: String = "/bridge/ha") : BridgeHAConfig

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
        override val whitelistedHeaders: List<String> = ArtemisMessagingComponent.Companion.P2PMessagingHeaders.whitelistedHeaders.toList()
) : FirewallConfiguration {
    init {
        if (firewallMode == FirewallMode.SenderReceiver) {
            require(inboundConfig != null && outboundConfig != null) { "Missing required configuration" }
        } else if (firewallMode == FirewallMode.BridgeInner) {
            require(bridgeInnerConfig != null && outboundConfig != null) { "Missing required configuration" }
        } else if (firewallMode == FirewallMode.FloatOuter) {
            require(inboundConfig != null && floatOuterConfig != null) { "Missing required configuration" }
        }
    }

    private val p2pKeystorePath = sslKeystore
    private val p2pKeyStore = FileBasedCertificateStoreSupplier(p2pKeystorePath, keyStorePassword)
    private val p2pTrustStoreFilePath = trustStoreFile
    private val p2pTrustStore = FileBasedCertificateStoreSupplier(p2pTrustStoreFilePath, trustStorePassword)
    override val p2pSslOptions: MutualSslConfiguration = SslConfiguration.mutual(p2pKeyStore, p2pTrustStore)
}


