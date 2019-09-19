package net.corda.bridge.services.config

import net.corda.bridge.services.api.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfigImpl
import java.nio.file.Path

data class BridgeSSLConfigurationImpl(private val sslKeystore: Path,
                                      private val keyStorePassword: String,
                                      private val keyStorePrivateKeyPassword: String = keyStorePassword,
                                      private val trustStoreFile: Path,
                                      private val trustStorePassword: String,
                                      override val useOpenSsl: Boolean = false) : BridgeSSLConfiguration {

    override val keyStore = FileBasedCertificateStoreSupplier(sslKeystore, keyStorePassword, keyStorePrivateKeyPassword)
    // Trust store does not and should not contain any keys therefore keys password for it should not matter
    override val trustStore = FileBasedCertificateStoreSupplier(trustStoreFile, trustStorePassword, trustStorePassword)
}

private const val ZERO_ADDRESS = "0.0.0.0"

data class BridgeOutboundConfigurationImpl(override val artemisBrokerAddress: NetworkHostAndPort,
                                           override val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>,
                                           override val artemisSSLConfiguration: BridgeSSLConfigurationImpl?,
                                           override val proxyConfig: ProxyConfig? = null) : BridgeOutboundConfiguration {

    init {
        require(artemisBrokerAddress.host != ZERO_ADDRESS) { "$ZERO_ADDRESS is not allowed as artemisBrokerAddress" }
        require(alternateArtemisBrokerAddresses.all { it.host != ZERO_ADDRESS }) { "$ZERO_ADDRESS is not allowed in alternateArtemisBrokerAddresses" }
    }
}

data class BridgeInboundConfigurationImpl(override val listeningAddress: NetworkHostAndPort) : BridgeInboundConfiguration

data class BridgeInnerConfigurationImpl(override val floatAddresses: List<NetworkHostAndPort>,
                                        override val expectedCertificateSubject: CordaX500Name,
                                        override val tunnelSSLConfiguration: BridgeSSLConfigurationImpl?,
                                        override val enableSNI: Boolean = true) : BridgeInnerConfiguration {
    init {
        require(floatAddresses.all { it.host != ZERO_ADDRESS }) { "$ZERO_ADDRESS is not allowed in floatAddresses" }
    }
}

data class FloatOuterConfigurationImpl(override val floatAddress: NetworkHostAndPort,
                                       override val expectedCertificateSubject: CordaX500Name,
                                       override val tunnelSSLConfiguration: BridgeSSLConfigurationImpl?) : FloatOuterConfiguration

data class BridgeHAConfigImpl(override val haConnectionString: String, override val haPriority: Int = 10, override val haTopic: String = "/bridge/ha") : BridgeHAConfig

// Intentional local implementation of CryptoServiceConfig interface
data class CryptoServiceConfigImpl(override val name: SupportedCryptoServices, override val conf: Path?) : CryptoServiceConfig

data class AuditServiceConfigurationImpl(override val loggingIntervalSec: Long) : AuditServiceConfiguration

data class FirewallConfigurationImpl(
        override val baseDirectory: Path,
        override val certificatesDirectory: Path = baseDirectory / "certificates",
        override val sslKeystore: Path = certificatesDirectory / "sslkeystore.jks",
        override val trustStoreFile: Path = certificatesDirectory / "truststore.jks",
        private val keyStorePassword: String,
        private val trustStorePassword: String,
        override val firewallMode: FirewallMode,
        override val networkParametersPath: Path? = null,
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
        override val p2pTlsSigningCryptoServiceConfig: CryptoServiceConfigImpl?,
        override val tunnelingCryptoServiceConfig: CryptoServiceConfigImpl?,
        override val artemisCryptoServiceConfig: CryptoServiceConfigImpl?,
        private val revocationConfig: RevocationConfig?,
        override val sslHandshakeTimeout: Long = DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS,
        override val useProxyForCrls: Boolean = false
        ) : FirewallConfiguration {
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

    override val revocationConfigSection: RevocationConfig
        get() = revocationConfig ?: when (firewallMode) {
            FirewallMode.FloatOuter -> RevocationConfigImpl(RevocationConfig.Mode.EXTERNAL_SOURCE)
            else -> RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL)
        }
}


