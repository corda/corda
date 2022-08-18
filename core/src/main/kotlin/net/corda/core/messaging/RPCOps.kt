package net.corda.core.messaging

import net.corda.core.DoNotImplement

/**
 * Base interface that all RPC servers must implement. Note: in Corda there's only one RPC interface. This base
 * interface is here in case we split the RPC system out into a separate library one day.
 */
@DoNotImplement
interface RPCOps {
    /** Returns the RPC protocol version. Exists since version 0 so guaranteed to be present.
     *
     * Getting this property is handled as a quick RPC, meaning that it is handled outside the node's standard
     * thread pool in order to provide a quick response even when the node is dealing with a high volume of RPC calls.
     */
    val protocolVersion: Int
}