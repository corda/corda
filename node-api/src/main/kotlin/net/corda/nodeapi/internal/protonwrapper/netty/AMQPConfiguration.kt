package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.DEFAULT_SSL_HANDSHAKE_TIMEOUT
import java.time.Duration

interface AMQPConfiguration {
    /**
     * SASL User name presented during protocol handshake. No SASL login if NULL.
     * For legacy interoperability with Artemis authorisation we typically require this to be "PEER_USER"
     */
    val userName: String?
        get() = ArtemisMessagingComponent.PEER_USER

    /**
     * SASL plain text password presented during protocol handshake. No SASL login if NULL.
     * For legacy interoperability with Artemis authorisation we typically require this to be "PEER_USER"
     */
    val password: String?
        get() = ArtemisMessagingComponent.PEER_USER

    /**
     * The key store used for TLS connections
     */
    val keyStore: CertificateStore

    /**
     * The trust root key store to validate the peer certificates against
     */
    val trustStore: CertificateStore

    /**
     * Control how CRL check will be performed.
     */
    val revocationConfig: RevocationConfig
        get() = RevocationConfigImpl(RevocationConfig.Mode.SOFT_FAIL)

    /**
     * Enables full debug tracing of all netty and AMQP level packets. This logs aat very high volume and is only for developers.
     */
    val trace: Boolean
        get() = false

    /**
     * The maximum allowed size for packets, which will be dropped ahead of send. In future may also be enforced on receive,
     * but currently that is deferred to Artemis and the bridge code.
     */
    val maxMessageSize: Int

    val proxyConfig: ProxyConfig?
        get() = null

    val sourceX500Name: String?
        get() = null

    /**
     * Whether to use the tcnative open/boring SSL provider or the default Java SSL provider
     */
    val useOpenSsl: Boolean
        get() = false

    val sslHandshakeTimeout: Duration
        get() = DEFAULT_SSL_HANDSHAKE_TIMEOUT // Aligned with sun.security.provider.certpath.URICertStore.DEFAULT_CRL_CONNECT_TIMEOUT

    /**
     * An optional Health Check Phrase which if passed through the channel will cause AMQP Server to echo it back instead of doing normal pipeline processing
     */
    val healthCheckPhrase: String?
        get() = null

    /**
     * An optional set of IPv4/IPv6 remote address strings which will be compared to the remote address of inbound connections and these will only log at TRACE level
     */
    val silencedIPs: Set<String>
        get() = emptySet()

    val enableSNI: Boolean
        get() = true
}

