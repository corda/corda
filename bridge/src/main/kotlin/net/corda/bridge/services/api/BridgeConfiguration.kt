/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.api

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyConfig
import java.nio.file.Path

enum class BridgeMode {
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

interface BridgeSSLConfiguration : SSLConfiguration {
    override val keyStorePassword: String
    override val trustStorePassword: String
    override val sslKeystore: Path
    override val trustStoreFile: Path
}


/**
 * Details of the local Artemis broker.
 * Required in SenderReceiver and BridgeInner modes.
 */
interface BridgeOutboundConfiguration {
    val artemisBrokerAddress: NetworkHostAndPort
    val alternateArtemisBrokerAddresses: List<NetworkHostAndPort>
    // Allows override of [KeyStore] details for the artemis connection, otherwise the general top level details are used.
    val customSSLConfiguration: BridgeSSLConfiguration?
    // Allows use of a SOCKS 4/5 proxy
    val socksProxyConfig: SocksProxyConfig?
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
 * Details of the target control ports of available [BridgeMode.FloatOuter] processes from the perspective of the [BridgeMode.BridgeInner] process.
 * Required for [BridgeMode.BridgeInner] mode.
 */
interface BridgeInnerConfiguration {
    val floatAddresses: List<NetworkHostAndPort>
    val expectedCertificateSubject: CordaX500Name
    // Allows override of [KeyStore] details for the control port, otherwise the general top level details are used.
    // Used for connection to Float in DMZ
    val customSSLConfiguration: BridgeSSLConfiguration?
    // The SSL keystores to provision into the Float in DMZ
    val customFloatOuterSSLConfiguration: BridgeSSLConfiguration?
}

interface BridgeHAConfig {
    val haConnectionString: String
    val haPriority: Int
}

/**
 * Details of the listening port for a [BridgeMode.FloatOuter] process and of the certificate that the [BridgeMode.BridgeInner] should present.
 * Required for [BridgeMode.FloatOuter] mode.
 */
interface FloatOuterConfiguration {
    val floatAddress: NetworkHostAndPort
    val expectedCertificateSubject: CordaX500Name
    // Allows override of [KeyStore] details for the control port, otherwise the general top level details are used.
    val customSSLConfiguration: BridgeSSLConfiguration?
}

interface BridgeConfiguration : NodeSSLConfiguration {
    val bridgeMode: BridgeMode
    val outboundConfig: BridgeOutboundConfiguration?
    val inboundConfig: BridgeInboundConfiguration?
    val bridgeInnerConfig: BridgeInnerConfiguration?
    val floatOuterConfig: FloatOuterConfiguration?
    val haConfig: BridgeHAConfig?
    val networkParametersPath: Path
    val enableAMQPPacketTrace: Boolean
    // Reconnect to artemis after [artemisReconnectionInterval] ms the default value is 5000 ms.
    val artemisReconnectionInterval: Int
    // The period to wait for clean shutdown of remote components
    // e.g links to the Float Outer, or Artemis sessions, before the process continues shutting down anyway.
    // Default value is 1000 ms.
    val politeShutdownPeriod: Int
    val whitelistedHeaders: List<String>
}