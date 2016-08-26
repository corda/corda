package com.r3corda.node.services.messaging

import rx.Observable

/**
 * RPC operations that the node exposes to clients using the Java client library. These can be called from
 * client apps and are implemented by the node in the [ServerRPCOps] class.
 */
interface CordaRPCOps : RPCOps {
    // TODO: Add useful RPCs for client apps (examining the vault, etc)
}