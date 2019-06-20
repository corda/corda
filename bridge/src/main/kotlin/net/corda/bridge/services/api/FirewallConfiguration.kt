package net.corda.bridge.services.api

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.protonwrapper.netty.ProxyConfig
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import java.nio.file.Path

enum class FirewallMode {
    /**
     * The Bridge/Float is run as a single process with both AMQP sending and receiving functionality.
     */
    SenderReceiver,
    /**
     * Runs only the trusted bridge side of the system, which has direct TLS access to Artemis.
     * The components handles all outgoing aspects of AMQP bridges directly.
     * The inbound messages are initially received onto a different [FloatOuter] process and a
     * separate AMQP tunnel is used to ship back the inbound data to this [BridgeInner] process.
     */
    BridgeInner,
    /**
     * A minimal process designed to be run inside a DMZ, which acts an AMQP receiver of inbound peer messages.
     * The component carries out basic validation of the TLS sources and AMQP packets, before forwarding to the [BridgeInner].
     * No keys are stored on disk for the component, but must instead be provisioned from the [BridgeInner] using a
     * separate AMQP link initiated from the [BridgeInner] to the [FloatOuter].
     */
    FloatOuter
}

interface BridgeSSLConfiguration : MutualSslConfiguration {
val revocationConfig: RevocationConfig
}
/**
 * Details of the local Artemis broker.
 * Required in SenderReceiver and BridgeInner modes.
 */
interface BridgeOutboundConfiguration {
    val artemisBrokerAddress: NetworkHostAndPort
    val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>
    // Allows override of [KeyStore] details for the artemis connection, otherwise the general top level details are used.
    val artemisSSLConfiguration: BridgeSSLConfiguration?
    // Allows use of a SOCKS 4/5 proxy
    val proxyConfig: ProxyConfig?
}

/**
 * Details of the inbound socket binding address, which should be where external peers
 * using the node's network map advertised data should route links and directly terminate their TLS connections.
 * This configuration is required in SenderReceiver and FloatOuter modes.
 */
interface BridgeInboundConfiguration {
    val listeningAddress: NetworkHostAndPort
    // Allows override of [KeyStore] details for the AMQP listener port, otherwise the general top level details are used.
    val customSSLConfiguration: BridgeSSLConfiguration?
}

/**
 * Details of the target control ports of available [FirewallMode.FloatOuter] processes from the perspective of the [FirewallMode.BridgeInner] process.
 * Required for [FirewallMode.BridgeInner] mode.
 */
interface BridgeInnerConfiguration {
    val floatAddresses: List<NetworkHostAndPort>
    val expectedCertificateSubject: CordaX500Name
    // Allows override of [KeyStore] details for the control port, otherwise the general top level details are used.
    // Used for connection to Float in DMZ
    val tunnelSSLConfiguration: BridgeSSLConfiguration?
    val enableSNI: Boolean
}

interface BridgeHAConfig {
    val haConnectionString: String
    val haPriority: Int
    val haTopic: String
}

/**
 * Details of the listening port for a [FirewallMode.FloatOuter] process and of the certificate that the [FirewallMode.BridgeInner] should present.
 * Required for [FirewallMode.FloatOuter] mode.
 */
interface FloatOuterConfiguration {
    val floatAddress: NetworkHostAndPort
    val expectedCertificateSubject: CordaX500Name
    // Allows override of [KeyStore] details for the control port, otherwise the general top level details are used.
    val tunnelSSLConfiguration: BridgeSSLConfiguration?
}

interface AuditServiceConfiguration {
    val loggingIntervalSec: Long
}

interface FirewallConfiguration {
    val baseDirectory: Path
    val certificatesDirectory: Path
    val sslKeystore: Path
    val trustStoreFile: Path
    val firewallMode: FirewallMode
    val outboundConfig: BridgeOutboundConfiguration?
    val inboundConfig: BridgeInboundConfiguration?
    val bridgeInnerConfig: BridgeInnerConfiguration?
    val floatOuterConfig: FloatOuterConfiguration?
    val haConfig: BridgeHAConfig?
    val networkParametersPath: Path
    val enableAMQPPacketTrace: Boolean
    // Initial reconnect interval for link to artemis after [artemisReconnectionIntervalMin] ms the default value is 5000 ms.
    val artemisReconnectionIntervalMin: Int
    // Slowest Artemis reconnect interval after exponential backoff applied. The default value is 60000 ms.
    val artemisReconnectionIntervalMax: Int
    // The period to wait for clean shutdown of remote components
    // e.g links to the Float Outer, or Artemis sessions, before the process continues shutting down anyway.
    // Default value is 1000 ms.
    val politeShutdownPeriod: Int
    // p2pConfirmationWindowSize determines the number of bytes buffered by the broker before flushing to disk and
    // acking the triggering send. Setting this to -1 causes session commits to immediately return, potentially
    // causing blowup in the broker if the rate of sends exceeds the broker's flush rate. Note also that this window
    // causes send latency to be around [brokerConnectionTtlCheckInterval] if the window isn't saturated.
    // This is relevant to bridges, because we push messages into the inbox and use the async acknowledgement responses to reply to sender.
    val p2pConfirmationWindowSize: Int
    val whitelistedHeaders: List<String>
    val publicSSLConfiguration: MutualSslConfiguration
    val publicCryptoServiceConfig: CryptoServiceConfig?
    val tunnelingCryptoServiceConfig: CryptoServiceConfig? // Location for the cryptoService conf file.
    val artemisCryptoServiceConfig: CryptoServiceConfig?

    val auditServiceConfiguration: AuditServiceConfiguration
    // An optional Health Check Phrase which if passed through the channel will cause AMQP Server to echo it back instead of doing normal pipeline processing
    val healthCheckPhrase: String?
    val silencedIPs: Set<String>

    val sslHandshakeTimeout:Long
    val revocationConfig: RevocationConfig
}

interface CryptoServiceConfig {
    val name: SupportedCryptoServices
    val conf: Path?
}