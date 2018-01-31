package net.corda.nodeapi.internal.protonwrapper.messages

import net.corda.core.utilities.NetworkHostAndPort

/**
 * An extension of ApplicationMessage that includes origin information.
 */
interface ReceivedMessage : ApplicationMessage {
    val sourceLegalName: String
    val sourceLink: NetworkHostAndPort

    fun complete(accepted: Boolean)
}