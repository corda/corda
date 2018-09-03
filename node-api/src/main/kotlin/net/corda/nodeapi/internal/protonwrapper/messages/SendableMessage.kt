package net.corda.nodeapi.internal.protonwrapper.messages

import net.corda.core.concurrent.CordaFuture

/**
 * An extension of ApplicationMessage to allow completion signalling.
 */
interface SendableMessage : ApplicationMessage {
    val onComplete: CordaFuture<MessageStatus>
}