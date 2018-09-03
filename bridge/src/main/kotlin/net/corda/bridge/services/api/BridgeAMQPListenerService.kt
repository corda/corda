package net.corda.bridge.services.api

import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.netty.ConnectionChange
import rx.Observable
import java.security.KeyStore

/**
 * This service when activated via [provisionKeysAndActivate] installs an AMQP listening socket,
 * which listens on the port specified in the [FirewallConfiguration.inboundConfig] section.
 * The service technically runs inside the 'float' portion of the bridge, so that it can be run remotely inside the DMZ.
 * As a result it reports as active, whilst not actually listening. Only when the TLS [KeyStore]s are passed to it
 * does the service become [running].
 */
interface BridgeAMQPListenerService : ServiceLifecycleSupport {
    /**
     * Passes in the [KeyStore]s containing the TLS keys and certificates. This data is only to be held in memory
     * and will be wiped on close.
     */
    fun provisionKeysAndActivate(keyStoreBytes: ByteArray,
                                 keyStorePassword: CharArray,
                                 keyStorePrivateKeyPassword: CharArray,
                                 trustStoreBytes: ByteArray,
                                 trustStorePassword: CharArray)

    /**
     * Stop listening on the socket and cleanup any private data/keys.
     */
    fun wipeKeysAndDeactivate()

    /**
     * If the service is [running] the AMQP listener is active.
     */
    val running: Boolean

    /**
     * Incoming AMQP packets from remote peers are available on this [Observable].
     */
    val onReceive: Observable<ReceivedMessage>

    /**
     * Any connection, disconnection, or authentication failure is available on this [Observable].
     */
    val onConnection: Observable<ConnectionChange>
}