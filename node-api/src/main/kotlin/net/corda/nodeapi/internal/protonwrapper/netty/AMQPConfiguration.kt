package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.nodeapi.internal.ArtemisMessagingComponent
import java.security.KeyStore

interface AMQPConfiguration {
    /**
     * SASL User name presented during protocol handshake. No SASL login if NULL.
     * For legacy interoperability with Artemis authorisation we typically require this to be "PEER_USER"
     */
    @JvmDefault
    val userName: String?
        get() = ArtemisMessagingComponent.PEER_USER

    /**
     * SASL plain text password presented during protocol handshake. No SASL login if NULL.
     * For legacy interoperability with Artemis authorisation we typically require this to be "PEER_USER"
     */
    @JvmDefault
    val password: String?
        get() = ArtemisMessagingComponent.PEER_USER

    /**
     * The keystore used for TLS connections
     */
    val keyStore: KeyStore

    /**
     * Password used to unlock TLS private keys in the KeyStore.
     */
    val keyStorePrivateKeyPassword: CharArray

    /**
     * The trust root KeyStore to validate the peer certificates against
     */
    val trustStore: KeyStore

    /**
     * Setting crlCheckSoftFail to true allows certificate paths where some leaf certificates do not contain cRLDistributionPoints
     * and also allows validation to continue if the CRL distribution server is not contactable.
     */
    @JvmDefault
    val crlCheckSoftFail: Boolean
        get() = true

    /**
     * Enables full debug tracing of all netty and AMQP level packets. This logs aat very high volume and is only for developers.
     */
    @JvmDefault
    val trace: Boolean
        get() = false

    /**
     * The maximum allowed size for packets, which will be dropped ahead of send. In future may also be enforced on receive,
     * but currently that is deferred to Artemis and the bridge code.
     */
    val maxMessageSize: Int
}

