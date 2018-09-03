package net.corda.nodeapi.internal.protonwrapper.messages

import net.corda.core.utilities.NetworkHostAndPort

/**
 * Represents a common interface for both sendable and received application messages.
 */
interface ApplicationMessage {
    val payload: ByteArray
    val topic: String
    val destinationLegalName: String
    val destinationLink: NetworkHostAndPort
    val applicationProperties: Map<String, Any?>
}